package com.example.gamechat.data

import android.content.Context
import android.webkit.WebSettings

object EncounterUserAgentProvider {
    private const val FALLBACK_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    @Volatile
    private var cachedUserAgent: String? = null

    fun get(context: Context): String {
        val cached = cachedUserAgent
        if (!cached.isNullOrBlank()) return cached

        val resolved = runCatching { WebSettings.getDefaultUserAgent(context) }
            .getOrNull()
            .orEmpty()
            .trim()
            .ifEmpty { FALLBACK_USER_AGENT }

        cachedUserAgent = resolved
        return resolved
    }
}
