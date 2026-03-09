package com.example.gamechat.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

object ChatServerClient {
    private const val MESSAGES_PATH = "messages"
    private const val ADMIN_LOGIN_PATH = "admin/login"
    private const val ADMIN_LOGOUT_PATH = "admin/logout"
    private const val SWITCH_ROOM_PATH = "admin/switch-room"
    private const val CLEAR_ROOM_PATH = "admin/clear-room"
    private const val ALLOWED_NICKS_PATH = "admin/allowed-nicks"
    private const val ROOMS_WITH_HISTORY_PATH = "admin/rooms-with-history"
    private const val APP_ACCESS_CHECK_PATH = "app-access/check"
    private const val ENGINE_LEVEL_CHANGED_PATH = "engine/level-changed"
    private const val MEDIA_PATH = "media"

    data class HistoryMessage(
        val id: String?,
        val senderName: String?,
        val text: String,
        val isOutgoing: Boolean,
        val timestamp: String?,
        val imageUrl: String?
    )

    data class HistoryResult(
        val messages: List<HistoryMessage>,
        val activeRoom: String?
    )

    fun loginAsAdmin(serverBaseUrl: String, pin: String): Result<String> {
        return runCatching {
            val normalizedPin = pin.trim()
            require(normalizedPin.isNotEmpty()) { "Введите PIN администратора" }

            val loginUrl = buildAdminLoginUrl(serverBaseUrl)
            val payload = JSONObject().apply {
                put("pin", normalizedPin)
            }.toString()

            val connection = (URL(loginUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 10_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }
            try {
                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(payload)
                }
                val statusCode = connection.responseCode
                val responseBody = readResponse(connection)
                if (statusCode !in 200..299) {
                    throw IllegalStateException("HTTP $statusCode: $responseBody")
                }
                val token = JSONObject(responseBody.ifBlank { "{}" }).optString("adminToken").trim()
                if (token.isBlank()) {
                    throw IllegalStateException("Admin token not found in response")
                }
                token
            } finally {
                connection.disconnect()
            }
        }
    }

