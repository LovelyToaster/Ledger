package com.verdantgem.ledger.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.verdantgem.ledger.data.model.BrandMapping
import com.verdantgem.ledger.data.model.Budget
import com.verdantgem.ledger.data.model.Category
import com.verdantgem.ledger.data.model.Record

@Database(entities = [Category::class, Record::class, Budget::class, BrandMapping::class], version = 11, exportSchema = false)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun recordDao(): RecordDao
    abstract fun categoryDao(): CategoryDao
    abstract fun budgetDao(): BudgetDao
    abstract fun brandMappingDao(): BrandMappingDao

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

        val MIGRATION_6_7 = Migration(6, 7) { db ->
            db.execSQL("ALTER TABLE records ADD COLUMN syncUuid TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE categories ADD COLUMN syncUuid TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE budgets ADD COLUMN syncUuid TEXT NOT NULL DEFAULT ''")
            // 不在此处生成 UUID：由首次同步推送的设备统一生成，确保跨设备 UUID 一致
        }

        val MIGRATION_7_8 = Migration(7, 8) { db ->
            db.execSQL("""
                CREATE TABLE brand_mappings (
                    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    brandName TEXT NOT NULL,
                    categoryId INTEGER NOT NULL,
                    source TEXT NOT NULL DEFAULT 'default',
                    hitCount INTEGER NOT NULL DEFAULT 0,
                    updatedAt INTEGER NOT NULL DEFAULT 0
                )
            """)
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_brand_mappings_brandName` ON brand_mappings(`brandName`)")
        }

        val MIGRATION_8_9 = Migration(8, 9) { db ->
            // 修复 v8 中索引名与 Room 实体注解不匹配的 bug
            db.execSQL("DROP INDEX IF EXISTS idx_brand_mappings_name")
            db.execSQL("DROP INDEX IF EXISTS `index_brand_mappings_brandName`")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_brand_mappings_brandName` ON brand_mappings(`brandName`)")
        }

        val MIGRATION_9_10 = Migration(9, 10) { db ->
            db.execSQL("ALTER TABLE records DROP COLUMN excludeFromBudget")
        }

        val MIGRATION_10_11 = Migration(10, 11) { db ->
            // 清理因跨设备 UUID 不一致导致同步合并产生的重复类别行
            // 同一 (name, parentName, isIncome) 组内保留 id 最小的，软删除其余
            db.execSQL("""
                UPDATE categories SET deleted = 1, updatedAt = ${System.currentTimeMillis()}
                WHERE id NOT IN (
                    SELECT MIN(id) FROM categories
                    WHERE deleted = 0
                    GROUP BY name, IFNULL(parentName, ''), isIncome
                )
                AND deleted = 0
            """)
        }
    }
}
