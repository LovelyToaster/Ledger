package com.verdantgem.ledger.data.local

import androidx.room.*
import com.verdantgem.ledger.data.model.Category
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE deleted = 0 ORDER BY isIncome ASC, name ASC")
    fun getAllCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE deleted = 0 ORDER BY isIncome ASC, name ASC")
    suspend fun getAllCategoriesList(): List<Category>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategories(categories: List<Category>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(category: Category)

    @Query("SELECT * FROM categories WHERE deleted = 0 AND name = :name LIMIT 1")
    suspend fun getCategoryByName(name: String): Category?

    @Delete
    suspend fun deleteCategory(category: Category)

    @Query("UPDATE categories SET deleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteCategory(id: Long, now: Long = System.currentTimeMillis())

    @Delete
    suspend fun deleteCategoryById(category: Category)

    @Query("DELETE FROM categories WHERE deleted = 1")
    suspend fun purgeDeletedCategories()

    @Update
    suspend fun updateCategory(category: Category)

    @Query("SELECT COUNT(*) FROM categories WHERE deleted = 0")
    suspend fun getCategoryCount(): Long

    @Query("DELETE FROM categories")
    suspend fun deleteAll()

    @Query("SELECT * FROM categories ORDER BY updatedAt ASC")
    suspend fun getAllCategoriesForSync(): List<Category>

    @Query("SELECT * FROM categories WHERE syncUuid = :syncUuid LIMIT 1")
    suspend fun getCategoryBySyncUuid(syncUuid: String): Category?

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun getCategoryById(id: Long): Category?

    @Query("""
        SELECT * FROM categories
        WHERE name = :name
          AND IFNULL(parentName, '') = IFNULL(:parentName, '')
          AND isIncome = :isIncome
          AND deleted = 0
        LIMIT 1
    """)
    suspend fun getCategoryByNameAndParent(name: String, parentName: String?, isIncome: Boolean): Category?
}
