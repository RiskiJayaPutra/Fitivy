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

    private val _selectedTarget = MutableLiveData<String>("bebas")
    val selectedTarget: LiveData<String> = _selectedTarget

    fun setActivityType(type: String) {
        _selectedActivity.value = type
    }

    fun setTarget(target: String) {
        _selectedTarget.value = target
    }
}
