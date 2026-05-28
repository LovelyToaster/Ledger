package com.verdantgem.ledger.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import com.verdantgem.ledger.data.model.BrandMapping
import com.verdantgem.ledger.data.model.Budget
import com.verdantgem.ledger.data.model.Category
import com.verdantgem.ledger.data.model.Record
import com.verdantgem.ledger.data.remote.LocationDelegate
import com.verdantgem.ledger.data.remote.LocationProvider
import com.verdantgem.ledger.data.remote.SyncManager
import com.verdantgem.ledger.data.repository.LedgerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

private fun currentMonthStart(): Long {
    return Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun currentMonthEnd(): Long {
    return Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis
}

data class AdvancedSearchParams(
    val query: String = "",
    val startTime: Long? = null,
    val endTime: Long? = null,
    val categoryName: String? = null
)

data class DailyAggregateItem(val totalIncome: Double, val totalExpense: Double)

sealed class DashboardItem {
    data class Header(val label: String) : DashboardItem()
    data class Record(val record: com.verdantgem.ledger.data.model.Record) : DashboardItem()
}

private fun getDateGroupLabel(dateMillis: Long): String {
    val today = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val recordCal = Calendar.getInstance().apply { timeInMillis = dateMillis }

    if (recordCal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
        recordCal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)) {
        return "今天"
    }

    val yesterday = Calendar.getInstance().apply {
        timeInMillis = today.timeInMillis
        add(Calendar.DAY_OF_YEAR, -1)
    }
    if (recordCal.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
        recordCal.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR)) {
        return "昨天"
    }

    return if (recordCal.get(Calendar.YEAR) == today.get(Calendar.YEAR)) {
        SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date(dateMillis))
    } else {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(dateMillis))
    }
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: LedgerRepository,
    private val syncManager: SyncManager,
    locationProvider: LocationProvider
) : ViewModel() {

    private val locationDelegate = LocationDelegate(locationProvider)

    val isSyncing: StateFlow<Boolean> = syncManager.isSyncing

    fun triggerSync() {
        viewModelScope.launch {
            syncManager.syncIfConfigured(ignoreAutoSync = true)
        }
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _searchStartTime = MutableStateFlow<Long?>(null)
    val searchStartTime: StateFlow<Long?> = _searchStartTime

    private val _searchEndTime = MutableStateFlow<Long?>(null)
    val searchEndTime: StateFlow<Long?> = _searchEndTime

    private val _selectedSearchCategory = MutableStateFlow<String?>(null)
    val selectedSearchCategory: StateFlow<String?> = _selectedSearchCategory

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode

    fun startLocation() = locationDelegate.startLocation(viewModelScope)

    fun stopLocation() = locationDelegate.stopLocation()

    private val _refreshBalance = MutableStateFlow(0)

    private val _refreshTrigger = MutableStateFlow(0)

    fun onResume() {
        _refreshTrigger.value++
    }

    init {
        viewModelScope.launch {
            repository.allCategories.collectLatest {
                _refreshBalance.value++
            }
        }
    }

    val allCategories: StateFlow<List<Category>> = repository.allCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allBrandMappings: StateFlow<List<BrandMapping>> = repository.allBrandMappings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dailyAggregates: StateFlow<Map<String, DailyAggregateItem>> = combine(
        repository.allRecords, repository.allCategories
    ) { records, categories ->
        val incomeNames = categories.filter { it.isIncome }.map { it.name }.toSet()
        val sixMonthsAgo = Calendar.getInstance().apply { add(Calendar.MONTH, -6) }.timeInMillis
        records.filter { it.date >= sixMonthsAgo }
            .groupBy { getDateGroupLabel(it.date) }
            .mapValues { (_, recs) ->
                val income = recs.filter { it.categoryName in incomeNames }.sumOf { it.amount }
                val expense = recs.filter { it.categoryName !in incomeNames }.sumOf { it.amount }
                DailyAggregateItem(income, expense)
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val pagedItems: Flow<PagingData<DashboardItem>> = combine(
        _searchQuery, _searchStartTime, _searchEndTime, _selectedSearchCategory, _refreshTrigger
    ) { query, start, end, cat, _ -> AdvancedSearchParams(query, start, end, cat) }
        .flatMapLatest { params -> repository.getRecordsPaged(params.query, params.startTime, params.endTime, params.categoryName) }
        .map { pagingData ->
            pagingData
                .map<Record, DashboardItem> { DashboardItem.Record(it) }
                .insertSeparators { before, after ->
                    val beforeLabel = (before as? DashboardItem.Record)?.record?.date?.let { getDateGroupLabel(it) }
                    val afterLabel = (after as? DashboardItem.Record)?.record?.date?.let { getDateGroupLabel(it) }
                    when {
                        before == null && after != null -> DashboardItem.Header(afterLabel!!)
                        before != null && after != null && beforeLabel != afterLabel -> DashboardItem.Header(afterLabel!!)
                        else -> null
                    }
                }
        }
        .cachedIn(viewModelScope)

    val budget: StateFlow<Budget?> = repository.budgetFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val monthlyExpense: StateFlow<Double> = combine(_refreshBalance, _refreshTrigger) { bal, _ -> bal }
        .flatMapLatest { repository.getMonthlyExpenseFlow(currentMonthStart(), currentMonthEnd()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val monthlyIncome: StateFlow<Double> = combine(_refreshBalance, _refreshTrigger) { bal, _ -> bal }
        .flatMapLatest { repository.getMonthlyIncomeFlow(currentMonthStart(), currentMonthEnd()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val monthlySurplus: StateFlow<Double> = combine(monthlyIncome, monthlyExpense) { inc, exp -> inc - exp }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSearchDateRange(start: Long?, end: Long?) {
        _searchStartTime.value = start
        _searchEndTime.value = end
    }

    fun clearSearchDateRange() {
        _searchStartTime.value = null
        _searchEndTime.value = null
    }

    fun setSearchCategory(categoryName: String?) {
        _selectedSearchCategory.value = categoryName
    }

    fun clearAdvancedSearch() {
        _selectedSearchCategory.value = null
        _searchStartTime.value = null
        _searchEndTime.value = null
    }

    fun toggleSelection(id: Long) {
        _selectedIds.value = if (_selectedIds.value.contains(id)) {
            _selectedIds.value - id
        } else {
            _selectedIds.value + id
        }
    }

    fun enterSelectionMode(firstId: Long) {
        _isSelectionMode.value = true
        _selectedIds.value = setOf(firstId)
    }

    fun exitSelectionMode() {
        _isSelectionMode.value = false
        _selectedIds.value = emptySet()
    }

    fun deleteSelected() {
        viewModelScope.launch {
            repository.deleteRecordsByIds(_selectedIds.value)
            exitSelectionMode()
        }
    }

    fun quickRecord(text: String, categoryName: String? = null, isIncome: Boolean = false, billDate: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            locationDelegate.joinLocation()
            repository.quickRecord(text, categoryName, isIncome, locationDelegate.address, billDate)
        }
    }

    fun learnBrandMapping(brandName: String, categoryName: String) {
        viewModelScope.launch {
            val cat = repository.getAllCategoriesList().firstOrNull { it.name == categoryName }
            if (cat != null) {
                repository.learnBrandMapping(brandName, cat.id)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        locationDelegate.stopLocation()
    }
}
