package com.verdantgem.ledger.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.verdantgem.ledger.data.model.Budget
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE id = 1 AND deleted = 0")
    fun getBudgetFlow(): Flow<Budget?>

    @Upsert
    suspend fun upsertBudget(budget: Budget)

    @Query("UPDATE budgets SET deleted = 1, updatedAt = :now WHERE id = 1")
    suspend fun softDeleteBudget(now: Long = System.currentTimeMillis())

    @Query("DELETE FROM budgets WHERE deleted = 1")
    suspend fun purgeDeletedBudgets()

    @Query("SELECT * FROM budgets WHERE id = 1")
    suspend fun getBudgetForSync(): Budget?

    @Query("SELECT * FROM budgets WHERE syncUuid = :syncUuid LIMIT 1")
    suspend fun getBudgetBySyncUuid(syncUuid: String): Budget?
}
