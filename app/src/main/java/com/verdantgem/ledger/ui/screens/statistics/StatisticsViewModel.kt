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

    fun toggleCategoryType() {
        _isCategoryIncome.value = !_isCategoryIncome.value
    }

    fun setMode(newMode: StatsMode) {
        _mode.value = newMode
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

    fun resetToDefault() {
        _mode.value = StatsMode.RECENT
        _isCategoryIncome.value = false
        _selectedDate.value = Calendar.getInstance()
    }
}
