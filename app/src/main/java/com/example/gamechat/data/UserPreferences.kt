package com.example.gamechat.data

import android.content.Context

object UserPreferences {
    private const val PREFS_NAME = "game_chat_prefs"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_CHAT_ROOM = "chat_room"
    private const val KEY_ENC_GAME_ID = "enc_game_id"
    private const val KEY_ADMIN_TOKEN = "admin_token"
    private const val KEY_ENC_SITE = "enc_site"
    private const val KEY_ENC_LOGIN = "enc_login"
    private const val KEY_ENC_USER_ID = "enc_user_id"
    private const val KEY_ENC_GUID = "enc_guid"
    private const val KEY_ENC_STOKEN = "enc_stoken"
    private const val KEY_ENC_ATOKEN = "enc_atoken"
    private const val KEY_ENC_LAST_SITE = "enc_last_site"
    private const val KEY_ENC_LAST_LOGIN = "enc_last_login"
    private const val KEY_ENC_LAST_PASSWORD = "enc_last_password"
    private const val KEY_SOLVER_MODE_ALIAS = "solver_mode_alias"
    private const val KEY_SOLVER_AUTO_ENABLED = "solver_auto_enabled"
    private const val KEY_SOLVER_HISTORY = "solver_history"
    private const val KEY_CHAT_NOTIFICATIONS_ENABLED = "chat_notifications_enabled"
    private const val DEFAULT_CHAT_NICK = "Игрок"
    private const val DEFAULT_SERVER_URL = "http://46.101.168.241:8080"
    private const val DEFAULT_CHAT_ROOM = "general"
    private const val DEFAULT_ENC_GAME_ID = ""
    private const val DEFAULT_SOLVER_MODE_ALIAS = "1"
    private const val DEFAULT_SOLVER_AUTO_ENABLED = false

