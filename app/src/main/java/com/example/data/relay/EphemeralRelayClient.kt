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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

sealed interface RelayConnectionState {
    data object Unpaired : RelayConnectionState
    data object Paused : RelayConnectionState
    data object Connecting : RelayConnectionState
    data object Connected : RelayConnectionState
    data class Error(val message: String) : RelayConnectionState
}

data class RelaySubscription(
    val chainId: String,
    val relayBaseUrl: String,
    val blindedTopic: String,
    val shouldListen: Boolean
)

data class IncomingRelayMessage(
    val chainId: String,
    val ciphertext: String
)

class EphemeralRelayClient {
    private class ChainSession(
        val chainId: String,
        var relayBaseUrl: String,
        var topic: String,
        var shouldBeConnected: Boolean = false,
        var webSocket: WebSocket? = null,
        var reconnectJob: Job? = null,
        var reconnectAttempts: Int = 0
    )

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

    private val sessions = ConcurrentHashMap<String, ChainSession>()

    private val _chainStates = MutableStateFlow<Map<String, RelayConnectionState>>(emptyMap())
    val chainStates: StateFlow<Map<String, RelayConnectionState>> = _chainStates.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<IncomingRelayMessage>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<IncomingRelayMessage> = _incomingMessages.asSharedFlow()

    fun encodeEnvelope(envelope: RelayEnvelope): String = envelopeAdapter.toJson(envelope)

    fun decodeEnvelope(json: String): RelayEnvelope? = runCatching {
        envelopeAdapter.fromJson(json)
    }.getOrNull()

    fun updateSubscriptions(subscriptions: List<RelaySubscription>) {
        val activeIds = subscriptions.map { it.chainId }.toSet()

        // Disconnect removed chains
        val removedIds = sessions.keys.filterNot { it in activeIds }
        removedIds.forEach { removedId ->
            sessions.remove(removedId)?.let { session ->
                closeSession(session)
            }
            _chainStates.update { it - removedId }
        }

        // Update or create sessions for active chains
        subscriptions.forEach { sub ->
            val normalizedBase = sub.relayBaseUrl.trim().trimEnd('/')
            val existing = sessions[sub.chainId]

            if (!sub.shouldListen || sub.blindedTopic.isBlank()) {
                if (existing != null) {
                    closeSession(existing)
                }
                setChainState(
                    sub.chainId,
                    if (sub.blindedTopic.isBlank()) RelayConnectionState.Unpaired else RelayConnectionState.Paused
                )
                return@forEach
            }

            if (existing != null &&
                existing.shouldBeConnected &&
                existing.relayBaseUrl == normalizedBase &&
                existing.topic == sub.blindedTopic &&
                existing.webSocket != null
            ) {
                return@forEach
            }

            val session = existing?.apply {
                closeSession(this)
                relayBaseUrl = normalizedBase
                topic = sub.blindedTopic
                shouldBeConnected = true
                reconnectAttempts = 0
            } ?: ChainSession(
                chainId = sub.chainId,
                relayBaseUrl = normalizedBase,
                topic = sub.blindedTopic,
                shouldBeConnected = true
            ).also { sessions[sub.chainId] = it }

            connectSession(session)
        }
    }

    private fun closeSession(session: ChainSession) {
        session.shouldBeConnected = false
        session.reconnectJob?.cancel()
        session.reconnectJob = null
        session.webSocket?.close(1000, "Client paused or disconnected")
        session.webSocket = null
    }

    private fun setChainState(chainId: String, state: RelayConnectionState) {
        _chainStates.update { current -> current + (chainId to state) }
    }

    private fun connectSession(session: ChainSession) {
        if (!session.shouldBeConnected) return

        session.reconnectJob?.cancel()
        session.webSocket?.close(1000, "Reconnecting")
        session.webSocket = null

        setChainState(session.chainId, RelayConnectionState.Connecting)

        val wsBase = when {
            session.relayBaseUrl.startsWith("https://") -> "wss://" + session.relayBaseUrl.removePrefix("https://")
            session.relayBaseUrl.startsWith("http://") -> "ws://" + session.relayBaseUrl.removePrefix("http://")
            else -> "wss://${session.relayBaseUrl}"
        }
        val wsUrl = "$wsBase/${session.topic}/ws"

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        session.webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (webSocket != session.webSocket) return
                session.reconnectAttempts = 0
                setChainState(session.chainId, RelayConnectionState.Connected)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (webSocket != session.webSocket) return
                val packet = runCatching { streamAdapter.fromJson(text) }.getOrNull()
                if (packet?.event == "message" && !packet.message.isNullOrBlank()) {
                    _incomingMessages.tryEmit(
                        IncomingRelayMessage(
                            chainId = session.chainId,
                            ciphertext = packet.message
                        )
                    )
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (webSocket != session.webSocket) return
                session.webSocket = null
                if (session.shouldBeConnected) {
                    scheduleReconnect(session)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (webSocket != session.webSocket) return
                session.webSocket = null
                if (session.shouldBeConnected) {
                    setChainState(
                        session.chainId,
                        RelayConnectionState.Error(t.localizedMessage ?: "Relay connection interrupted")
                    )
                    scheduleReconnect(session)
                }
            }
        })
    }

    private fun scheduleReconnect(session: ChainSession) {
        if (!session.shouldBeConnected) return
        session.reconnectJob?.cancel()
        session.reconnectJob = scope.launch {
            val backoffMs = (2000L * (1L shl session.reconnectAttempts.coerceAtMost(4))).coerceAtMost(30_000L)
            session.reconnectAttempts++
            delay(backoffMs)
            if (session.shouldBeConnected && sessions[session.chainId] === session) {
                connectSession(session)
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
