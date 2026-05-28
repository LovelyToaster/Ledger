package com.verdantgem.ledger.ui.screens.statistics

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.verdantgem.ledger.data.model.Category
import com.verdantgem.ledger.data.model.Record
import com.verdantgem.ledger.data.repository.LedgerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import java.net.URLDecoder

@HiltViewModel
class CategoryRecordsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: LedgerRepository
) : ViewModel() {

    val categoryName: String = savedStateHandle.get<String>("categoryName")?.let {
        URLDecoder.decode(it, "UTF-8")
    } ?: ""

    val isParent: Boolean = savedStateHandle.get<Boolean>("isParent") ?: false
    val startTime: Long = savedStateHandle.get<Long>("startTime") ?: 0L
    val endTime: Long = savedStateHandle.get<Long>("endTime") ?: 0L
    val isIncome: Boolean = savedStateHandle.get<Boolean>("isIncome") ?: false

    val allCategories: StateFlow<List<Category>> = repository.allCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pagedItems: Flow<PagingData<Record>> = repository.getRecordsByCategoryPaged(
        categoryName = categoryName,
        isParent = isParent,
        isIncome = isIncome,
        startTime = startTime,
        endTime = endTime
    ).cachedIn(viewModelScope)
}
