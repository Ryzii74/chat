package com.example.gamechat.data

import android.content.Context

object UserPreferences {
    private const val PREFS_NAME = "game_chat_prefs"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_CHAT_ROOM = "chat_room"
    private const val KEY_ENC_GAME_ID = "enc_game_id"
    private const val KEY_IS_ADMIN = "is_admin"
    private const val KEY_ENC_SITE = "enc_site"
    private const val KEY_ENC_LOGIN = "enc_login"
    private const val KEY_ENC_USER_ID = "enc_user_id"
    private const val KEY_ENC_GUID = "enc_guid"
    private const val KEY_ENC_STOKEN = "enc_stoken"
    private const val KEY_ENC_ATOKEN = "enc_atoken"
    private const val DEFAULT_CHAT_NICK = "Игрок"
    private const val DEFAULT_SERVER_URL = "http://10.0.2.2:8080"
    private const val DEFAULT_CHAT_ROOM = "general"
    private const val DEFAULT_ENC_GAME_ID = ""
    private const val ADMIN_PIN = "1234"

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
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_IS_ADMIN, false)
    }

    fun loginAsAdmin(context: Context, pin: String): Boolean {
        if (pin.trim() != ADMIN_PIN) return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_IS_ADMIN, true).apply()
        return true
    }

    fun logoutAdmin(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_IS_ADMIN, false).apply()
    }

    fun getAdminPinForApi(): String = ADMIN_PIN

    data class EncounterSession(
        val site: String,
        val login: String,
        val userId: String?,
        val guid: String?,
        val stoken: String?,
        val atoken: String?
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

    fun getChatNick(context: Context): String {
        return getEncounterSession(context).login.trim().ifEmpty { DEFAULT_CHAT_NICK }
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
}
