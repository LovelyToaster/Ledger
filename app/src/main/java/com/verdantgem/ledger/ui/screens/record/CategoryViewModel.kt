package com.verdantgem.ledger.ui.screens.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verdantgem.ledger.data.model.BrandMapping
import com.verdantgem.ledger.data.model.Category
import com.verdantgem.ledger.data.remote.AddressResult
import com.verdantgem.ledger.data.remote.LocationDelegate
import com.verdantgem.ledger.data.remote.LocationProvider
import com.verdantgem.ledger.data.repository.LedgerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CategoryViewModel @Inject constructor(
    private val repository: LedgerRepository,
    locationProvider: LocationProvider
) : ViewModel() {

    private val locationDelegate = LocationDelegate(locationProvider)

    val allCategories: StateFlow<List<Category>> = repository.allCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allBrandMappings: StateFlow<List<BrandMapping>> = repository.allBrandMappings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 分类编辑页面的父类别展开状态，提升到 ViewModel 以避免 Navigation 跳转丢失 */
    private val _expandedParentIds = MutableStateFlow<Set<Long>>(emptySet())
    val expandedParentIds: StateFlow<Set<Long>> = _expandedParentIds.asStateFlow()

    fun toggleExpand(parentId: Long) {
        _expandedParentIds.value = if (parentId in _expandedParentIds.value)
            _expandedParentIds.value - parentId
        else
            _expandedParentIds.value + parentId
    }

    fun resetExpandState() {
        _expandedParentIds.value = emptySet()
    }

    fun startLocation() = locationDelegate.startLocation(viewModelScope)

    fun stopLocation() = locationDelegate.stopLocation()

    fun addCategory(name: String, parentName: String?, isIncome: Boolean, icon: String = "default_icon") {
        viewModelScope.launch {
            repository.addCategory(Category(name = name, parentName = parentName, isIncome = isIncome, icon = icon))
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            repository.softDeleteCategory(category)
        }
    }

    fun updateCategory(category: Category) {
        viewModelScope.launch {
            repository.updateCategory(category)
        }
    }

    fun resetToDefault() {
        viewModelScope.launch {
            repository.resetToDefaultCategories()
        }
    }

    fun recordUserChoice(brandName: String, categoryName: String) {
        viewModelScope.launch {
            val cat = repository.getAllCategoriesList().firstOrNull { it.name == categoryName }
            if (cat != null) {
                repository.recordUserChoice(brandName, cat.id)
            }
        }
    }

    // ========== 提示词管理 ==========

    fun addPrompt(categoryId: Long, prompt: String) {
        viewModelScope.launch {
            val cat = repository.getAllCategoriesList().firstOrNull { it.id == categoryId } ?: return@launch
            val prompts = cat.prompts.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
            val trimmed = prompt.trim()
            if (trimmed.isNotEmpty() && trimmed !in prompts) {
                prompts.add(trimmed)
                repository.updateCategory(cat.copy(prompts = prompts.joinToString(",")))
            }
        }
    }

    fun removePrompt(categoryId: Long, prompt: String) {
        viewModelScope.launch {
            val cat = repository.getAllCategoriesList().firstOrNull { it.id == categoryId } ?: return@launch
            val prompts = cat.prompts.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
            if (prompts.remove(prompt.trim())) {
                repository.updateCategory(cat.copy(prompts = prompts.joinToString(",")))
            }
        }
    }

    // ========== 品牌映射管理 ==========

    fun addBrandMapping(brandName: String, categoryId: Long) {
        viewModelScope.launch {
            repository.addBrandMapping(brandName, categoryId)
        }
    }

    fun deleteBrandMapping(id: Long) {
        viewModelScope.launch {
            repository.deleteBrandMapping(id)
        }
    }

    suspend fun saveRecord(amount: Double, note: String, categoryName: String?, isIncome: Boolean, billDate: Long = System.currentTimeMillis()): Boolean {
        locationDelegate.joinLocation()
        return repository.saveRecordWithFallback(amount, note, categoryName, isIncome, locationDelegate.address, billDate)
    }

    override fun onCleared() {
        super.onCleared()
        locationDelegate.stopLocation()
    }
}
