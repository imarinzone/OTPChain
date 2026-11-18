package com.example.security

import android.net.Uri

data class PairingPayload(
    val chainId: String,
    val masterKeyBase64: String,
    val peerDeviceName: String,
    val relayBaseUrl: String
) {
    fun toUriString(): String {
        return Uri.Builder()
            .scheme("codechain")
            .authority("pair")
            .appendQueryParameter("v", "1")
            .appendQueryParameter("id", chainId)
            .appendQueryParameter("k", masterKeyBase64)
            .appendQueryParameter("n", peerDeviceName)
            .appendQueryParameter("r", relayBaseUrl)
            .build()
            .toString()
    }

    companion object {
        const val DEFAULT_RELAY_URL = "https://ntfy.sh"

        fun parse(rawInput: String): Result<PairingPayload> = runCatching {
            val trimmed = rawInput.trim()
            val uri = Uri.parse(trimmed)
            require(uri.scheme == "codechain" && uri.host == "pair") {
                "Not a valid CodeChain QR pairing link."
            }
            val version = uri.getQueryParameter("v")
            require(version == "1") { "Unsupported pairing QR version." }

            val chainId = uri.getQueryParameter("id")?.takeIf { it.isNotBlank() }
                ?: error("Missing chain identifier in QR.")
            val keyB64 = uri.getQueryParameter("k")?.takeIf { it.isNotBlank() }
                ?: error("Missing encryption key in QR.")
            val decodedKey = E2eeCryptoManager.decodeBase64Url(keyB64)
            require(decodedKey.size == 32) { "Invalid 256-bit key length." }

            val peerName = uri.getQueryParameter("n")?.takeIf { it.isNotBlank() } ?: "Paired Device"
            val relay = uri.getQueryParameter("r")?.takeIf { it.startsWith("http") } ?: DEFAULT_RELAY_URL

            PairingPayload(
                chainId = chainId,
                masterKeyBase64 = keyB64,
                peerDeviceName = peerName,
                relayBaseUrl = relay.trimEnd('/')
            )
        }
    }
}
