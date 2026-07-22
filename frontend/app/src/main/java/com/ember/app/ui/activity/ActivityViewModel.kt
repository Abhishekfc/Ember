package com.ember.app.ui.activity

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.app.data.ActivityRepository
import com.ember.app.data.remote.dto.ActivityEventDto
import kotlinx.coroutines.launch

class ActivityViewModel(private val repository: ActivityRepository) : ViewModel() {

    var events by mutableStateOf<List<ActivityEventDto>>(emptyList())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    init {
        loadActivity()
    }

    fun loadActivity(isPullRefresh: Boolean = false) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            repository.getActivity(forceRefresh = isPullRefresh).fold(
                onSuccess = { events = it },
                onFailure = { errorMessage = it.message ?: "Couldn't load activity" },
            )
            isLoading = false
        }
    }
}
