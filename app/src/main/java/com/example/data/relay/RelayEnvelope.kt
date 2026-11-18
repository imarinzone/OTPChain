package com.example.data.relay

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RelayEnvelope(
    val messageUuid: String,
    val type: String, // "OTP", "PAIR_ACK", "PING", "UNPAIR"
    val senderDeviceId: String,
    val senderDeviceName: String,
    val otpCode: String = "",
    val serviceName: String = "",
    val snippet: String = "",
    val sentAtEpochMs: Long
)

@JsonClass(generateAdapter = true)
data class NtfyStreamPacket(
    val id: String? = null,
    val time: Long? = null,
    val event: String? = null,
    val topic: String? = null,
    val message: String? = null
)
