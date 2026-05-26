package com.verdantgem.ledger.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.verdantgem.ledger.data.local.BrandMappingDao
import com.verdantgem.ledger.data.local.BudgetDao
import com.verdantgem.ledger.data.local.CategoryDao
import com.verdantgem.ledger.data.local.RecordDao
import com.verdantgem.ledger.data.model.BrandMapping
import com.verdantgem.ledger.data.model.Budget
import com.verdantgem.ledger.data.model.Category
import com.verdantgem.ledger.data.model.Record
import com.verdantgem.ledger.data.DataChangeNotifier
import com.verdantgem.ledger.data.remote.AddressResult
import com.verdantgem.ledger.domain.parser.SmartParser
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LedgerRepository @Inject constructor(
    private val recordDao: RecordDao,
    private val categoryDao: CategoryDao,
    private val budgetDao: BudgetDao,
    private val brandMappingDao: BrandMappingDao,
    private val changeNotifier: DataChangeNotifier
) {
    val allRecords: Flow<List<Record>> = recordDao.getAllRecords()
    val allCategories: Flow<List<Category>> = categoryDao.getAllCategories()
    val allBrandMappings: Flow<List<BrandMapping>> = brandMappingDao.getAllMappings()

    val totalExpenseFlow: Flow<Double> = recordDao.getTotalExpenseFlow()
    val totalIncomeFlow: Flow<Double> = recordDao.getTotalIncomeFlow()

    fun getRecordsPaged(query: String = ""): Flow<PagingData<Record>> {
        return Pager(
            config = PagingConfig(
                pageSize = 20,
                initialLoadSize = 20,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                if (query.isEmpty()) recordDao.getPagingSource()
                else recordDao.getSearchPagingSource(query)
            }
        ).flow
    }

    suspend fun getRecordById(id: Long): Record? = recordDao.getRecordById(id)

    suspend fun deleteRecordsByIds(ids: Set<Long>) {
        val now = System.currentTimeMillis()
        ids.forEach { recordDao.softDeleteRecord(it, now) }
        changeNotifier.notifyChange()
    }

    suspend fun quickRecord(input: String, categoryName: String? = null, isIncome: Boolean = false, addressResult: AddressResult? = null, billDate: Long = System.currentTimeMillis()): Boolean {
        val parseResult = SmartParser.parse(input) ?: return false
        return saveRecordWithFallback(parseResult.amount, parseResult.note, categoryName, isIncome, addressResult, billDate)
    }

    suspend fun saveRecordWithFallback(amount: Double, note: String, categoryNameInput: String?, isIncome: Boolean, addressResult: AddressResult? = null, billDate: Long = System.currentTimeMillis()): Boolean {
        val cat = categoryNameInput?.let { categoryDao.getCategoryByName(it) } ?: return false
        recordDao.insertRecord(Record(
            amount = amount,
            note = note,
            categoryId = cat.id,
            categoryName = cat.name,
            date = billDate,
            address = addressResult?.address ?: "",
            latitude = addressResult?.latitude,
            longitude = addressResult?.longitude
        ))
        changeNotifier.notifyChange()
        return true
    }

    suspend fun insertRecord(record: Record): Long {
        val id = recordDao.insertRecord(record)
        changeNotifier.notifyChange()
        return id
    }

    suspend fun softDeleteRecord(record: Record) {
        recordDao.softDeleteRecord(record.id)
        changeNotifier.notifyChange()
    }

    suspend fun addCategory(category: Category) {
        categoryDao.insertCategories(listOf(category))
        changeNotifier.notifyChange()
    }

    suspend fun softDeleteCategory(category: Category) {
        categoryDao.softDeleteCategory(category.id)
        changeNotifier.notifyChange()
    }

    suspend fun updateCategory(category: Category) {
        categoryDao.updateCategory(category)
        changeNotifier.notifyChange()
    }

    val budgetFlow: Flow<Budget?> = budgetDao.getBudgetFlow()

    fun getMonthlyExpenseFlow(start: Long, end: Long): Flow<Double> =
        recordDao.getMonthlyExpenseFlow(start, end)

    fun getMonthlyIncomeFlow(start: Long, end: Long): Flow<Double> =
        recordDao.getMonthlyIncomeFlow(start, end)

    suspend fun updateRecordBillDate(id: Long, billDate: Long) {
        recordDao.updateBillDate(id, billDate)
        changeNotifier.notifyChange()
    }

    suspend fun updateRecordNote(id: Long, note: String) {
        recordDao.updateRecordNote(id, note)
        changeNotifier.notifyChange()
    }

    suspend fun updateRecordCategory(id: Long, categoryId: Long, categoryName: String) {
        recordDao.updateRecordCategory(id, categoryId, categoryName)
        changeNotifier.notifyChange()
    }

    suspend fun updateRecordAmount(id: Long, amount: Double) {
        recordDao.updateRecordAmount(id, amount)
        changeNotifier.notifyChange()
    }

    suspend fun saveBudget(amount: Double) {
        budgetDao.upsertBudget(Budget(monthlyAmount = amount))
        changeNotifier.notifyChange()
    }

    suspend fun clearBudget() {
        budgetDao.softDeleteBudget()
        changeNotifier.notifyChange()
    }

    suspend fun getAllCategoriesList(): List<Category> = categoryDao.getAllCategoriesList()

    /**
     * 批量导入 Record，每条使用独立 id（自增），最后触发一次同步通知
     * @param onProgress 每 50 条回调一次进度，参数为当前已插入条数
     */
    suspend fun insertRecords(records: List<Record>, onProgress: (Int) -> Unit = {}): Int {
        var count = 0
        val total = records.size
        for (record in records) {
            recordDao.insertRecord(record)
            count++
            if (count % 50 == 0 || count == total) {
                onProgress(count)
            }
        }
        if (count > 0) changeNotifier.notifyChange()
        return count
    }

    suspend fun seedDefaultCategories() {
        if (categoryDao.getCategoryCount() > 0) return
        categoryDao.insertCategories(DefaultCategories.getAll())
        seedBrandMappings()
    }

    /**
     * 种子品牌映射数据（仅当 brand_mappings 表为空时执行）
     * 根据 BrandSeedData 中的分类名查找 ID 后写入
     */
    suspend fun seedBrandMappings() {
        if (brandMappingDao.getCount() > 0) return
        val categories = categoryDao.getAllCategoriesList()
        val mappings = BrandSeedData.getAll().mapNotNull { (brandName, categoryName) ->
            val cat = categories.firstOrNull { it.name == categoryName }
            if (cat != null) {
                BrandMapping(
                    brandName = brandName,
                    categoryId = cat.id,
                    source = "default"
                )
            } else null
        }
        if (mappings.isNotEmpty()) {
            brandMappingDao.insertAll(mappings)
        }
    }

    /**
     * 用户自学习：记录未知品牌→分类的映射
     * @param brandName 品牌名/备注文本
     * @param categoryId 用户选择的分组 ID
     */
    suspend fun learnBrandMapping(brandName: String, categoryId: Long) {
        val existing = brandMappingDao.getByBrandName(brandName)
        if (existing != null) {
            brandMappingDao.upsert(existing.copy(
                hitCount = existing.hitCount + 1,
                updatedAt = System.currentTimeMillis()
            ))
        } else {
            brandMappingDao.upsert(BrandMapping(
                brandName = brandName,
                categoryId = categoryId,
                source = "user"
            ))
        }
    }

    suspend fun resetToDefaultCategories() {
        categoryDao.deleteAll()
        categoryDao.insertCategories(DefaultCategories.getAll())
    }

    suspend fun getAllRecordsForSync(): List<Record> = recordDao.getAllRecordsForSync()
    suspend fun getAllCategoriesForSync(): List<Category> = categoryDao.getAllCategoriesForSync()
    suspend fun getBudgetForSync(): Budget? = budgetDao.getBudgetForSync()
    suspend fun getDeletedRecords(): List<Record> = recordDao.getDeletedRecords()

    suspend fun insertRecordForSync(record: Record) {
        val effectiveSyncUuid = record.syncUuid.ifBlank { java.util.UUID.randomUUID().toString() }
        val existing = recordDao.getRecordBySyncUuid(effectiveSyncUuid)
        if (existing != null) {
            // 本地已有相同 syncUuid 的记录，用远程数据覆盖但保留本地 id
            recordDao.insertRecord(record.copy(id = existing.id, syncUuid = effectiveSyncUuid))
        } else {
            // 新记录，让 Room 自增 id
            recordDao.insertRecord(record.copy(id = 0, syncUuid = effectiveSyncUuid))
        }
    }

    suspend fun upsertCategoryForSync(category: Category) {
        val effectiveSyncUuid = category.syncUuid.ifBlank { java.util.UUID.randomUUID().toString() }
        val existing = categoryDao.getCategoryBySyncUuid(effectiveSyncUuid)
        if (existing != null) {
            categoryDao.upsertCategory(category.copy(id = existing.id, syncUuid = effectiveSyncUuid))
        } else {
            categoryDao.upsertCategory(category.copy(id = 0, syncUuid = effectiveSyncUuid))
        }
    }

    suspend fun upsertBudgetForSync(budget: Budget) {
        val effectiveSyncUuid = budget.syncUuid.ifBlank { java.util.UUID.randomUUID().toString() }
        val existing = budgetDao.getBudgetBySyncUuid(effectiveSyncUuid)
        if (existing != null) {
            budgetDao.upsertBudget(budget.copy(id = existing.id, syncUuid = effectiveSyncUuid))
        } else {
            budgetDao.upsertBudget(budget.copy(id = 1, syncUuid = effectiveSyncUuid))
        }
    }

    suspend fun purgeDeletedRecords() {
        recordDao.purgeDeletedRecords()
        categoryDao.purgeDeletedCategories()
        budgetDao.purgeDeletedBudgets()
    }
}
