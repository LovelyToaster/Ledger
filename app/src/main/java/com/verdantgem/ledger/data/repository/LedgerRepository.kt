package com.verdantgem.ledger.data.repository

import android.content.Context
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.verdantgem.ledger.data.local.BrandMappingDao
import com.verdantgem.ledger.data.local.BudgetDao
import com.verdantgem.ledger.data.local.CategoryDao
import com.verdantgem.ledger.data.local.RecordDao
import com.verdantgem.ledger.data.local.SyncChangeLog
import com.verdantgem.ledger.data.local.SyncChangeLogDao
import com.verdantgem.ledger.data.model.BrandMapping
import com.verdantgem.ledger.data.model.Budget
import com.verdantgem.ledger.data.model.Category
import com.verdantgem.ledger.data.model.Record
import com.verdantgem.ledger.data.DataChangeNotifier
import com.verdantgem.ledger.data.remote.AddressResult
import com.verdantgem.ledger.data.remote.SyncRecord
import com.verdantgem.ledger.data.remote.toSync
import com.verdantgem.ledger.domain.parser.SmartParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LedgerRepository @Inject constructor(
    private val recordDao: RecordDao,
    private val categoryDao: CategoryDao,
    private val budgetDao: BudgetDao,
    private val brandMappingDao: BrandMappingDao,
    private val changeNotifier: DataChangeNotifier,
    private val syncChangeLogDao: SyncChangeLogDao,
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("ledger_settings", Context.MODE_PRIVATE)
    private val deviceId: String by lazy {
        var id = prefs.getString("device_id", null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            prefs.edit().putString("device_id", id).apply()
        }
        id
    }
    val allRecords: Flow<List<Record>> = recordDao.getAllRecords()
    val allCategories: Flow<List<Category>> = categoryDao.getAllCategories()
    val allBrandMappings: Flow<List<BrandMapping>> = brandMappingDao.getAllMappings()

    val totalExpenseFlow: Flow<Double> = recordDao.getTotalExpenseFlow()
    val totalIncomeFlow: Flow<Double> = recordDao.getTotalIncomeFlow()

    private val syncJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun buildLogEntry(entity: String, uuid: String, operation: String, data: String, changedAt: Long): SyncChangeLog {
        return SyncChangeLog(
            seq = 0,
            entity = entity,
            uuid = uuid,
            operation = operation,
            data = data,
            changedAt = changedAt,
            deviceId = deviceId
        )
    }

    private suspend fun logRecordChange(record: com.verdantgem.ledger.data.model.Record, operation: String) {
        try {
            val syncRecord = record.toSync()
            val jsonStr = syncJson.encodeToString(
                com.verdantgem.ledger.data.remote.SyncRecord.serializer(),
                syncRecord
            )
            syncChangeLogDao.appendNext(
                buildLogEntry("record", record.syncUuid, operation, jsonStr, record.updatedAt)
            )
        } catch (e: Exception) {
            // 日志写入失败不阻断业务操作
        }
    }

    private suspend fun logCategoryChange(category: com.verdantgem.ledger.data.model.Category, operation: String) {
        try {
            val syncCat = category.toSync()
            val jsonStr = syncJson.encodeToString(
                com.verdantgem.ledger.data.remote.SyncCategory.serializer(),
                syncCat
            )
            syncChangeLogDao.appendNext(
                buildLogEntry("category", category.syncUuid, operation, jsonStr, category.updatedAt)
            )
        } catch (e: Exception) {
            // 日志写入失败不阻断业务操作
        }
    }

    private suspend fun logBudgetChange(budget: com.verdantgem.ledger.data.model.Budget, operation: String) {
        try {
            val syncBudget = budget.toSync()
            val jsonStr = syncJson.encodeToString(
                com.verdantgem.ledger.data.remote.SyncBudget.serializer(),
                syncBudget
            )
            syncChangeLogDao.appendNext(
                buildLogEntry("budget", budget.syncUuid, operation, jsonStr, budget.updatedAt)
            )
        } catch (e: Exception) {
            // 日志写入失败不阻断业务操作
        }
    }

    fun getRecordsPaged(
        query: String = "",
        searchStartTime: Long? = null,
        searchEndTime: Long? = null,
        searchCategoryName: String? = null
    ): Flow<PagingData<Record>> {
        return Pager(
            config = PagingConfig(
                pageSize = 20,
                initialLoadSize = 20,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                when {
                    query.isNotEmpty() && searchCategoryName != null && searchStartTime != null && searchEndTime != null ->
                        recordDao.getSearchWithCategoryAndDateRangePagingSource(query, searchCategoryName, searchStartTime, searchEndTime)
                    query.isNotEmpty() && searchStartTime != null && searchEndTime != null ->
                        recordDao.getSearchWithDateRangePagingSource(query, searchStartTime, searchEndTime)
                    searchCategoryName != null && searchStartTime != null && searchEndTime != null ->
                        recordDao.getRecordsByCategoryPagingSource(searchCategoryName, searchStartTime, searchEndTime)
                    query.isNotEmpty() && searchCategoryName != null ->
                        recordDao.getSearchWithCategoryOnlyPagingSource(query, searchCategoryName)
                    query.isNotEmpty() ->
                        recordDao.getSearchPagingSource(query)
                    searchCategoryName != null ->
                        recordDao.getRecordsByCategoryOnlyPagingSource(searchCategoryName)
                    searchStartTime != null && searchEndTime != null ->
                        recordDao.getRecordsInDateRange(searchStartTime, searchEndTime)
                    else ->
                        recordDao.getPagingSource()
                }
            }
        ).flow
    }

    fun getRecordsByCategoryPaged(categoryName: String, isParent: Boolean, isIncome: Boolean, startTime: Long, endTime: Long): Flow<PagingData<Record>> {
        return Pager(
            config = PagingConfig(pageSize = 20, initialLoadSize = 20, enablePlaceholders = false),
            pagingSourceFactory = {
                if (isParent) recordDao.getRecordsByParentCategoryPagingSource(categoryName, isIncome, startTime, endTime)
                else recordDao.getRecordsByCategoryPagingSource(categoryName, startTime, endTime)
            }
        ).flow
    }

    suspend fun getRecordById(id: Long): Record? = recordDao.getRecordById(id)

    suspend fun deleteRecordsByIds(ids: Set<Long>) {
        val now = System.currentTimeMillis()
        ids.forEach { id ->
            val entity = recordDao.getRecordById(id)
            if (entity != null) {
                logRecordChange(entity.copy(deleted = true, updatedAt = now), "delete")
            }
            recordDao.softDeleteRecord(id, now)
        }
        changeNotifier.notifyChange()
    }

    suspend fun quickRecord(input: String, categoryName: String? = null, isIncome: Boolean = false, addressResult: AddressResult? = null, billDate: Long = System.currentTimeMillis()): Boolean {
        val parseResult = SmartParser.parse(input) ?: return false
        return saveRecordWithFallback(parseResult.amount, parseResult.note, categoryName, isIncome, addressResult, billDate)
    }

    suspend fun saveRecordWithFallback(amount: Double, note: String, categoryNameInput: String?, isIncome: Boolean, addressResult: AddressResult? = null, billDate: Long = System.currentTimeMillis()): Boolean {
        val cat = categoryNameInput?.let { categoryDao.getCategoryByName(it) } ?: return false
        val id = recordDao.insertRecord(Record(
            amount = amount,
            note = note,
            categoryId = cat.id,
            categoryName = cat.name,
            date = billDate,
            address = addressResult?.address ?: "",
            latitude = addressResult?.latitude,
            longitude = addressResult?.longitude
        ))
        val fullRecord = recordDao.getRecordById(id)
        if (fullRecord != null) {
            logRecordChange(fullRecord, "upsert")
        }
        changeNotifier.notifyChange()
        return true
    }

    suspend fun insertRecord(record: Record): Long {
        val id = recordDao.insertRecord(record)
        val fullRecord = recordDao.getRecordById(id)
        if (fullRecord != null) {
            logRecordChange(fullRecord, "upsert")
        }
        changeNotifier.notifyChange()
        return id
    }

    suspend fun softDeleteRecord(record: Record) {
        val entity = recordDao.getRecordById(record.id)
        if (entity != null) {
            logRecordChange(entity.copy(deleted = true, updatedAt = System.currentTimeMillis()), "delete")
        }
        recordDao.softDeleteRecord(record.id)
        changeNotifier.notifyChange()
    }

    suspend fun addCategory(category: Category) {
        val existed = categoryDao.getCategoryByName(category.name)
        categoryDao.insertCategories(listOf(category))
        val inserted = categoryDao.getCategoryByName(category.name)
        // 仅当分类此前不存在（即本次为真正新增）时才记录 upsert 日志
        // 若已存在，insertCategories 的 IGNORE 策略会静默跳过，不应产生虚假变更
        if (inserted != null && existed == null) {
            logCategoryChange(inserted, "upsert")
        }
        changeNotifier.notifyChange()
    }

    suspend fun softDeleteCategory(category: Category) {
        val entity = categoryDao.getCategoryById(category.id)
        if (entity != null) {
            logCategoryChange(entity.copy(deleted = true, updatedAt = System.currentTimeMillis()), "delete")
        }
        categoryDao.softDeleteCategory(category.id)
        changeNotifier.notifyChange()
    }

    suspend fun updateCategory(category: Category) {
        categoryDao.updateCategory(category)
        val fullCat = categoryDao.getCategoryById(category.id)
        if (fullCat != null) {
            logCategoryChange(fullCat, "upsert")
        }
        changeNotifier.notifyChange()
    }

    val budgetFlow: Flow<Budget?> = budgetDao.getBudgetFlow()

    fun getMonthlyExpenseFlow(start: Long, end: Long): Flow<Double> =
        recordDao.getMonthlyExpenseFlow(start, end)

    fun getMonthlyIncomeFlow(start: Long, end: Long): Flow<Double> =
        recordDao.getMonthlyIncomeFlow(start, end)

    suspend fun updateRecordBillDate(id: Long, billDate: Long) {
        recordDao.updateBillDate(id, billDate)
        val fullRecord = recordDao.getRecordById(id)
        if (fullRecord != null) {
            logRecordChange(fullRecord, "upsert")
        }
        changeNotifier.notifyChange()
    }

    suspend fun updateRecordNote(id: Long, note: String) {
        recordDao.updateRecordNote(id, note)
        val fullRecord = recordDao.getRecordById(id)
        if (fullRecord != null) {
            logRecordChange(fullRecord, "upsert")
        }
        changeNotifier.notifyChange()
    }

    suspend fun updateRecordCategory(id: Long, categoryId: Long, categoryName: String) {
        recordDao.updateRecordCategory(id, categoryId, categoryName)
        val fullRecord = recordDao.getRecordById(id)
        if (fullRecord != null) {
            logRecordChange(fullRecord, "upsert")
        }
        changeNotifier.notifyChange()
    }

    suspend fun updateRecordAmount(id: Long, amount: Double) {
        recordDao.updateRecordAmount(id, amount)
        val fullRecord = recordDao.getRecordById(id)
        if (fullRecord != null) {
            logRecordChange(fullRecord, "upsert")
        }
        changeNotifier.notifyChange()
    }

    suspend fun saveBudget(amount: Double) {
        budgetDao.upsertBudget(Budget(monthlyAmount = amount))
        val budget = budgetDao.getBudgetForSync()
        if (budget != null) {
            logBudgetChange(budget, "upsert")
        }
        changeNotifier.notifyChange()
    }

    suspend fun clearBudget() {
        val budget = budgetDao.getBudgetForSync()
        if (budget != null) {
            logBudgetChange(budget.copy(deleted = true, updatedAt = System.currentTimeMillis()), "delete")
        }
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
        val logEntries = mutableListOf<SyncChangeLog>()
        for (record in records) {
            val id = recordDao.insertRecord(record)
            val fullRecord = record.copy(id = id)
            val jsonStr = syncJson.encodeToString(SyncRecord.serializer(), fullRecord.toSync())
            logEntries.add(buildLogEntry("record", fullRecord.syncUuid, "upsert", jsonStr, fullRecord.updatedAt))
            count++
            if (count % 50 == 0 || count == total) {
                onProgress(count)
            }
        }
        if (logEntries.isNotEmpty()) {
            try { syncChangeLogDao.appendAll(logEntries) } catch (_: Exception) {}
        }
        if (count > 0) changeNotifier.notifyChange()
        return count
    }

    suspend fun seedDefaultCategories() {
        if (categoryDao.getCategoryCount() > 0) {
            seedBrandMappings()
            return
        }
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
                    source = "default",
                    confirmCount = 3
                )
            } else null
        }
        if (mappings.isNotEmpty()) {
            brandMappingDao.insertAll(mappings)
        }
    }

    /**
     * 用户选择记录：处理品牌映射学习 + miss 衰减
     *
     * - 无已有映射 → 创建新映射，confirmCount=1（需多次确认才生效）
     * - 已有映射且类别相同 → confirmCount++（上限 3），missCount 归零
     * - 已有映射但用户选了不同类别 → missCount++；missCount >= 3 则自动删除
     */
    suspend fun recordUserChoice(brandName: String, chosenCategoryId: Long) {
        val existing = brandMappingDao.getByBrandName(brandName)
        if (existing != null) {
            if (existing.categoryId == chosenCategoryId) {
                brandMappingDao.upsert(existing.copy(
                    confirmCount = minOf(existing.confirmCount + 1, 3),
                    missCount = 0,
                    hitCount = existing.hitCount + 1,
                    updatedAt = System.currentTimeMillis()
                ))
            } else {
                val newMissCount = existing.missCount + 1
                if (newMissCount >= 3) {
                    brandMappingDao.deleteById(existing.id)
                } else {
                    brandMappingDao.upsert(existing.copy(
                        missCount = newMissCount,
                        updatedAt = System.currentTimeMillis()
                    ))
                }
            }
        } else {
            brandMappingDao.upsert(BrandMapping(
                brandName = brandName,
                categoryId = chosenCategoryId,
                source = "user",
                confirmCount = 1
            ))
        }
    }

    /**
     * 获取指定分类下的所有品牌映射
     */
    suspend fun getBrandMappingsByCategory(categoryId: Long): List<BrandMapping> =
        brandMappingDao.getByCategoryId(categoryId)

    /**
     * 删除指定品牌映射
     */
    suspend fun deleteBrandMapping(id: Long) {
        brandMappingDao.deleteById(id)
        changeNotifier.notifyChange()
    }

    /**
     * 手动添加品牌映射（管理界面使用），确认次数满 3 立即生效
     */
    suspend fun addBrandMapping(brandName: String, categoryId: Long) {
        val existing = brandMappingDao.getByBrandName(brandName)
        if (existing == null) {
            brandMappingDao.upsert(BrandMapping(
                brandName = brandName,
                categoryId = categoryId,
                source = "user",
                confirmCount = 3
            ))
            changeNotifier.notifyChange()
        }
    }

    suspend fun resetToDefaultCategories() {
        categoryDao.deleteAll()
        categoryDao.insertCategories(DefaultCategories.getAll())
        brandMappingDao.deleteAll()
        seedBrandMappings()
        changeNotifier.notifyChange()
    }

    suspend fun getAllRecordsForSync(): List<Record> = recordDao.getAllRecordsForSync()
    suspend fun getAllCategoriesForSync(): List<Category> = categoryDao.getAllCategoriesForSync()
    suspend fun getBudgetForSync(): Budget? = budgetDao.getBudgetForSync()
    suspend fun getDeletedRecords(): List<Record> = recordDao.getDeletedRecords()

    suspend fun insertRecordForSync(record: Record) {
        val effectiveSyncUuid = record.syncUuid.ifBlank { java.util.UUID.randomUUID().toString() }
        var existing = recordDao.getRecordBySyncUuid(effectiveSyncUuid)
        // 回退：如果 UUID 找不到但 id 有效，按 id 查找（仅限本地 syncUuid 为空或与目标一致的旧格式记录）
        if (existing == null && record.id > 0) {
            val byId = recordDao.getRecordById(record.id)
            if (byId != null && (byId.syncUuid.isBlank() || byId.syncUuid == effectiveSyncUuid)) {
                existing = byId
            }
        }
        if (existing != null) {
            // 本地已有记录，用远程数据覆盖但保留本地 id，收敛 UUID 为远程值
            recordDao.insertRecord(record.copy(id = existing.id, syncUuid = effectiveSyncUuid))
        } else {
            // 新记录，让 Room 自增 id
            recordDao.insertRecord(record.copy(id = 0, syncUuid = effectiveSyncUuid))
        }
    }

    suspend fun upsertCategoryForSync(category: Category) {
        val effectiveSyncUuid = category.syncUuid.ifBlank { java.util.UUID.randomUUID().toString() }
        var existing = categoryDao.getCategoryBySyncUuid(effectiveSyncUuid)
        // 回退：如果 UUID 找不到但 id 有效，按 id 查找（仅限本地 syncUuid 为空或与目标一致的旧格式记录）
        if (existing == null && category.id > 0) {
            val byId = categoryDao.getCategoryById(category.id)
            if (byId != null && (byId.syncUuid.isBlank() || byId.syncUuid == effectiveSyncUuid)) {
                existing = byId
            }
        }
        // 第三回退：按 (name, parentName, isIncome) 语义匹配（处理重置类别后 ID 漂移）
        if (existing == null) {
            existing = categoryDao.getCategoryByNameAndParent(category.name, category.parentName, category.isIncome)
        }
        if (existing != null) {
            // 更新已有记录，收敛 UUID 为远程值
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
