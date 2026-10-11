package com.synax.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class SynaxRealtime(private val onEvent: (JSONObject) -> Unit) {
    private val client = OkHttpClient.Builder().pingInterval(25, TimeUnit.SECONDS).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var socket: WebSocket? = null
    private var token: String? = null
    private var reconnectJob: Job? = null
    private var heartbeatJob: Job? = null
    private val stopped = AtomicBoolean(true)
    private var attempts = 0

    @Synchronized fun connect(authToken: String) {
        if (!stopped.get() && token == authToken && socket != null) return
        token = authToken
        stopped.set(false)
        openSocket()
    }

    @Synchronized private fun openSocket() {
        if (stopped.get()) return
        socket?.cancel()
        val request = Request.Builder().url("wss://synax.online/ws").build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                attempts = 0
                webSocket.send(JSONObject().put("type", "auth").put("token", token).put("active", true).toString())
                onEvent(JSONObject().put("type", "connection:open"))
                heartbeatJob?.cancel()
                heartbeatJob = scope.launch {
                    while (isActive && !stopped.get()) {
                        delay(4_000)
                        webSocket.send(JSONObject().put("type", "presence:heartbeat").put("active", true).put("timestamp", System.currentTimeMillis()).toString())
                    }
                }
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val event = JSONObject(text)
                    onEvent(event)
                    if (event.optString("type") == "auth:success") webSocket.send(JSONObject().put("type", "chat:active").put("active", true).toString())
                } catch (_: Exception) { onEvent(JSONObject().put("type", "realtime:parse_error")) }
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, reason) }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { scheduleReconnect() }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onEvent(JSONObject().put("type", "connection:closed").put("message", t.message ?: "Disconnected"))
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (stopped.get()) return
        reconnectJob?.cancel()
        val wait = (500L * (1L shl attempts.coerceIn(0, 5))).coerceAtMost(15_000L)
        attempts++
        reconnectJob = scope.launch { delay(wait); synchronized(this@SynaxRealtime) { openSocket() } }
    }

    fun send(event: JSONObject): Boolean = socket?.send(event.toString()) == true

    @Synchronized fun disconnect() {
        stopped.set(true)
        heartbeatJob?.cancel(); heartbeatJob = null
        reconnectJob?.cancel(); reconnectJob = null
        socket?.close(1000, "logout"); socket = null
        token = null
    }

    fun dispose() { disconnect(); scope.cancel(); client.dispatcher.executorService.shutdown() }
}