    fun getServerUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL).orEmpty()
        return normalizeServerBaseUrl(stored)
    }

    fun setServerUrl(context: Context, serverUrl: String) {
        val normalizedUrl = normalizeServerBaseUrl(serverUrl)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SERVER_URL, normalizedUrl).apply()
    }

    fun getChatRoom(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_CHAT_ROOM, DEFAULT_CHAT_ROOM).orEmpty()
        return normalizeChatRoom(stored)
    }

    fun setChatRoom(context: Context, room: String) {
        val normalizedRoom = normalizeChatRoom(room)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CHAT_ROOM, normalizedRoom).apply()
    }

    fun getEncounterGameId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ENC_GAME_ID, DEFAULT_ENC_GAME_ID).orEmpty().trim()
    }

    fun setEncounterGameId(context: Context, gameId: String) {
        val normalized = gameId.trim()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_ENC_GAME_ID, normalized).apply()
    }

    fun isAdmin(context: Context): Boolean {
        return getAdminToken(context).isNotBlank()
    }

    fun saveAdminToken(context: Context, token: String) {
        val normalizedToken = token.trim()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_ADMIN_TOKEN, normalizedToken).apply()
    }

    fun logoutAdmin(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_ADMIN_TOKEN).apply()
    }

    fun getAdminToken(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ADMIN_TOKEN, "").orEmpty().trim()
    }

    data class EncounterSession(
        val site: String,
        val login: String,
        val userId: String?,
        val guid: String?,
        val stoken: String?,
        val atoken: String?
    )

    data class EncounterCredentials(
        val site: String,
        val login: String,
        val password: String
    )

    fun saveEncounterSession(
        context: Context,
        site: String,
        login: String,
        userId: String?,
        guid: String?,
        stoken: String?,
        atoken: String?
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_ENC_SITE, site.trim())
            .putString(KEY_ENC_LOGIN, login.trim())
            .putString(KEY_ENC_USER_ID, userId)
            .putString(KEY_ENC_GUID, guid)
            .putString(KEY_ENC_STOKEN, stoken)
            .putString(KEY_ENC_ATOKEN, atoken)
            .apply()
    }

    fun getEncounterSession(context: Context): EncounterSession {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return EncounterSession(
            site = prefs.getString(KEY_ENC_SITE, "").orEmpty(),
            login = prefs.getString(KEY_ENC_LOGIN, "").orEmpty(),
            userId = prefs.getString(KEY_ENC_USER_ID, null),
            guid = prefs.getString(KEY_ENC_GUID, null),
            stoken = prefs.getString(KEY_ENC_STOKEN, null),
            atoken = prefs.getString(KEY_ENC_ATOKEN, null)
        )
    }

    fun saveEncounterCredentials(
        context: Context,
        site: String,
        login: String,
        password: String
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_ENC_LAST_SITE, site.trim())
            .putString(KEY_ENC_LAST_LOGIN, login.trim())
            .putString(KEY_ENC_LAST_PASSWORD, password)
            .apply()
    }

    fun getEncounterCredentials(context: Context): EncounterCredentials {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val session = getEncounterSession(context)
        val siteFromSession = session.site.trim()
        val loginFromSession = session.login.trim()
        val site = prefs.getString(KEY_ENC_LAST_SITE, null)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: siteFromSession
        val login = prefs.getString(KEY_ENC_LAST_LOGIN, null)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: loginFromSession
        val password = prefs.getString(KEY_ENC_LAST_PASSWORD, "").orEmpty()
        return EncounterCredentials(
            site = site,
            login = login,
            password = password
        )
    }

    fun getChatNick(context: Context): String {
        return getEncounterSession(context).login.trim().ifEmpty { DEFAULT_CHAT_NICK }
    }

    fun getSolverModeAlias(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SOLVER_MODE_ALIAS, DEFAULT_SOLVER_MODE_ALIAS)
            .orEmpty()
            .trim()
            .ifEmpty { DEFAULT_SOLVER_MODE_ALIAS }
    }

    fun setSolverModeAlias(context: Context, alias: String) {
        val normalized = alias.trim().ifEmpty { DEFAULT_SOLVER_MODE_ALIAS }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SOLVER_MODE_ALIAS, normalized).apply()
    }

    fun isSolverAutoEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SOLVER_AUTO_ENABLED, DEFAULT_SOLVER_AUTO_ENABLED)
    }

    fun setSolverAutoEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SOLVER_AUTO_ENABLED, enabled).apply()
    }

    fun saveSolverHistory(context: Context, messages: List<com.example.gamechat.ui.chat.ChatMessage>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val limitedMessages = messages.takeLast(50) // Сохраняем только последние 50 сообщений
        
        val jsonArray = org.json.JSONArray()
        for (message in limitedMessages) {
            val jsonObject = org.json.JSONObject()
            jsonObject.put("id", message.id ?: "")
            jsonObject.put("senderName", message.senderName ?: "")
            jsonObject.put("text", message.text)
            jsonObject.put("isOutgoing", message.isOutgoing)
            jsonObject.put("deliveryState", message.deliveryState.name)
            jsonObject.put("timeLabel", message.timeLabel)
            jsonObject.put("imageUrl", message.imageUrl ?: "")
            jsonArray.put(jsonObject)
        }
        
        prefs.edit().putString(KEY_SOLVER_HISTORY, jsonArray.toString()).apply()
    }

    fun getSolverHistory(context: Context): List<com.example.gamechat.ui.chat.ChatMessage> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_SOLVER_HISTORY, "") ?: ""
        
        if (jsonString.isBlank()) {
            return emptyList()
        }
        
        return try {
            val jsonArray = org.json.JSONArray(jsonString)
            val messages = mutableListOf<com.example.gamechat.ui.chat.ChatMessage>()
            
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val message = com.example.gamechat.ui.chat.ChatMessage(
                    id = jsonObject.getString("id").takeIf { it.isNotEmpty() },
                    senderName = jsonObject.getString("senderName").takeIf { it.isNotEmpty() },
                    text = jsonObject.getString("text"),
                    isOutgoing = jsonObject.getBoolean("isOutgoing"),
                    deliveryState = com.example.gamechat.ui.chat.DeliveryState.valueOf(jsonObject.getString("deliveryState")),
                    timeLabel = jsonObject.getString("timeLabel"),
                    imageUrl = jsonObject.getString("imageUrl").takeIf { it.isNotEmpty() }
                )
                messages.add(message)
            }
            
            messages
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun normalizeServerBaseUrl(url: String): String {
        val raw = url.trim().ifEmpty { DEFAULT_SERVER_URL }
        val withoutMessagesPath = if (raw.endsWith("/messages")) {
            raw.removeSuffix("/messages")
        } else {
            raw
        }
        return withoutMessagesPath.trimEnd('/').ifEmpty { DEFAULT_SERVER_URL }
    }

    private fun normalizeChatRoom(room: String): String {
        return room.trim()
            .replace("\\s+".toRegex(), "-")
            .ifEmpty { DEFAULT_CHAT_ROOM }
    }
    
    fun isChatNotificationsEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_CHAT_NOTIFICATIONS_ENABLED, true)
    }

    fun setChatNotificationsEnabled(context: Context, enabled: Boolean) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPrefs.edit()
            .putBoolean(KEY_CHAT_NOTIFICATIONS_ENABLED, enabled)
            .apply()
    }
    
    fun syncGameIdFromServer(context: Context) {
        try {
            val serverUrl = getServerUrl(context)
            if (serverUrl.isBlank()) return
            
            val result = ChatServerClient.getServerConfig(serverUrl)
            result.onSuccess { config ->
                val gameId = config.gameId?.trim().orEmpty()
                if (gameId.isNotEmpty()) {
                    setEncounterGameId(context, gameId)
                }
            }
        } catch (e: Exception) {
            // Тихо игнорируем ошибки синхронизации
        }
    }
}
