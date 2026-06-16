package com.fitivy.app.ui.tracker

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class TrackerViewModel @Inject constructor() : ViewModel() {

    private val _selectedActivity = MutableLiveData<String>("walking")
    val selectedActivity: LiveData<String> = _selectedActivity

    private val _targetValue = MutableLiveData<Float>(5.0f)
    val targetValue: LiveData<Float> = _targetValue
    
    private val _targetType = MutableLiveData<String>("distance")
    val targetType: LiveData<String> = _targetType

    fun setActivityType(type: String) {
        _selectedActivity.value = type
    }

    fun setTargetValue(value: Float) {
        _targetValue.value = value
    }
    
    fun setTargetType(type: String) {
        _targetType.value = type
        // Reset or adjust value based on type if needed
    }
}
