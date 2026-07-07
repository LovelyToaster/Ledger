package com.verdantgem.ledger.ui.screens.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verdantgem.ledger.data.model.Category
import com.verdantgem.ledger.data.repository.LedgerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

enum class StatsMode { RECENT, MONTH, YEAR }

data class CategoryRank(
    val name: String,
    val amount: Float,
    val percentage: Float,
    val icon: String = ""
)

data class CategoryComparisonInfo(
    val changeAmount: Float,
    val changePercent: Float,
    val previousAmount: Float
)

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val repository: LedgerRepository
) : ViewModel() {

    private val _mode = MutableStateFlow(StatsMode.RECENT)
    val mode: StateFlow<StatsMode> = _mode

    private val _selectedDate = MutableStateFlow(Calendar.getInstance())
    val selectedDate: StateFlow<Calendar> = _selectedDate

    private val _isCategoryIncome = MutableStateFlow(false)
    val isCategoryIncome: StateFlow<Boolean> = _isCategoryIncome

    val allCategories: StateFlow<List<Category>> = repository.allCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val dayFormat = SimpleDateFormat("dd", Locale.getDefault())
    private val monthFormat = SimpleDateFormat("MM", Locale.getDefault())

    val chartData: StateFlow<List<Float>> = combine(repository.allRecords, _mode, _selectedDate, _isCategoryIncome, repository.allCategories) { records, currentMode, date, showIncome, categories ->
        if (records.isEmpty()) return@combine emptyList()

        val incomeNames = categories.filter { it.isIncome }.map { it.name }.toSet()
        val calendar = Calendar.getInstance()
        val filteredRecords = when (currentMode) {
            StatsMode.RECENT -> {
                val cal = Calendar.getInstance().apply { 
                    firstDayOfWeek = Calendar.MONDAY
                    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                records.filter { it.date >= cal.timeInMillis }
            }
            StatsMode.MONTH -> {
                records.filter { 
                    calendar.timeInMillis = it.date
                    calendar.get(Calendar.YEAR) == date.get(Calendar.YEAR) && calendar.get(Calendar.MONTH) == date.get(Calendar.MONTH)
                }
            }
            StatsMode.YEAR -> {
                records.filter { 
                    calendar.timeInMillis = it.date
                    calendar.get(Calendar.YEAR) == date.get(Calendar.YEAR)
                }
            }
        }

        val typeFiltered = filteredRecords.filter {
            if (showIncome) it.categoryName in incomeNames
            else it.categoryName !in incomeNames
        }

        val sdf = if (currentMode == StatsMode.YEAR) monthFormat else dayFormat

        val grouped = typeFiltered.groupBy { sdf.format(Date(it.date)) }
            .mapValues { entry -> entry.value.sumOf { it.amount }.toFloat() }
            .toSortedMap()

        val result = mutableListOf<Float>()
        when (currentMode) {
            StatsMode.RECENT -> {
                val cal = Calendar.getInstance().apply { 
                    firstDayOfWeek = Calendar.MONDAY
                    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                }
                for (i in 0..6) {
                    result.add(grouped[sdf.format(cal.time)] ?: 0f)
                    cal.add(Calendar.DAY_OF_WEEK, 1)
                }
            }
            StatsMode.MONTH -> {
                val maxDay = date.getActualMaximum(Calendar.DAY_OF_MONTH)
                for (i in 1..maxDay) {
                    val dayStr = String.format("%02d", i)
                    result.add(grouped[dayStr] ?: 0f)
                }
            }
            StatsMode.YEAR -> {
                for (i in 1..12) {
                    val monthStr = String.format("%02d", i)
                    result.add(grouped[monthStr] ?: 0f)
                }
            }
        }
        result
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val chartLabels: StateFlow<List<String>> = combine(_mode, _selectedDate) { currentMode, date ->
        val result = mutableListOf<String>()
        when (currentMode) {
            StatsMode.RECENT -> {
                result.addAll(listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日"))
            }
            StatsMode.MONTH -> {
                val maxDay = date.getActualMaximum(Calendar.DAY_OF_MONTH)
                for (i in 1..maxDay) {
                    if (i == 1 || i % 5 == 0 || i == maxDay) result.add("${i}") else result.add("")
                }
            }
            StatsMode.YEAR -> {
                for (i in 1..12) {
                    result.add("${i}月")
                }
            }
        }
        result
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalIncome: StateFlow<Double> = combine(
        repository.allRecords, _mode, _selectedDate, repository.allCategories
    ) { records, currentMode, date, categories ->
        if (records.isEmpty()) return@combine 0.0

        val incomeNames = categories.filter { it.isIncome }.map { it.name }.toSet()
        val calendar = Calendar.getInstance()
        val filtered = when (currentMode) {
            StatsMode.RECENT -> {
                val cal = Calendar.getInstance().apply {
                    firstDayOfWeek = Calendar.MONDAY
                    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
                records.filter { it.date >= cal.timeInMillis }
            }
            StatsMode.MONTH -> records.filter {
                calendar.timeInMillis = it.date
                calendar.get(Calendar.YEAR) == date.get(Calendar.YEAR) && calendar.get(Calendar.MONTH) == date.get(Calendar.MONTH)
            }
            StatsMode.YEAR -> records.filter {
                calendar.timeInMillis = it.date
                calendar.get(Calendar.YEAR) == date.get(Calendar.YEAR)
            }
        }
        filtered.filter { it.categoryName in incomeNames }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalExpense: StateFlow<Double> = combine(
        repository.allRecords, _mode, _selectedDate, repository.allCategories
    ) { records, currentMode, date, categories ->
        if (records.isEmpty()) return@combine 0.0

        val incomeNames = categories.filter { it.isIncome }.map { it.name }.toSet()
        val calendar = Calendar.getInstance()
        val filtered = when (currentMode) {
            StatsMode.RECENT -> {
                val cal = Calendar.getInstance().apply {
                    firstDayOfWeek = Calendar.MONDAY
                    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
                records.filter { it.date >= cal.timeInMillis }
            }
            StatsMode.MONTH -> records.filter {
                calendar.timeInMillis = it.date
                calendar.get(Calendar.YEAR) == date.get(Calendar.YEAR) && calendar.get(Calendar.MONTH) == date.get(Calendar.MONTH)
            }
            StatsMode.YEAR -> records.filter {
                calendar.timeInMillis = it.date
                calendar.get(Calendar.YEAR) == date.get(Calendar.YEAR)
            }
        }
        filtered.filter { it.categoryName !in incomeNames }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalSurplus: StateFlow<Double> = combine(totalIncome, totalExpense) { inc, exp -> inc - exp }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val categoryDistribution: StateFlow<Map<String, Float>> = combine(
        repository.allRecords, _mode, _selectedDate, _isCategoryIncome, repository.allCategories
    ) { records, currentMode, date, showIncome, categories ->
        if (records.isEmpty()) return@combine emptyMap<String, Float>()

        val incomeNames = categories.filter { it.isIncome }.map { it.name }.toSet()
        val calendar = Calendar.getInstance()
        val filtered = when (currentMode) {
            StatsMode.RECENT -> {
                val cal = Calendar.getInstance().apply { 
                    firstDayOfWeek = Calendar.MONDAY
                    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                records.filter { it.date >= cal.timeInMillis }
            }
            StatsMode.MONTH -> records.filter { 
                calendar.timeInMillis = it.date
                calendar.get(Calendar.YEAR) == date.get(Calendar.YEAR) && calendar.get(Calendar.MONTH) == date.get(Calendar.MONTH)
            }
            StatsMode.YEAR -> records.filter { 
                calendar.timeInMillis = it.date
                calendar.get(Calendar.YEAR) == date.get(Calendar.YEAR)
            }
        }

        val typeFiltered = filtered.filter {
            if (showIncome) it.categoryName in incomeNames
            else it.categoryName !in incomeNames
        }

        typeFiltered.groupBy { it.categoryName }
            .mapValues { it.value.sumOf { r -> r.amount }.toFloat() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val categoryRanking: StateFlow<List<CategoryRank>> = combine(categoryDistribution, allCategories) { map, categories ->
        val iconMap = categories.associate { it.name to it.icon }
        val total = map.values.sum()
        map.entries
            .map {
                CategoryRank(
                    name = it.key,
                    amount = it.value,
                    percentage = if (total > 0f) it.value / total else 0f,
                    icon = iconMap[it.key] ?: ""
                )
            }
            .sortedByDescending { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _showDetail = MutableStateFlow(false)
    val showDetail: StateFlow<Boolean> = _showDetail

    private val parentCategoryDistribution: StateFlow<Map<String, Float>> = combine(
        repository.allRecords, _mode, _selectedDate, _isCategoryIncome, repository.allCategories
    ) { records, currentMode, date, showIncome, categories ->
        if (records.isEmpty()) return@combine emptyMap<String, Float>()

        val incomeNames = categories.filter { it.isIncome }.map { it.name }.toSet()
        val calendar = Calendar.getInstance()
        val filtered = when (currentMode) {
            StatsMode.RECENT -> {
                val cal = Calendar.getInstance().apply {
                    firstDayOfWeek = Calendar.MONDAY
                    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                records.filter { it.date >= cal.timeInMillis }
            }
            StatsMode.MONTH -> records.filter {
                calendar.timeInMillis = it.date
                calendar.get(Calendar.YEAR) == date.get(Calendar.YEAR) && calendar.get(Calendar.MONTH) == date.get(Calendar.MONTH)
            }
            StatsMode.YEAR -> records.filter {
                calendar.timeInMillis = it.date
                calendar.get(Calendar.YEAR) == date.get(Calendar.YEAR)
            }
        }

        val typeFiltered = filtered.filter {
            if (showIncome) it.categoryName in incomeNames
            else it.categoryName !in incomeNames
        }

        val parentMap = categories.filter { it.parentName != null }.associate { it.name to it.parentName!! }
        typeFiltered.groupBy { parentMap[it.categoryName] ?: it.categoryName }
            .mapValues { it.value.sumOf { r -> r.amount }.toFloat() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val activeCategoryDistribution: StateFlow<Map<String, Float>> = combine(
        categoryDistribution, parentCategoryDistribution, _showDetail
    ) { detail, parent, showDetail -> if (showDetail) detail else parent }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val activeRanking: StateFlow<List<CategoryRank>> = combine(
        activeCategoryDistribution, allCategories, _showDetail
    ) { map, categories, showDetail ->
        val parentMap = if (showDetail) {
            categories.filter { it.parentName != null }.associate { it.name to it.parentName!! }
        } else emptyMap()
        val iconMap = categories.associate { it.name to it.icon }
        val total = map.values.sum()
        map.entries
            .map {
                val name = if (showDetail) {
                    val parent = parentMap[it.key]
                    if (parent != null) "$parent-${it.key}" else it.key
                } else it.key
                CategoryRank(
                    name = name,
                    amount = it.value,
                    percentage = if (total > 0f) it.value / total else 0f,
                    icon = iconMap[it.key] ?: ""
                )
            }
            .sortedByDescending { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val prevPeriodDistribution: StateFlow<Pair<Map<String, Float>, Map<String, Float>>> = combine(
        repository.allRecords, _mode, _selectedDate, _isCategoryIncome, repository.allCategories
    ) { records, currentMode, date, showIncome, categories ->
        val incomeNames = categories.filter { it.isIncome }.map { it.name }.toSet()
        val (prevStart, prevEnd) = getPreviousPeriodRange(currentMode, date)

        val prevFiltered = records.filter { it.date >= prevStart && it.date <= prevEnd }
        val typeFiltered = prevFiltered.filter {
            if (showIncome) it.categoryName in incomeNames
            else it.categoryName !in incomeNames
        }

        val subDist = typeFiltered.groupBy { it.categoryName }
            .mapValues { it.value.sumOf { r -> r.amount }.toFloat() }

        val parentMap = categories.filter { it.parentName != null }
            .associate { it.name to it.parentName!! }
        val parentDist = typeFiltered.groupBy { parentMap[it.categoryName] ?: it.categoryName }
            .mapValues { it.value.sumOf { r -> r.amount }.toFloat() }

        Pair(subDist, parentDist)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Pair(emptyMap(), emptyMap()))

    val activeComparisonMap: StateFlow<Map<String, CategoryComparisonInfo>> = combine(
        activeCategoryDistribution, prevPeriodDistribution, _showDetail, allCategories
    ) { currentDist, (prevSub, prevParent), showDetail, categories ->
        if (currentDist.isEmpty()) return@combine emptyMap<String, CategoryComparisonInfo>()

        val prevDist = if (showDetail) prevSub else prevParent
        val parentMap = if (showDetail) {
            categories.filter { it.parentName != null }.associate { it.name to it.parentName!! }
        } else emptyMap()

        currentDist.mapValues { (name, currentAmount) ->
            val prevAmount = prevDist[name] ?: 0f
            val changeAmount = currentAmount - prevAmount
            val changePercent = if (prevAmount > 0f) changeAmount / prevAmount else Float.NaN
            CategoryComparisonInfo(changeAmount, changePercent, prevAmount)
        }.mapKeys { (name, _) ->
            if (showDetail) {
                val parent = parentMap[name]
                if (parent != null) "$parent-$name" else name
            } else name
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun toggleDetail() {
        _showDetail.value = !_showDetail.value
    }

    fun toggleCategoryType() {
        _isCategoryIncome.value = !_isCategoryIncome.value
    }

    fun setMode(newMode: StatsMode) {
        _mode.value = newMode
    }

    fun setDateFromMillis(millis: Long) {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        val newDate = _selectedDate.value.clone() as Calendar
        when (_mode.value) {
            StatsMode.MONTH -> {
                newDate.set(Calendar.YEAR, cal.get(Calendar.YEAR))
                newDate.set(Calendar.MONTH, cal.get(Calendar.MONTH))
                newDate.set(Calendar.DAY_OF_MONTH, 1)
            }
            StatsMode.YEAR -> {
                newDate.set(Calendar.YEAR, cal.get(Calendar.YEAR))
                newDate.set(Calendar.MONTH, 0)
                newDate.set(Calendar.DAY_OF_MONTH, 1)
            }
            else -> {}
        }
        _selectedDate.value = newDate
    }

    fun adjustDate(amount: Int) {
        val newDate = _selectedDate.value.clone() as Calendar
        when (_mode.value) {
            StatsMode.MONTH -> newDate.add(Calendar.MONTH, amount)
            StatsMode.YEAR -> newDate.add(Calendar.YEAR, amount)
            else -> {}
        }
        _selectedDate.value = newDate
    }

    private var skipNextReset = false
    private var currentScrollPosition = 0
    private var savedScrollPosition: Int? = null

    fun skipNextReset() {
        skipNextReset = true
    }

    fun saveCurrentScroll(pos: Int) {
        currentScrollPosition = pos
    }

    fun saveScrollOnExit() {
        savedScrollPosition = if (skipNextReset) currentScrollPosition else null
    }

    fun consumeScrollPosition(): Int? {
        val v = savedScrollPosition
        savedScrollPosition = null
        return v
    }

    fun resetToDefaultIfNeeded() {
        if (skipNextReset) {
            skipNextReset = false
            return
        }
        resetToDefault()
    }

    fun resetToDefault() {
        _mode.value = StatsMode.RECENT
        _isCategoryIncome.value = false
        _showDetail.value = false
        _selectedDate.value = Calendar.getInstance()
    }

    private fun getPreviousPeriodRange(mode: StatsMode, date: Calendar): Pair<Long, Long> {
        val cal = date.clone() as Calendar
        return when (mode) {
            StatsMode.RECENT -> {
                cal.firstDayOfWeek = Calendar.MONDAY
                cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val thisMonday = cal.timeInMillis
                cal.add(Calendar.DAY_OF_WEEK, -7)
                Pair(cal.timeInMillis, thisMonday - 1)
            }
            StatsMode.MONTH -> {
                cal.clear()
                cal.set(date.get(Calendar.YEAR), date.get(Calendar.MONTH), 1)
                val thisMonthStart = cal.timeInMillis
                cal.add(Calendar.MONTH, -1)
                Pair(cal.timeInMillis, thisMonthStart - 1)
            }
            StatsMode.YEAR -> {
                cal.clear()
                cal.set(date.get(Calendar.YEAR), 0, 1)
                val thisYearStart = cal.timeInMillis
                cal.add(Calendar.YEAR, -1)
                Pair(cal.timeInMillis, thisYearStart - 1)
            }
        }
    }
}
