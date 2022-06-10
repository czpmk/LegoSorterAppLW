package com.lsorter.view.analyzeLW

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class AnalyzeLWViewModel : ViewModel() {

    private val _eventActionButtonClicked = MutableLiveData<Boolean>()
    val eventActionButtonClicked
        get() = _eventActionButtonClicked

    fun onActionButtonClicked() {
        eventActionButtonClicked.value = true
    }

}