package com.verdantgem.ledger.ui.screens.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verdantgem.ledger.data.model.Budget
import com.verdantgem.ledger.data.repository.LedgerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

private val currentMonthStart: Long by lazy {
    Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private val currentMonthEnd: Long by lazy {
    Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis
}

@HiltViewModel
class BudgetEditViewModel @Inject constructor(
    private val repository: LedgerRepository
) : ViewModel() {

    val budget: StateFlow<Budget?> = repository.budgetFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val monthlyExpense: StateFlow<Double> = repository.getMonthlyExpenseFlow(currentMonthStart, currentMonthEnd)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    fun saveBudget(amount: Double) {
        viewModelScope.launch {
            repository.saveBudget(amount)
        }
    }

    fun clearBudget() {
        viewModelScope.launch {
            repository.clearBudget()
        }
    }
}
