package com.devicemonitor.app.data.socket

import com.devicemonitor.app.BuildConfig
import com.devicemonitor.app.data.prefs.TokenManager
import io.socket.client.Ack
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume

class SocketManager(private val tokenManager: TokenManager) {

    private var socket: Socket? = null

    @Synchronized
    fun connect(): Socket {
        val token = tokenManager.getToken()
            ?: throw IllegalStateException("Not authenticated")

        if (socket?.connected() == true) {
            return socket!!
        }

        disconnect()

        val options = IO.Options().apply {
            auth = mapOf("token" to token)
            reconnection = true
            reconnectionAttempts = Int.MAX_VALUE
            reconnectionDelay = 1000
            transports = arrayOf("websocket", "polling")
            timeout = 15000
        }

        socket = IO.socket(BuildConfig.SOCKET_URL, options)
        socket!!.connect()
        return socket!!
    }

    @Synchronized
    fun connectGuest(): Socket {
        disconnect()

        val options = IO.Options().apply {
            reconnection = true
            transports = arrayOf("websocket", "polling")
            timeout = 15000
        }

        socket = IO.socket(BuildConfig.SOCKET_URL, options)
        socket!!.connect()
        return socket!!
    }

    @Synchronized
    fun disconnect() {
        socket?.disconnect()
        socket?.off()
        socket = null
    }

    fun isConnected(): Boolean = socket?.connected() == true

    suspend fun emitAck(event: String, data: JSONObject, guest: Boolean = false): JSONObject {
        return suspendCancellableCoroutine { cont ->
            try {
                val activeSocket = when {
                    guest -> connectGuest()
                    isConnected() -> socket!!
                    else -> connect()
                }

                activeSocket.emit(event, data, Ack { args ->
                    val response = args.firstOrNull() as? JSONObject
                        ?: JSONObject().put("ok", false).put("error", "Empty response")
                    cont.resume(response)
                })
            } catch (e: Exception) {
                cont.resume(
                    JSONObject()
                        .put("ok", false)
                        .put("error", e.message ?: "Socket error")
                )
            }
        }
    }
}
