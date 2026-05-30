package com.verdantgem.ledger.data.local

import androidx.room.*
import com.verdantgem.ledger.data.model.BrandMapping
import kotlinx.coroutines.flow.Flow

@Dao
interface BrandMappingDao {
    @Query("SELECT * FROM brand_mappings")
    fun getAllMappings(): Flow<List<BrandMapping>>

    @Query("SELECT * FROM brand_mappings")
    suspend fun getAllMappingsList(): List<BrandMapping>

    @Query("SELECT COUNT(*) FROM brand_mappings")
    suspend fun getCount(): Long

    @Query("SELECT * FROM brand_mappings WHERE brandName = :name LIMIT 1")
    suspend fun getByBrandName(name: String): BrandMapping?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(mapping: BrandMapping)

    @Query("SELECT * FROM brand_mappings WHERE categoryId = :categoryId")
    suspend fun getByCategoryId(categoryId: Long): List<BrandMapping>

    @Query("DELETE FROM brand_mappings WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM brand_mappings")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(mappings: List<BrandMapping>)
}
