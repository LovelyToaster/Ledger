package com.verdantgem.ledger.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey val id: Int = 1,
    val monthlyAmount: Double,
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false
)
