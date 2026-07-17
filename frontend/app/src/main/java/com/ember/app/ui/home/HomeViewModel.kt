package com.ember.app.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.app.data.PhotoRepository
import com.ember.app.data.remote.dto.FeedItem
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class HomeViewModel(private val repository: PhotoRepository) : ViewModel() {

    var feedItems by mutableStateOf<List<FeedItem>>(emptyList())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    val greeting: String = run {
        val hour = LocalDateTime.now().hour
        when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    val dateText: String = run {
        val now = LocalDateTime.now()
        val day = now.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        val month = now.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        "$day, $month ${now.dayOfMonth}"
    }

    init {
        loadFeed()
    }

    fun loadFeed() {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            repository.getFeed().fold(
                onSuccess = { feedItems = it },
                onFailure = { errorMessage = it.message ?: "Couldn't load your feed" },
            )
            isLoading = false
        }
    }
}
