package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class OtpDirection {
    INBOUND,
    OUTBOUND
}

@Entity(tableName = "synced_otps")
data class SyncedOtpEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageUuid: String,
    val serviceName: String,
    val otpCode: String,
    val rawSnippet: String,
    val direction: String, // "INBOUND" or "OUTBOUND"
    val peerDeviceName: String,
    val timestamp: Long,
    val expiresAt: Long, // Long.MAX_VALUE if manual retention
    val isCopied: Boolean = false,
    val chainId: String = ""
)