    fun logoutAdmin(serverBaseUrl: String, adminToken: String): Result<String> {
        return runCatching {
            val token = adminToken.trim()
            require(token.isNotEmpty()) { "Admin token is empty" }

            val logoutUrl = buildAdminLogoutUrl(serverBaseUrl)
            val connection = (URL(logoutUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("X-Admin-Token", token)
            }
            try {
                val statusCode = connection.responseCode
                val responseBody = readResponse(connection)
                if (statusCode in 200..299) {
                    responseBody.ifBlank { "ok" }
                } else {
                    throw IllegalStateException("HTTP $statusCode: $responseBody")
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    fun loadHistory(
        serverBaseUrl: String,
        room: String,
        currentUser: String,
        limit: Int? = null
    ): Result<HistoryResult> {
        return runCatching {
            val historyUrl = buildHistoryUrl(serverBaseUrl, room, limit)
            val connection = (URL(historyUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/json")
            }

            try {
                val statusCode = connection.responseCode
                val responseBody = readResponse(connection)

                if (statusCode !in 200..299) {
                    throw IllegalStateException("HTTP $statusCode: $responseBody")
                }

                parseHistoryResponse(responseBody, currentUser)
            } finally {
                connection.disconnect()
            }
        }
    }

    fun sendMessage(
        serverBaseUrl: String,
        room: String,
        userName: String,
        message: String,
        imageUrl: String? = null
    ): Result<String> {
        return runCatching {
            val postUrl = buildSendUrl(serverBaseUrl, room)
            val payload = JSONObject().apply {
                put("user", userName)
                put("message", message)
                put("room", room)
                if (!imageUrl.isNullOrBlank()) {
                    put("imageUrl", imageUrl)
                }
            }.toString()

            val connection = (URL(postUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 10_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }

            try {
                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(payload)
                }

                val statusCode = connection.responseCode
                val responseBody = readResponse(connection)

                if (statusCode in 200..299) {
                    responseBody.ifBlank { "Message sent" }
                } else {
                    throw IllegalStateException("HTTP $statusCode: $responseBody")
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    fun deleteMessage(serverBaseUrl: String, messageId: String): Result<String> {
        return runCatching {
            val url = buildDeleteMessageUrl(serverBaseUrl, messageId)
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "DELETE"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/json")
            }

            try {
                val statusCode = connection.responseCode
                val responseBody = readResponse(connection)
                if (statusCode in 200..299) {
                    responseBody.ifBlank { "Message deleted" }
                } else {
                    throw IllegalStateException("HTTP $statusCode: $responseBody")
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    fun switchActiveRoom(serverBaseUrl: String, room: String, adminToken: String): Result<String> {
        return runCatching {
            val switchUrl = buildSwitchRoomUrl(serverBaseUrl)
            val payload = JSONObject().apply {
                put("room", room)
            }.toString()

            val connection = (URL(switchUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 10_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("X-Admin-Token", adminToken)
            }

            try {
                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(payload)
                }

                val statusCode = connection.responseCode
                val responseBody = readResponse(connection)
                if (statusCode in 200..299) {
                    responseBody.ifBlank { "Room switched" }
                } else {
                    throw IllegalStateException("HTTP $statusCode: $responseBody")
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    fun clearRoomHistory(serverBaseUrl: String, room: String, adminToken: String): Result<String> {
        return runCatching {
            val clearUrl = buildClearRoomUrl(serverBaseUrl)
            val payload = JSONObject().apply {
                put("room", room)
            }.toString()

            val connection = (URL(clearUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 10_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("X-Admin-Token", adminToken)
            }

            try {
                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(payload)
                }

                val statusCode = connection.responseCode
                val responseBody = readResponse(connection)
                if (statusCode in 200..299) {
                    responseBody.ifBlank { "Room history cleared" }
                } else {
                    throw IllegalStateException("HTTP $statusCode: $responseBody")
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    fun uploadImage(serverBaseUrl: String, imageBytes: ByteArray): Result<String> {
        return runCatching {
            val uploadUrl = buildMediaUploadUrl(serverBaseUrl)
            val connection = (URL(uploadUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 15_000
                doOutput = true
                setRequestProperty("Content-Type", "image/jpeg")
                setRequestProperty("Accept", "application/json")
                setFixedLengthStreamingMode(imageBytes.size)
            }

            try {
                connection.outputStream.use { output ->
                    output.write(imageBytes)
                }
                val statusCode = connection.responseCode
                val responseBody = readResponse(connection)
                if (statusCode !in 200..299) {
                    throw IllegalStateException("HTTP $statusCode: $responseBody")
                }
                val body = JSONObject(responseBody.ifBlank { "{}" })
                val rawUrl = body.optString("url")
                if (rawUrl.isBlank()) {
                    throw IllegalStateException("Upload response does not contain media url")
                }
                resolveServerMediaUrl(serverBaseUrl, rawUrl)
            } finally {
                connection.disconnect()
            }
        }
    }

    fun getAllowedNicks(serverBaseUrl: String, adminToken: String): Result<List<String>> {
        return runCatching {
            val url = buildAllowedNicksUrl(serverBaseUrl)
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("X-Admin-Token", adminToken)
            }
            try {
                val statusCode = connection.responseCode
                val responseBody = readResponse(connection)
                if (statusCode !in 200..299) {
                    throw IllegalStateException("HTTP $statusCode: $responseBody")
                }
                val array = JSONObject(responseBody.ifBlank { "{}" }).optJSONArray("nicks")
                if (array == null) return@runCatching emptyList()
                buildList {
                    for (i in 0 until array.length()) {
                        val nick = array.optString(i).trim()
                        if (nick.isNotBlank()) add(nick)
                    }
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    fun setAllowedNicks(serverBaseUrl: String, nicks: List<String>, adminToken: String): Result<String> {
        return runCatching {
            val url = buildAllowedNicksUrl(serverBaseUrl)
            val payload = JSONObject().apply {
                put("nicks", JSONArray(nicks))
            }.toString()

            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 10_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("X-Admin-Token", adminToken)
            }
            try {
                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(payload)
                }
                val statusCode = connection.responseCode
                val responseBody = readResponse(connection)
                if (statusCode in 200..299) {
                    responseBody.ifBlank { "Allowed nicks saved" }
                } else {
                    throw IllegalStateException("HTTP $statusCode: $responseBody")
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    fun getRoomsWithHistory(serverBaseUrl: String, adminToken: String): Result<List<String>> {
        return runCatching {
            val url = buildRoomsWithHistoryUrl(serverBaseUrl)
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("X-Admin-Token", adminToken)
            }
            try {
                val statusCode = connection.responseCode
                val responseBody = readResponse(connection)
                if (statusCode !in 200..299) {
                    throw IllegalStateException("HTTP $statusCode: $responseBody")
                }
                val array = JSONObject(responseBody.ifBlank { "{}" }).optJSONArray("rooms")
                if (array == null) return@runCatching emptyList()
                buildList {
                    for (i in 0 until array.length()) {
                        val room = array.optString(i).trim()
                        if (room.isNotBlank()) add(room)
                    }
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    fun checkAppAccess(serverBaseUrl: String, nick: String): Result<Boolean> {
        return runCatching {
            val base = normalizeBaseUrl(serverBaseUrl)
            val encoded = URLEncoder.encode(nick.trim(), "UTF-8")
            val url = "$base/$APP_ACCESS_CHECK_PATH?nick=$encoded"
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/json")
            }
            try {
                val statusCode = connection.responseCode
                val responseBody = readResponse(connection)
                if (statusCode !in 200..299) {
                    throw IllegalStateException("HTTP $statusCode: $responseBody")
                }
                JSONObject(responseBody.ifBlank { "{}" }).optBoolean("allowed", false)
            } finally {
                connection.disconnect()
            }
        }
    }

    fun notifyEngineLevelChanged(serverBaseUrl: String, levelNumber: String): Result<String> {
        return runCatching {
            val normalizedLevel = levelNumber.trim()
            require(normalizedLevel.isNotEmpty()) { "Level number is empty" }

            val url = buildEngineLevelChangedUrl(serverBaseUrl)
            val payload = JSONObject().apply {
                put("levelNumber", normalizedLevel)
            }.toString()

            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 10_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }
            try {
                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(payload)
                }
                val statusCode = connection.responseCode
                val responseBody = readResponse(connection)
                if (statusCode in 200..299) {
                    responseBody.ifBlank { "ok" }
                } else {
                    throw IllegalStateException("HTTP $statusCode: $responseBody")
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    // Получение настроек сервера включая gameId
    fun getServerConfig(serverBaseUrl: String): Result<ServerConfig> {
        return runCatching {
            val connection = URL("$serverBaseUrl/api/config").openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/json")
            }

            val statusCode = connection.responseCode
            val responseBody = readResponse(connection)
            connection.disconnect()

            if (statusCode !in 200..299) {
                // Если API не поддерживается, возвращаем пустую конфигурацию
                ServerConfig(gameId = "", serverName = "")
            } else {
                parseServerConfig(responseBody)
            }
        }
    }

    data class ServerConfig(
        val gameId: String?,
        val serverName: String?
    )

    private fun parseServerConfig(response: String): ServerConfig {
        return try {
            val json = JSONObject(response)
            ServerConfig(
                gameId = json.optString("gameId", ""),
                serverName = json.optString("serverName", "")
            )
        } catch (e: Exception) {
            ServerConfig(gameId = "", serverName = "")
        }
    }

    private fun buildHistoryUrl(serverBaseUrl: String, room: String, limit: Int?): String =
        buildMessagesUrl(serverBaseUrl, room, limit)

    private fun buildSendUrl(serverBaseUrl: String, room: String): String =
        buildMessagesUrl(serverBaseUrl, room, null)

    private fun buildAdminLoginUrl(serverBaseUrl: String): String {
        val base = normalizeBaseUrl(serverBaseUrl)
        return "$base/$ADMIN_LOGIN_PATH"
    }

    private fun buildAdminLogoutUrl(serverBaseUrl: String): String {
        val base = normalizeBaseUrl(serverBaseUrl)
        return "$base/$ADMIN_LOGOUT_PATH"
    }

    private fun buildSwitchRoomUrl(serverBaseUrl: String): String {
        val trimmed = serverBaseUrl.trim()
        require(trimmed.isNotEmpty()) { "Server URL is empty" }

        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }

        val noTrailingSlash = withScheme.trimEnd('/')
        return "$noTrailingSlash/$SWITCH_ROOM_PATH"
    }

    private fun buildClearRoomUrl(serverBaseUrl: String): String {
        val trimmed = serverBaseUrl.trim()
        require(trimmed.isNotEmpty()) { "Server URL is empty" }

        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }

        val noTrailingSlash = withScheme.trimEnd('/')
        return "$noTrailingSlash/$CLEAR_ROOM_PATH"
    }

    private fun buildMediaUploadUrl(serverBaseUrl: String): String {
        val base = normalizeBaseUrl(serverBaseUrl)
        return "$base/$MEDIA_PATH"
    }

    private fun buildDeleteMessageUrl(serverBaseUrl: String, messageId: String): String {
        val base = normalizeBaseUrl(serverBaseUrl)
        val encoded = URLEncoder.encode(messageId, "UTF-8")
        return "$base/$MESSAGES_PATH/$encoded"
    }

    private fun buildAllowedNicksUrl(serverBaseUrl: String): String {
        val base = normalizeBaseUrl(serverBaseUrl)
        return "$base/$ALLOWED_NICKS_PATH"
    }

    private fun buildRoomsWithHistoryUrl(serverBaseUrl: String): String {
        val base = normalizeBaseUrl(serverBaseUrl)
        return "$base/$ROOMS_WITH_HISTORY_PATH"
    }

    private fun buildEngineLevelChangedUrl(serverBaseUrl: String): String {
        val base = normalizeBaseUrl(serverBaseUrl)
        return "$base/$ENGINE_LEVEL_CHANGED_PATH"
    }

    fun resolveServerMediaUrl(serverBaseUrl: String, rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed
        }
        val base = normalizeBaseUrl(serverBaseUrl)
        return if (trimmed.startsWith("/")) "$base$trimmed" else "$base/$trimmed"
    }

    private fun normalizeBaseUrl(serverBaseUrl: String): String {
        val trimmed = serverBaseUrl.trim()
        require(trimmed.isNotEmpty()) { "Server URL is empty" }

        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }
        return withScheme.trimEnd('/')
    }

    private fun buildMessagesUrl(serverBaseUrl: String, room: String, limit: Int?): String {
        val trimmed = serverBaseUrl.trim()
        require(trimmed.isNotEmpty()) { "Server URL is empty" }

        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }

        val noTrailingSlash = withScheme.trimEnd('/')
        val base = if (noTrailingSlash.endsWith("/$MESSAGES_PATH")) {
            noTrailingSlash
        } else {
            "$noTrailingSlash/$MESSAGES_PATH"
        }
        val encodedRoom = URLEncoder.encode(room.trim().ifEmpty { "general" }, "UTF-8")
        val limitPart = limit?.takeIf { it > 0 }?.let { "&limit=$it" }.orEmpty()
        return "$base?room=$encodedRoom$limitPart"
    }

    private fun parseHistoryResponse(body: String, currentUser: String): HistoryResult {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return HistoryResult(emptyList(), null)

        return when {
            trimmed.startsWith("[") -> HistoryResult(
                messages = parseJsonArray(JSONArray(trimmed), currentUser),
                activeRoom = null
            )

            trimmed.startsWith("{") -> {
                val obj = JSONObject(trimmed)
                val array = obj.optJSONArray("messages")
                val activeRoom = obj.optString("activeRoom").takeIf { it.isNotBlank() }
                    ?: obj.optString("room").takeIf { it.isNotBlank() }
                if (array != null) {
                    HistoryResult(
                        messages = parseJsonArray(array, currentUser),
                        activeRoom = activeRoom
                    )
                } else {
                    val text = obj.optString("message", obj.optString("text", ""))
                    val imageUrl = obj.optString("imageUrl").takeIf { it.isNotBlank() }
                    val messages = if (text.isBlank() && imageUrl == null) {
                        emptyList()
                    } else {
                        val senderName = obj.optString("user", obj.optString("sender", "")).trim()
                        listOf(
                            HistoryMessage(
                                id = obj.optString("id").takeIf { it.isNotBlank() },
                                senderName = senderName.takeIf { it.isNotBlank() },
                                text = text,
                                isOutgoing = senderName.equals(currentUser, ignoreCase = true),
                                timestamp = obj.optString("timestamp").takeIf { it.isNotBlank() },
                                imageUrl = imageUrl
                            )
                        )
                    }
                    HistoryResult(messages, activeRoom)
                }
            }

            else -> trimmed.lines()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .map {
                    HistoryMessage(
                        id = null,
                        senderName = null,
                        text = it,
                        isOutgoing = false,
                        timestamp = null,
                        imageUrl = null
                    )
                }
                .let { HistoryResult(it, null) }
        }
    }

    private fun parseJsonArray(array: JSONArray, currentUser: String): List<HistoryMessage> {
        val messages = mutableListOf<HistoryMessage>()
        for (i in 0 until array.length()) {
            val item = array.opt(i)
            when (item) {
                is JSONObject -> {
                    val text = item.optString("message", item.optString("text", "")).trim()
                    val imageUrl = item.optString("imageUrl").takeIf { it.isNotBlank() }
                    if (text.isBlank() && imageUrl == null) continue

                    val user = item.optString("user", item.optString("sender", ""))
                    val isOutgoing = if (item.has("isOutgoing")) {
                        item.optBoolean("isOutgoing")
                    } else {
                        user.equals(currentUser, ignoreCase = true)
                    }
                    messages.add(
                        HistoryMessage(
                            id = item.optString("id").takeIf { it.isNotBlank() },
                            senderName = user.trim().takeIf { it.isNotBlank() },
                            text = text,
                            isOutgoing = isOutgoing,
                            timestamp = item.optString("timestamp").takeIf { it.isNotBlank() },
                            imageUrl = imageUrl
                        )
                    )
                }

                is String -> {
                    val text = item.trim()
                    if (text.isNotBlank()) {
                        messages.add(
                            HistoryMessage(
                                id = null,
                                senderName = null,
                                text = text,
                                isOutgoing = false,
                                timestamp = null,
                                imageUrl = null
                            )
                        )
                    }
                }
            }
        }
        return messages
    }

    private fun readResponse(connection: HttpURLConnection): String {
        val stream = if (connection.responseCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        } ?: return ""

        return stream.bufferedReader().use(BufferedReader::readText)
    }
}
