package com.verdantgem.ledger.di

import android.content.Context
import androidx.room.Room
import com.verdantgem.ledger.data.local.BrandMappingDao
import com.verdantgem.ledger.data.local.BudgetDao
import com.verdantgem.ledger.data.local.CategoryDao
import com.verdantgem.ledger.data.local.LedgerDatabase
import com.verdantgem.ledger.data.local.RecordDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LedgerDatabase {
        return Room.databaseBuilder(
            context,
            LedgerDatabase::class.java,
            LedgerDatabase.DATABASE_NAME
        ).addMigrations(
            LedgerDatabase.MIGRATION_1_2,
            LedgerDatabase.MIGRATION_2_3,
            LedgerDatabase.MIGRATION_3_4,
            LedgerDatabase.MIGRATION_4_5,
            LedgerDatabase.MIGRATION_5_6,
            LedgerDatabase.MIGRATION_6_7,
            LedgerDatabase.MIGRATION_7_8,
            LedgerDatabase.MIGRATION_8_9,
            LedgerDatabase.MIGRATION_9_10,
            LedgerDatabase.MIGRATION_10_11,
            LedgerDatabase.MIGRATION_11_12
        ).build()
    }

    @Provides
    fun provideRecordDao(database: LedgerDatabase): RecordDao {
        return database.recordDao()
    }

    @Provides
    fun provideCategoryDao(database: LedgerDatabase): CategoryDao {
        return database.categoryDao()
    }

    @Provides
    fun provideBudgetDao(database: LedgerDatabase): BudgetDao {
        return database.budgetDao()
    }

    @Provides
    fun provideBrandMappingDao(database: LedgerDatabase): BrandMappingDao {
        return database.brandMappingDao()
    }
}
