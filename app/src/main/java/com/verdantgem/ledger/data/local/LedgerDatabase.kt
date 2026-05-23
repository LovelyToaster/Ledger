package com.verdantgem.ledger.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.verdantgem.ledger.data.model.Budget
import com.verdantgem.ledger.data.model.Category
import com.verdantgem.ledger.data.model.Record

@Database(entities = [Category::class, Record::class, Budget::class], version = 6, exportSchema = false)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun recordDao(): RecordDao
    abstract fun categoryDao(): CategoryDao
    abstract fun budgetDao(): BudgetDao

    companion object {
        const val DATABASE_NAME = "ledger_db"
        val MIGRATION_1_2 = Migration(1, 2) { db ->
            db.execSQL("ALTER TABLE categories ADD COLUMN prompts TEXT NOT NULL DEFAULT ''")
        }
        val MIGRATION_2_3 = Migration(2, 3) { db ->
            db.execSQL("ALTER TABLE records ADD COLUMN address TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE records ADD COLUMN latitude REAL")
            db.execSQL("ALTER TABLE records ADD COLUMN longitude REAL")
        }
        val MIGRATION_3_4 = Migration(3, 4) { db ->
            db.execSQL("CREATE TABLE budgets (id INTEGER NOT NULL PRIMARY KEY, monthlyAmount REAL NOT NULL, updatedAt INTEGER NOT NULL)")
        }
        val MIGRATION_4_5 = Migration(4, 5) { db ->
            db.execSQL("ALTER TABLE records ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE records ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE categories ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE categories ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE budgets ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE records SET updatedAt = createdAt")
            db.execSQL("UPDATE categories SET updatedAt = (SELECT COALESCE(MAX(createdAt), 0) FROM records)")
        }

        val MIGRATION_5_6 = Migration(5, 6) { db ->
            db.execSQL("ALTER TABLE records ADD COLUMN excludeFromBudget INTEGER NOT NULL DEFAULT 0")
        }
    }
}
