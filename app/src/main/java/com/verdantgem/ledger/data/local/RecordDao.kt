package com.verdantgem.ledger.data.local

import androidx.paging.PagingSource
import androidx.room.*
import com.verdantgem.ledger.data.model.Record
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordDao {
    @Query("SELECT * FROM records WHERE deleted = 0 ORDER BY date DESC")
    fun getAllRecords(): Flow<List<Record>>

    @Query("SELECT * FROM records WHERE deleted = 0 ORDER BY date DESC")
    fun getPagingSource(): PagingSource<Int, Record>

    @Query("SELECT * FROM records WHERE deleted = 0 AND (note LIKE '%' || :query || '%' OR categoryName LIKE '%' || :query || '%') ORDER BY date DESC")
    fun getSearchPagingSource(query: String): PagingSource<Int, Record>

    @Query("SELECT * FROM records WHERE deleted = 0 AND (note LIKE '%' || :query || '%' OR categoryName LIKE '%' || :query || '%') AND date >= :startTime AND date <= :endTime ORDER BY date DESC")
    fun getSearchWithDateRangePagingSource(query: String, startTime: Long, endTime: Long): PagingSource<Int, Record>

    @Query("SELECT * FROM records WHERE deleted = 0 AND date >= :startTime AND date <= :endTime ORDER BY date DESC")
    fun getRecordsInDateRange(startTime: Long, endTime: Long): PagingSource<Int, Record>

    @Query("SELECT * FROM records WHERE deleted = 0 AND categoryName = :categoryName AND date >= :startTime AND date <= :endTime ORDER BY date DESC")
    fun getRecordsByCategoryPagingSource(categoryName: String, startTime: Long, endTime: Long): PagingSource<Int, Record>

    @Query("SELECT * FROM records WHERE deleted = 0 AND (note LIKE '%' || :query || '%' OR categoryName LIKE '%' || :query || '%') AND categoryName = :categoryName AND date >= :startTime AND date <= :endTime ORDER BY date DESC")
    fun getSearchWithCategoryAndDateRangePagingSource(query: String, categoryName: String, startTime: Long, endTime: Long): PagingSource<Int, Record>

    @Query("SELECT * FROM records WHERE deleted = 0 AND categoryName = :categoryName ORDER BY date DESC")
    fun getRecordsByCategoryOnlyPagingSource(categoryName: String): PagingSource<Int, Record>

    @Query("SELECT * FROM records WHERE deleted = 0 AND (note LIKE '%' || :query || '%' OR categoryName LIKE '%' || :query || '%') AND categoryName = :categoryName ORDER BY date DESC")
    fun getSearchWithCategoryOnlyPagingSource(query: String, categoryName: String): PagingSource<Int, Record>

    @Query("SELECT * FROM records WHERE deleted = 0 AND categoryName IN (SELECT name FROM categories WHERE parentName = :parentName AND isIncome = :isIncome) AND date >= :startTime AND date <= :endTime ORDER BY date DESC")
    fun getRecordsByParentCategoryPagingSource(parentName: String, isIncome: Boolean, startTime: Long, endTime: Long): PagingSource<Int, Record>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: Record): Long

    @Delete
    suspend fun deleteRecord(record: Record)

    @Query("UPDATE records SET deleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteRecord(id: Long, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM records WHERE deleted = 1")
    suspend fun purgeDeletedRecords()

    @Query("SELECT * FROM records WHERE deleted = 1")
    suspend fun getDeletedRecords(): List<Record>

    @Query("SELECT * FROM records WHERE id = :id")
    suspend fun getRecordById(id: Long): Record?

    @Query("DELETE FROM records WHERE id IN (:ids)")
    suspend fun deleteRecordsByIds(ids: Set<Long>)

    @Query("SELECT * FROM records WHERE note = :note AND deleted = 0 ORDER BY date DESC LIMIT 1")
    suspend fun findLastRecordByNote(note: String): Record?

    @Query("SELECT COALESCE(SUM(amount), 0) FROM records WHERE deleted = 0 AND categoryName NOT IN (SELECT name FROM categories WHERE isIncome = 1)")
    fun getTotalExpenseFlow(): Flow<Double>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM records WHERE deleted = 0 AND categoryName IN (SELECT name FROM categories WHERE isIncome = 1)")
    fun getTotalIncomeFlow(): Flow<Double>

    @Query("SELECT COUNT(*) FROM records WHERE deleted = 0")
    fun getRecordCountFlow(): Flow<Int>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM records WHERE deleted = 0 AND date >= :start AND date <= :end AND categoryName NOT IN (SELECT name FROM categories WHERE isIncome = 1)")
    fun getMonthlyExpenseFlow(start: Long, end: Long): Flow<Double>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM records WHERE deleted = 0 AND date >= :start AND date <= :end AND categoryName IN (SELECT name FROM categories WHERE isIncome = 1)")
    fun getMonthlyIncomeFlow(start: Long, end: Long): Flow<Double>

    @Query("SELECT SUM(amount) FROM records WHERE deleted = 0 AND date >= :start AND date <= :end")
    fun getSumAmountInRange(start: Long, end: Long): Flow<Double?>

    @Query("SELECT * FROM records ORDER BY updatedAt ASC")
    suspend fun getAllRecordsForSync(): List<Record>

    @Query("SELECT * FROM records WHERE syncUuid = :syncUuid LIMIT 1")
    suspend fun getRecordBySyncUuid(syncUuid: String): Record?

    @Query("UPDATE records SET updatedAt = :now WHERE id = :id")
    suspend fun touchUpdatedAt(id: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE records SET date = :billDate, updatedAt = :now WHERE id = :id")
    suspend fun updateBillDate(id: Long, billDate: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE records SET note = :note, updatedAt = :now WHERE id = :id")
    suspend fun updateRecordNote(id: Long, note: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE records SET categoryId = :categoryId, categoryName = :categoryName, updatedAt = :now WHERE id = :id")
    suspend fun updateRecordCategory(id: Long, categoryId: Long, categoryName: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE records SET amount = :amount, updatedAt = :now WHERE id = :id")
    suspend fun updateRecordAmount(id: Long, amount: Double, now: Long = System.currentTimeMillis())
}
