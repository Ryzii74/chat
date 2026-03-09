package com.example.gamechat.data

import org.json.JSONObject
import org.json.JSONArray
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object EncounterApiClient {
    private const val FALLBACK_USER_AGENT: String =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    data class UserInfo(
        val site: String,
        val login: String,
        val userId: String?,
        val guid: String?,
        val stoken: String?,
        val atoken: String?
    )

    data class CurrentLevelInfo(
        val gameId: Int,
        val gameTitle: String?,
        val eventCode: Int,
        val levelNumber: String?,
        val levelName: String?
    )

    data class EngineSector(
        val order: Int,
        val name: String,
        val isCompleted: Boolean,
        val answerCode: String?,
        val answerTimestamp: String?,
        val answerLogin: String?
    )

    data class EngineHint(
        val title: String,
        val text: String?,
        val remainingSeconds: Int?,
        val isPenalty: Boolean = false,
        val penalty: Int? = null,
        val penaltyComment: String? = null,
        val penaltyHelpState: Int? = null,
        val helpId: Int? = null,
        val number: Int? = null
    )

    data class EngineBonus(
        val title: String,
        val text: String?,
        val hint: String?,
        val isCompleted: Boolean,
        val completedBy: String?,
        val completedAt: String?,
        val completedCode: String?
    )

    data class EngineLevelState(
        val gameId: Int,
        val gameTitle: String?,
        val eventCode: Int,
        val levelId: Int?,
        val levelNumber: String?,
        val totalLevels: Int?,
        val levelName: String?,
        val requiredSectorsCount: Int?,
        val totalSectorsCount: Int,
        val sectorsLeftToClose: Int?,
        val taskTexts: List<String>,
        val messages: List<String>,
        val sectors: List<EngineSector>,
        val hints: List<EngineHint>,
        val bonuses: List<EngineBonus>,
        val mixedActions: List<EngineMixedAction>,
        val levelAutoTransitionTimeout: Long? = null
    )

    data class EngineMixedAction(
        val timeLabel: String,
        val login: String,
        val answer: String,
        val isCorrect: Boolean,
        val kind: Int?
    )

    data class SubmitCodeResult(
        val message: String,
        val state: EngineLevelState?
    )

    fun login(siteBaseUrl: String, login: String, password: String, userAgent: String? = null): Result<UserInfo> {
        return runCatching {
            val normalizedSite = normalizeSite(siteBaseUrl)
            val endpoint = "$normalizedSite/login/signin?json=1"

            val body = buildFormBody(
                mapOf(
                    "Login" to login,
                    "Password" to password,
                    "ddlNetwork" to "1"
                )
            )

            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 15_000
                doOutput = true
                instanceFollowRedirects = false
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", effectiveUserAgent(userAgent))
            }

            try {
                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(body)
                }

                val responseCode = connection.responseCode
                val responseBody = readResponse(connection, responseCode)

                val json = JSONObject(responseBody.ifBlank { "{}" })
                val errorCode = json.optInt("Error", -1)
                if (responseCode !in 200..299 || errorCode != 0) {
                    val errorText = json.optString("Message").ifBlank {
                        "Ошибка авторизации (код $errorCode)"
                    }
                    throw IllegalStateException(errorText)
                }

                val cookies = extractCookies(connection)
                val atoken = cookies["atoken"].orEmpty()
                val decodedAtoken = URLDecoder.decode(atoken, "UTF-8")
                val userId = decodedAtoken.split("&")
                    .firstOrNull { it.startsWith("uid=") }
                    ?.substringAfter("uid=")
                    ?.ifBlank { null }

                UserInfo(
                    site = normalizedSite,
                    login = login.trim(),
                    userId = userId,
                    guid = cookies["GUID"]?.takeIf { it.isNotBlank() },
                    stoken = cookies["stoken"]?.takeIf { it.isNotBlank() },
                    atoken = atoken.takeIf { it.isNotBlank() }
                )
            } finally {
                connection.disconnect()
            }
        }
    }

    fun loadCurrentLevel(
        siteBaseUrl: String,
        gameIdRaw: String,
        guid: String?,
        stoken: String?,
        atoken: String?,
        userAgent: String? = null
    ): Result<CurrentLevelInfo> {
        return runCatching {
            val gameId = gameIdRaw.trim().toIntOrNull()
                ?: throw IllegalArgumentException("Укажите корректный ID игры")
            require(gameId > 0) { "Укажите корректный ID игры" }

            val normalizedSite = normalizeSite(siteBaseUrl)
            val endpoint = "$normalizedSite/GameEngines/Encounter/Play/$gameId?json=1"

            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", effectiveUserAgent(userAgent))
                val cookie = buildAuthCookieHeader(guid, stoken, atoken)
                if (cookie.isNotBlank()) {
                    setRequestProperty("Cookie", cookie)
                }
            }

            try {
                val responseCode = connection.responseCode
                val responseBody = readResponse(connection, responseCode)
                if (responseCode !in 200..299) {
                    throw IllegalStateException("Ошибка API: HTTP $responseCode")
                }
                if (responseBody.trim().startsWith("<")) {
                    throw IllegalStateException("Сессия Encounter истекла. Войдите заново.")
                }

                val json = JSONObject(responseBody.ifBlank { "{}" })
                val event = json.optInt("Event", -1)
                val level = json.optJSONObject("Level")

                CurrentLevelInfo(
                    gameId = gameId,
                    gameTitle = json.optString("GameTitle").takeIf { it.isNotBlank() },
                    eventCode = event,
                    levelNumber = level?.opt("Number")?.toString()?.takeIf { it.isNotBlank() },
                    levelName = level?.optString("Name")?.takeIf { it.isNotBlank() }
                )
            } finally {
                connection.disconnect()
            }
        }
    }

    fun loadEngineState(
        siteBaseUrl: String,
        gameIdRaw: String,
        guid: String?,
        stoken: String?,
        atoken: String?,
        userAgent: String? = null
    ): Result<EngineLevelState> {
        return runCatching {
            val gameId = parseGameId(gameIdRaw)
            val normalizedSite = normalizeSite(siteBaseUrl)
            val endpoint = "$normalizedSite/GameEngines/Encounter/Play/$gameId?json=1"

            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", effectiveUserAgent(userAgent))
                val cookie = buildAuthCookieHeader(guid, stoken, atoken)
                if (cookie.isNotBlank()) {
                    setRequestProperty("Cookie", cookie)
                }
            }

            try {
                val responseCode = connection.responseCode
                val responseBody = readResponse(connection, responseCode)
                if (responseCode !in 200..299) {
                    throw IllegalStateException("Ошибка API: HTTP $responseCode")
                }
                parseEngineStateResponse(responseBody, gameId)
            } finally {
                connection.disconnect()
            }
        }
    }

    fun submitCode(
        siteBaseUrl: String,
        gameIdRaw: String,
        levelId: Int?,
        levelNumber: String?,
        code: String,
        guid: String?,
        stoken: String?,
        atoken: String?,
        userAgent: String? = null
    ): Result<SubmitCodeResult> {
        return runCatching {
            val gameId = parseGameId(gameIdRaw)
            val normalizedSite = normalizeSite(siteBaseUrl)
            val endpoint = "$normalizedSite/GameEngines/Encounter/Play/$gameId?json=1"
            val answer = code.trim()
            require(answer.isNotBlank()) { "Введите код" }

            val params = linkedMapOf<String, String>()
            params["LevelAction.Answer"] = answer
            if (levelId != null && levelId > 0) {
                params["LevelId"] = levelId.toString()
            }
            val levelNumberValue = levelNumber?.trim().orEmpty()
            if (levelNumberValue.isNotEmpty()) {
                params["LevelNumber"] = levelNumberValue
            }
            val body = buildFormBody(params)

            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 15_000
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", effectiveUserAgent(userAgent))
                val cookie = buildAuthCookieHeader(guid, stoken, atoken)
                if (cookie.isNotBlank()) {
                    setRequestProperty("Cookie", cookie)
                }
            }

            try {
                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(body)
                }

                val responseCode = connection.responseCode
                val responseBody = readResponse(connection, responseCode)
                if (responseCode !in 200..299) {
                    throw IllegalStateException("Ошибка отправки кода: HTTP $responseCode")
                }
                if (responseBody.trim().startsWith("<")) {
                    throw IllegalStateException("Сессия Encounter истекла. Войдите заново.")
                }

                val json = JSONObject(responseBody.ifBlank { "{}" })
                val message = firstNotBlank(
                    json.optJSONObject("EngineAction")
                        ?.optJSONObject("LevelAction")
                        ?.optString("Message"),
                    json.optJSONObject("EngineAction")
                        ?.optJSONObject("LevelAction")
                        ?.optString("Error"),
                    json.optString("Message")
                ) ?: "Код отправлен"

                val state = runCatching { parseEngineState(json, gameId) }.getOrNull()
                SubmitCodeResult(message = message, state = state)
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun normalizeSite(raw: String): String {
        val trimmed = raw.trim()
        require(trimmed.isNotEmpty()) { "Введите адрес сайта" }
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
        return withScheme.trimEnd('/')
    }

    private fun parseGameId(gameIdRaw: String): Int {
        val gameId = gameIdRaw.trim().toIntOrNull()
            ?: throw IllegalArgumentException("Укажите корректный ID игры")
        require(gameId > 0) { "Укажите корректный ID игры" }
        return gameId
    }

    private fun parseEngineStateResponse(responseBody: String, gameId: Int): EngineLevelState {
        if (responseBody.trim().startsWith("<")) {
            throw IllegalStateException("Сессия Encounter истекла. Войдите заново.")
        }
        return parseEngineState(JSONObject(responseBody.ifBlank { "{}" }), gameId)
    }

    private fun parseEngineState(json: JSONObject, gameId: Int): EngineLevelState {
        val level = json.optJSONObject("Level")

        val levelId = extractPositiveInt(
            level?.opt("LevelId"),
            level?.opt("Id"),
            json.opt("LevelId")
        )
        val levelNumber = firstNotBlank(
            level?.opt("Number")?.toString(),
            level?.optString("LevelNumber")
        )
        val levelName = level?.optString("Name")?.trim()?.takeIf { it.isNotEmpty() }
        val declaredTotalLevels = extractPositiveInt(
            json.opt("LevelsCount"),
            json.opt("LevelsNumber"),
            json.opt("GameLevelsCount"),
            json.opt("TotalLevels"),
            level?.opt("LevelsCount"),
            level?.opt("TotalLevels")
        )
        val maxLevelNumberFromJson = findMaxLevelNumberInJson(json)
        val totalLevels = when {
            declaredTotalLevels != null && maxLevelNumberFromJson != null ->
                maxOf(declaredTotalLevels, maxLevelNumberFromJson)
            declaredTotalLevels != null -> declaredTotalLevels
            else -> maxLevelNumberFromJson
        }
        val tasksArray = level?.optJSONArray("Tasks") ?: json.optJSONArray("Tasks")
        val taskTexts = buildList {
            if (tasksArray != null) {
                for (i in 0 until tasksArray.length()) {
                    val taskObj = tasksArray.optJSONObject(i) ?: continue
                    val taskTextFormatted = firstNotBlank(
                        taskObj.optString("TaskTextFormatted"),
                        taskObj.optString("TaskText")
                    )
                    if (!taskTextFormatted.isNullOrBlank()) {
                        add(taskTextFormatted)
                    }
                }
            }
            if (isEmpty()) {
                firstNotBlank(
                    level?.optString("Task"),
                    level?.optString("TaskText"),
                    level?.optString("Text"),
                    json.optString("Task")
                )?.let { add(it) }
            }
        }

        val sectorsArray = level?.optJSONArray("Sectors") ?: json.optJSONArray("Sectors")
        val totalSectorsCount = sectorsArray?.length() ?: 0
        val requiredSectorsCount = extractNonNegativeInt(
            level?.opt("RequiredSectorsCount"),
            level?.opt("NeedSectors"),
            level?.opt("SectorsNeedToClose"),
            json.opt("RequiredSectorsCount")
        )
        val sectorsLeftToClose = extractNonNegativeInt(
            level?.opt("SectorsLeftToClose"),
            level?.opt("LeftToClose"),
            json.opt("SectorsLeftToClose")
        )
        val sectors = buildList {
            if (sectorsArray == null) return@buildList
            for (i in 0 until sectorsArray.length()) {
                val sectorObj = sectorsArray.optJSONObject(i) ?: continue
                val answerObj = sectorObj.optJSONObject("Answer")
                val answerCode = firstNotBlank(
                    answerObj?.optString("Answer"),
                    sectorObj.optString("Answer")
                )
                val answerTimestamp = firstNotBlank(
                    answerObj?.optJSONObject("AnswerDateTime")?.opt("Timestamp")?.toString()
                )
                val answerLogin = firstNotBlank(
                    answerObj?.optString("Login")
                )
                val isCompleted =
                    sectorObj.optBoolean("IsAnswered", false) ||
                        sectorObj.optBoolean("IsPassed", false) ||
                        answerObj != null ||
                        !answerCode.isNullOrBlank()

                add(
                    EngineSector(
                        order = extractNonNegativeInt(
                            sectorObj.opt("Order"),
                            sectorObj.opt("SectorOrder"),
                            sectorObj.opt("SortOrder")
                        ) ?: (i + 1),
                        name = firstNotBlank(
                            sectorObj.optString("Name"),
                            sectorObj.optString("Title"),
                            sectorObj.optString("SectorName"),
                            "Сектор ${i + 1}"
                        ) ?: "Сектор ${i + 1}",
                        isCompleted = isCompleted,
                        answerCode = answerCode,
                        answerTimestamp = answerTimestamp,
                        answerLogin = answerLogin
                    )
                )
            }
        }

        val hintsArray = level?.optJSONArray("Helps")
            ?: level?.optJSONArray("Hints")
            ?: json.optJSONArray("Helps")
            ?: json.optJSONArray("Hints")
        val penaltyHintsArray = level?.optJSONArray("PenaltyHelps")
            ?: json.optJSONArray("PenaltyHelps")
            
        val hints = buildList {
            // Парсим обычные подсказки
            if (hintsArray != null) {
                for (i in 0 until hintsArray.length()) {
                    val hintObj = hintsArray.optJSONObject(i) ?: continue
                    val title = firstNotBlank(
                        hintObj.optString("Name"),
                        hintObj.optString("Title"),
                        "Подсказка ${i + 1}"
                    ) ?: "Подсказка ${i + 1}"
                    val text = firstNotBlank(
                        hintObj.optString("Text"),
                        hintObj.optString("HelpText"),
                        hintObj.optString("Value")
                    )
                    val remainingSeconds = extractNonNegativeInt(
                        hintObj.opt("RemainSeconds"),
                        hintObj.opt("SecondsToOpen"),
                        hintObj.opt("SecondsLeft"),
                        hintObj.opt("DelaySeconds")
                    )
                    add(
                        EngineHint(
                            title = title,
                            text = text,
                            remainingSeconds = remainingSeconds,
                            isPenalty = false,
                            helpId = extractPositiveInt(hintObj.opt("HelpId")),
                            number = extractPositiveInt(hintObj.opt("Number"))
                        )
                    )
                }
            }
            
            // Парсим штрафные подсказки
            if (penaltyHintsArray != null) {
                for (i in 0 until penaltyHintsArray.length()) {
                    val hintObj = penaltyHintsArray.optJSONObject(i) ?: continue
                    val title = firstNotBlank(
                        hintObj.optString("Name"),
                        hintObj.optString("Title"),
                        hintObj.optString("PenaltyComment"),
                        "Штрафная подсказка ${i + 1}"
                    ) ?: "Штрафная подсказка ${i + 1}"
                    val text = hintObj.optString("HelpText").takeIf { it.isNotBlank() }
                    val remainingSeconds = extractNonNegativeInt(
                        hintObj.opt("RemainSeconds")
                    )
                    val penaltyHelpState = extractNonNegativeInt(
                        hintObj.opt("PenaltyHelpState")
                    )
                    val penalty = extractNonNegativeInt(
                        hintObj.opt("Penalty")
                    )
                    val penaltyComment = hintObj.optString("PenaltyComment").takeIf { it.isNotBlank() }
                    
                    add(
                        EngineHint(
                            title = title,
                            text = text,
                            remainingSeconds = remainingSeconds,
                            isPenalty = true,
                            penalty = penalty,
                            penaltyComment = penaltyComment,
                            penaltyHelpState = penaltyHelpState,
                            helpId = extractPositiveInt(hintObj.opt("HelpId")),
                            number = extractPositiveInt(hintObj.opt("Number"))
                        )
                    )
                }
            }
        }

        val bonusesArray = level?.optJSONArray("Bonuses") ?: json.optJSONArray("Bonuses")
        val bonuses = buildList {
            if (bonusesArray == null) return@buildList
            for (i in 0 until bonusesArray.length()) {
                val bonusObj = bonusesArray.optJSONObject(i) ?: continue
                val title = firstNotBlank(
                    bonusObj.optString("Name"),
                    bonusObj.optString("Title"),
                    "Бонус ${i + 1}"
                ) ?: "Бонус ${i + 1}"
                val text = firstNotBlank(
                    bonusObj.optString("Task"),
                    bonusObj.optString("Text"),
                    bonusObj.optString("Description")
                )
                val hint = firstNotBlank(
                    bonusObj.optString("Help"),
                    bonusObj.optString("Hint"),
                    bonusObj.optString("BonusHelp"),
                    bonusObj.optString("AnswerHelp")
                )
                val answerObj = bonusObj.optJSONObject("Answer")
                val completedBy = firstNotBlank(answerObj?.optString("Login"))
                val completedCode = firstNotBlank(answerObj?.optString("Answer"))
                val completedAt = firstNotBlank(
                    answerObj
                        ?.optJSONObject("AnswerDateTime")
                        ?.opt("Timestamp")
                        ?.toString()
                )
                val isCompleted =
                    bonusObj.optBoolean("IsAnswered", false) ||
                        bonusObj.optBoolean("IsPassed", false) ||
                        bonusObj.optBoolean("IsDone", false) ||
                        answerObj != null ||
                        !completedBy.isNullOrBlank() ||
                        !completedAt.isNullOrBlank() ||
                        !completedCode.isNullOrBlank()

                add(
                    EngineBonus(
                        title = title,
                        text = text,
                        hint = hint,
                        isCompleted = isCompleted,
                        completedBy = completedBy,
                        completedAt = completedAt,
                        completedCode = completedCode
                    )
                )
            }
        }

        val mixedActions = parseMixedActions(level)
        
        // Парсим сообщения уровня
        val messagesArray = level?.optJSONArray("Messages")
        val messages = buildList {
            if (messagesArray != null) {
                for (i in 0 until messagesArray.length()) {
                    val messageText = messagesArray.optString(i)
                    if (messageText.isNotBlank()) {
                        add(messageText)
                    }
                }
            }
        }
        
        // Парсим время автоперехода из поля Timeout в Level
        val levelAutoTransitionTimeout = level?.optLong("Timeout")
            ?.takeIf { it > 0 }  // Если 0 или отрицательное - считаем что автоперехода нет

        return EngineLevelState(
            gameId = gameId,
            gameTitle = json.optString("GameTitle").takeIf { it.isNotBlank() },
            eventCode = json.optInt("Event", -1),
            levelId = levelId,
            levelNumber = levelNumber,
            totalLevels = totalLevels,
            levelName = levelName,
            requiredSectorsCount = requiredSectorsCount,
            totalSectorsCount = totalSectorsCount,
            sectorsLeftToClose = sectorsLeftToClose,
            taskTexts = taskTexts,
            messages = messages,
            sectors = sectors,
            hints = hints,
            bonuses = bonuses,
            mixedActions = mixedActions,
            levelAutoTransitionTimeout = levelAutoTransitionTimeout
        )
    }

    private fun extractPositiveInt(vararg values: Any?): Int? {
        values.forEach { value ->
            val intValue = value?.toString()?.toIntOrNull()
            if (intValue != null && intValue > 0) {
                return intValue
            }
        }
        return null
    }

    private fun extractNonNegativeInt(vararg values: Any?): Int? {
        values.forEach { value ->
            val intValue = value?.toString()?.toIntOrNull()
            if (intValue != null && intValue >= 0) {
                return intValue
            }
        }
        return null
    }

    private fun firstNotBlank(vararg values: String?): String? {
        values.forEach { value ->
            val normalized = value?.trim()
            if (!normalized.isNullOrEmpty() && !normalized.equals("null", ignoreCase = true)) {
                return normalized
            }
        }
        return null
    }

    private fun parseMixedActions(level: JSONObject?): List<EngineMixedAction> {
        val mixed = level?.optJSONArray("MixedActions") ?: return emptyList()
        return buildList {
            for (i in 0 until mixed.length()) {
                val item = mixed.opt(i)
                when (item) {
                    is JSONObject -> {
                        val timestampRaw = item
                            .optJSONObject("EnterDateTime")
                            ?.opt("Timestamp")
                            ?.toString()
                        val time = formatTimeFromTimestamp(timestampRaw).ifBlank { "??:??:??" }
                        val login = firstNotBlank(item.optString("Login")).orEmpty().ifBlank { "?" }
                        val answer = firstNotBlank(item.optString("Answer")).orEmpty().ifBlank { "?" }
                        val isCorrect = item.optBoolean("IsCorrect", false)
                        val kind = extractNonNegativeInt(item.opt("Kind"))
                        add(
                            EngineMixedAction(
                                timeLabel = time,
                                login = login,
                                answer = answer,
                                isCorrect = isCorrect,
                                kind = kind
                            )
                        )
                    }
                }
            }
        }
    }

    private fun formatTimeFromTimestamp(raw: String?): String {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return ""
        val numeric = value.toLongOrNull() ?: return ""
        val millis = when {
            numeric > 999_999_999_999L -> numeric
            numeric > 0L -> numeric * 1000L
            else -> return ""
        }
        return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(millis))
    }

    private fun findMaxLevelNumberInJson(root: JSONObject): Int? {
        var maxValue: Int? = null

        fun visitAny(node: Any?) {
            when (node) {
                is JSONObject -> {
                    val names = node.keys()
                    while (names.hasNext()) {
                        val key = names.next()
                        val value = node.opt(key)
                        if (key.equals("LevelNumber", ignoreCase = true)) {
                            val parsed = value?.toString()?.trim()?.toIntOrNull()
                            if (parsed != null && parsed > 0) {
                                maxValue = if (maxValue == null) parsed else maxOf(maxValue!!, parsed)
                            }
                        }
                        visitAny(value)
                    }
                }

                is JSONArray -> {
                    for (i in 0 until node.length()) {
                        visitAny(node.opt(i))
                    }
                }
            }
        }

        visitAny(root)
        return maxValue
    }

    private fun buildFormBody(params: Map<String, String>): String {
        return params.entries.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
        }
    }

    private fun effectiveUserAgent(userAgent: String?): String {
        return userAgent?.trim().orEmpty().ifBlank { FALLBACK_USER_AGENT }
    }

    private fun buildAuthCookieHeader(guid: String?, stoken: String?, atoken: String?): String {
        val parts = mutableListOf<String>()
        if (!guid.isNullOrBlank()) parts.add("GUID=$guid")
        if (!stoken.isNullOrBlank()) parts.add("stoken=$stoken")
        if (!atoken.isNullOrBlank()) parts.add("atoken=$atoken")
        return parts.joinToString("; ")
    }

    private fun extractCookies(connection: HttpURLConnection): Map<String, String> {
        val headers = connection.headerFields["Set-Cookie"] ?: emptyList()
        val map = mutableMapOf<String, String>()
        headers.forEach { line ->
            val pair = line.substringBefore(";")
            val name = pair.substringBefore("=", "").trim()
            val value = pair.substringAfter("=", "").trim()
            if (name.isNotBlank()) {
                map[name] = value
            }
        }
        return map
    }

    private fun readResponse(connection: HttpURLConnection, statusCode: Int): String {
        val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
        return stream?.bufferedReader()?.use { it.readText() }.orEmpty()
    }
}
