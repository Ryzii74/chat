package com.example.gamechat.data

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ChatSocketClient {
    interface Listener {
        fun onEvent(type: String, activeRoom: String?, levelNumber: String?)
        fun onError(errorMessage: String)
    }

    private var client: OkHttpClient? = null
    private var socket: WebSocket? = null

    fun connect(serverBaseUrl: String, listener: Listener) {
        disconnect()

        val wsUrl = buildWsUrl(serverBaseUrl)
        client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        socket = client?.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val payload = runCatching { JSONObject(text) }.getOrNull() ?: return
                val type = payload.optString("type")
                val activeRoom = payload.optString("activeRoom").ifBlank {
                    payload.optString("room").ifBlank { null }
                }
                val levelNumber = payload.optString("levelNumber").ifBlank { null }
                listener.onEvent(type, activeRoom, levelNumber)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                listener.onError(t.message ?: "Socket connection failed")
            }
        })
    }

    fun disconnect() {
        socket?.close(1000, "close")
        socket = null
        client?.dispatcher?.executorService?.shutdown()
        client?.connectionPool?.evictAll()
        client = null
    }

    private fun buildWsUrl(serverBaseUrl: String): String {
        val trimmed = serverBaseUrl.trim()
        require(trimmed.isNotEmpty()) { "Server URL is empty" }

        val withScheme = when {
            trimmed.startsWith("http://") -> trimmed.replaceFirst("http://", "ws://")
            trimmed.startsWith("https://") -> trimmed.replaceFirst("https://", "wss://")
            trimmed.startsWith("ws://") || trimmed.startsWith("wss://") -> trimmed
            else -> "ws://$trimmed"
        }

        val noTrailingSlash = withScheme.trimEnd('/')
        return "$noTrailingSlash/ws"
    }
}
