package com.verdantgem.ledger.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey val id: Int = 1,
    val syncUuid: String = UUID.randomUUID().toString(),
    val monthlyAmount: Double,
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false
)
