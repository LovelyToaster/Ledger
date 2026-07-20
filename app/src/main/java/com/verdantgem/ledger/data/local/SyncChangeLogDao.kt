package com.verdantgem.ledger.data.local

import androidx.room.*

@Dao
interface SyncChangeLogDao {
    @Insert
    suspend fun insert(entry: SyncChangeLog)

    @Insert
    suspend fun insertAll(entries: List<SyncChangeLog>)

    @Query("SELECT * FROM sync_change_log WHERE synced = 0 ORDER BY seq ASC")
    suspend fun getUnsyncedChanges(): List<SyncChangeLog>

    @Query("SELECT COALESCE(MAX(seq), 0) FROM sync_change_log")
    suspend fun getMaxSeq(): Int

    @Query("SELECT COALESCE(MAX(seq), 0) + 1 FROM sync_change_log")
    suspend fun getNextSeq(): Int

    @Transaction
    suspend fun appendNext(entry: SyncChangeLog): SyncChangeLog {
        val nextSeq = getNextSeq()
        val withSeq = entry.copy(seq = nextSeq)
        insert(withSeq)
        return withSeq
    }

    @Transaction
    suspend fun appendAll(entries: List<SyncChangeLog>) {
        for (entry in entries) {
            val nextSeq = getNextSeq()
            insert(entry.copy(seq = nextSeq))
        }
    }

    @Query("UPDATE sync_change_log SET synced = 1 WHERE seq <= :upToSeq AND synced = 0")
    suspend fun markSynced(upToSeq: Int)

    @Query("SELECT COUNT(*) FROM sync_change_log WHERE synced = 0")
    suspend fun getUnsyncedCount(): Int

    @Query("DELETE FROM sync_change_log WHERE synced = 1 AND seq <= :upToSeq")
    suspend fun deleteSyncedUpTo(upToSeq: Int)

    @Query("SELECT COUNT(*) FROM sync_change_log")
    suspend fun getTotalCount(): Int
}
