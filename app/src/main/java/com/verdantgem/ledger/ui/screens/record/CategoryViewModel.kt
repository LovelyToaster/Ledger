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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    fun startLocation() = locationDelegate.startLocation(viewModelScope)

    fun stopLocation() = locationDelegate.stopLocation()

    fun addCategory(name: String, parentName: String?, isIncome: Boolean) {
        viewModelScope.launch {
            repository.addCategory(Category(name = name, parentName = parentName, isIncome = isIncome))
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

    fun learnBrandMapping(brandName: String, categoryName: String) {
        viewModelScope.launch {
            val cat = repository.getAllCategoriesList().firstOrNull { it.name == categoryName }
            if (cat != null) {
                repository.learnBrandMapping(brandName, cat.id)
            }
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
