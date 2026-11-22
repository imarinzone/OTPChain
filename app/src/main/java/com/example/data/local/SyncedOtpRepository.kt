package com.example.data.local

import kotlinx.coroutines.flow.Flow

class SyncedOtpRepository(private val dao: SyncedOtpDao) {
    val allOtps: Flow<List<SyncedOtpEntity>> = dao.observeAll()

    suspend fun insertIfNew(entity: SyncedOtpEntity): Boolean {
        if (dao.existsByUuid(entity.messageUuid)) return false
        return dao.insert(entity) > 0
    }

    suspend fun markCopiedById(id: Long) = dao.markCopiedById(id)

    suspend fun markCopiedByUuid(uuid: String) = dao.markCopiedByUuid(uuid)

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun purgeExpired(nowEpochMs: Long = System.currentTimeMillis()) =
        dao.purgeExpired(nowEpochMs)

    suspend fun deleteByChainId(chainId: String) = dao.deleteByChainId(chainId)

    suspend fun clearAll() = dao.clearAll()
}
