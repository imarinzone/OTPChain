package com.example.data.relay

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

sealed interface RelayConnectionState {
    data object Unpaired : RelayConnectionState
    data object Paused : RelayConnectionState
    data object Connecting : RelayConnectionState
    data object Connected : RelayConnectionState
    data class Error(val message: String) : RelayConnectionState
}

class EphemeralRelayClient {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val client = OkHttpClient.Builder()
        .pingInterval(25, TimeUnit.SECONDS)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Required for persistent WebSocket
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val streamAdapter = moshi.adapter(NtfyStreamPacket::class.java)
    private val envelopeAdapter = moshi.adapter(RelayEnvelope::class.java)

    private val _connectionState = MutableStateFlow<RelayConnectionState>(RelayConnectionState.Unpaired)
    val connectionState: StateFlow<RelayConnectionState> = _connectionState.asStateFlow()

    private val _incomingCiphertexts = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val incomingCiphertexts: SharedFlow<String> = _incomingCiphertexts.asSharedFlow()

    private var activeWebSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var currentRelayBaseUrl: String? = null
    private var currentTopic: String? = null
    private var shouldBeConnected: Boolean = false
    private var reconnectAttempts: Int = 0

    fun encodeEnvelope(envelope: RelayEnvelope): String = envelopeAdapter.toJson(envelope)

    fun decodeEnvelope(json: String): RelayEnvelope? = runCatching {
        envelopeAdapter.fromJson(json)
    }.getOrNull()

    fun updateSubscription(
        hasChain: Boolean,
        shouldListen: Boolean,
        relayBaseUrl: String,
        blindedTopic: String?
    ) {
        if (!hasChain || blindedTopic.isNullOrBlank()) {
            disconnect(RelayConnectionState.Unpaired)
            return
        }
        if (!shouldListen) {
            disconnect(RelayConnectionState.Paused)
            return
        }

        val normalizedBase = relayBaseUrl.trim().trimEnd('/')
        if (shouldBeConnected &&
            currentRelayBaseUrl == normalizedBase &&
            currentTopic == blindedTopic &&
            activeWebSocket != null
        ) {
            return
        }

        shouldBeConnected = true
        currentRelayBaseUrl = normalizedBase
        currentTopic = blindedTopic
        reconnectAttempts = 0
        connectInternal()
    }

    private fun disconnect(targetState: RelayConnectionState) {
        shouldBeConnected = false
        currentTopic = null
        reconnectJob?.cancel()
        reconnectJob = null
        activeWebSocket?.close(1000, "Client paused or disconnected")
        activeWebSocket = null
        _connectionState.value = targetState
    }

    private fun connectInternal() {
        val baseUrl = currentRelayBaseUrl ?: return
        val topic = currentTopic ?: return
        if (!shouldBeConnected) return

        reconnectJob?.cancel()
        activeWebSocket?.close(1000, "Reconnecting")
        activeWebSocket = null

        _connectionState.value = RelayConnectionState.Connecting

        val wsBase = when {
            baseUrl.startsWith("https://") -> "wss://" + baseUrl.removePrefix("https://")
            baseUrl.startsWith("http://") -> "ws://" + baseUrl.removePrefix("http://")
            else -> "wss://$baseUrl"
        }
        val wsUrl = "$wsBase/$topic/ws"

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        activeWebSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (webSocket != activeWebSocket) return
                reconnectAttempts = 0
                _connectionState.value = RelayConnectionState.Connected
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (webSocket != activeWebSocket) return
                val packet = runCatching { streamAdapter.fromJson(text) }.getOrNull()
                if (packet?.event == "message" && !packet.message.isNullOrBlank()) {
                    _incomingCiphertexts.tryEmit(packet.message)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (webSocket != activeWebSocket) return
                activeWebSocket = null
                if (shouldBeConnected) {
                    scheduleReconnect()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (webSocket != activeWebSocket) return
                activeWebSocket = null
                if (shouldBeConnected) {
                    _connectionState.value = RelayConnectionState.Error(
                        t.localizedMessage ?: "Relay connection interrupted"
                    )
                    scheduleReconnect()
                }
            }
        })
    }

    private fun scheduleReconnect() {
        if (!shouldBeConnected) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val backoffMs = (2000L * (1L shl reconnectAttempts.coerceAtMost(4))).coerceAtMost(30_000L)
            reconnectAttempts++
            delay(backoffMs)
            if (shouldBeConnected) {
                connectInternal()
            }
        }
    }

    suspend fun publishCiphertext(
        relayBaseUrl: String,
        blindedTopic: String,
        ciphertextToken: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val base = relayBaseUrl.trim().trimEnd('/')
            val url = "$base/$blindedTopic"
            val body = ciphertextToken.toRequestBody("text/plain; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(body)
                .header("X-Cache", "no") // Enforce zero server-side storage on relay
                .header("X-Title", "OTP Chain E2EE")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Relay returned HTTP ${response.code}")
                }
            }
        }
    }
}
