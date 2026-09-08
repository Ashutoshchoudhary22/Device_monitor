package com.devicemonitor.app.data.socket

import com.devicemonitor.app.BuildConfig
import com.devicemonitor.app.data.prefs.TokenManager
import io.socket.client.Ack
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import kotlin.coroutines.resume

class SocketManager(private val tokenManager: TokenManager) {

    private var socket: Socket? = null
    private var onReconnectListener: (() -> Unit)? = null

    fun setOnReconnectListener(listener: (() -> Unit)?) {
        onReconnectListener = listener
    }

    @Synchronized
    fun connect(): Socket {
        val token = tokenManager.getToken()
            ?: throw IllegalStateException("Not authenticated")

        if (socket?.connected() == true) {
            return socket!!
        }

        if (socket != null && !socket!!.connected()) {
            socket!!.connect()
            return socket!!
        }

        disconnect()

        val options = IO.Options().apply {
            auth = mapOf("token" to token)
            reconnection = true
            reconnectionAttempts = Int.MAX_VALUE
            reconnectionDelay = 1000
            reconnectionDelayMax = 5000
            transports = arrayOf("websocket", "polling")
            timeout = 20000
        }

        socket = IO.socket(BuildConfig.SOCKET_URL, options).apply {
            on(Socket.EVENT_CONNECT) {
                onReconnectListener?.invoke()
            }
        }
        socket!!.connect()
        return socket!!
    }

    @Synchronized
    fun connectGuest(): Socket {
        disconnect()

        val options = IO.Options().apply {
            reconnection = true
            transports = arrayOf("websocket", "polling")
            timeout = 20000
        }

        socket = IO.socket(BuildConfig.SOCKET_URL, options)
        socket!!.connect()
        return socket!!
    }

    @Synchronized
    fun disconnect() {
        socket?.off(Socket.EVENT_CONNECT)
        socket?.disconnect()
        socket?.off()
        socket = null
    }

    fun isConnected(): Boolean = socket?.connected() == true

    @Synchronized
    fun ensureConnected(): Socket {
        return if (isConnected()) socket!! else connect()
    }

    /**
     * Waits up to [CONNECT_WAIT_MS] for the socket to become connected before emitting.
     * This prevents the "socket timeout" that happens when emit fires before the
     * TCP/WebSocket handshake completes.
     */
    private suspend fun waitForConnection(sock: Socket): Boolean {
        if (sock.connected()) return true
        return withTimeoutOrNull(CONNECT_WAIT_MS) {
            suspendCancellableCoroutine { cont ->
                sock.once(Socket.EVENT_CONNECT) { cont.resume(true) }
                sock.once(Socket.EVENT_CONNECT_ERROR) { cont.resume(false) }
            }
        } ?: false
    }

    suspend fun emitAck(event: String, data: JSONObject, guest: Boolean = false): JSONObject {
        return withTimeoutOrNull(EMIT_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                try {
                    val activeSocket = when {
                        guest -> connectGuest()
                        else -> ensureConnected()
                    }

                    // If socket is not yet connected, wait for connection before emitting.
                    // Without this, emit fires immediately and the ack never arrives because
                    // the underlying transport isn't ready yet — causing a false timeout.
                    if (!activeSocket.connected()) {
                        activeSocket.once(Socket.EVENT_CONNECT) {
                            activeSocket.emit(event, data, Ack { args ->
                                val response = args.firstOrNull() as? JSONObject
                                    ?: JSONObject().put("ok", false).put("error", "Empty response")
                                if (cont.isActive) cont.resume(response)
                            })
                        }
                        activeSocket.once(Socket.EVENT_CONNECT_ERROR) { args ->
                            val err = args.firstOrNull()?.toString() ?: "Connection failed"
                            if (cont.isActive) cont.resume(
                                JSONObject().put("ok", false).put("error", err)
                            )
                        }
                    } else {
                        activeSocket.emit(event, data, Ack { args ->
                            val response = args.firstOrNull() as? JSONObject
                                ?: JSONObject().put("ok", false).put("error", "Empty response")
                            if (cont.isActive) cont.resume(response)
                        })
                    }
                } catch (e: Exception) {
                    cont.resume(
                        JSONObject()
                            .put("ok", false)
                            .put("error", e.message ?: "Socket error")
                    )
                }
            }
        } ?: JSONObject().put("ok", false).put("error", "Socket timeout")
    }

    companion object {
        private const val EMIT_TIMEOUT_MS = 20_000L   // increased from 8s — gives time to connect + get ack
        private const val CONNECT_WAIT_MS = 15_000L   // max wait for socket handshake before emitting
    }
}
