package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncedOtpDao {
    @Query("SELECT * FROM synced_otps ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<SyncedOtpEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: SyncedOtpEntity): Long

    @Query("SELECT EXISTS(SELECT 1 FROM synced_otps WHERE messageUuid = :uuid)")
    suspend fun existsByUuid(uuid: String): Boolean

    @Query("UPDATE synced_otps SET isCopied = 1 WHERE id = :id")
    suspend fun markCopiedById(id: Long)

    @Query("UPDATE synced_otps SET isCopied = 1 WHERE messageUuid = :uuid")
    suspend fun markCopiedByUuid(uuid: String)

    @Query("DELETE FROM synced_otps WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM synced_otps WHERE expiresAt < :nowEpochMs")
    suspend fun purgeExpired(nowEpochMs: Long)

    @Query("DELETE FROM synced_otps WHERE chainId = :chainId")
    suspend fun deleteByChainId(chainId: String)

    @Query("DELETE FROM synced_otps")
    suspend fun clearAll()
}
