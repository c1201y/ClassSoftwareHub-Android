package com.classsoftwarehub.android.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.classsoftwarehub.android.data.model.DownloadTask
import com.classsoftwarehub.android.data.repository.DownloadRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DownloadsViewModel(private val repository: DownloadRepository) : ViewModel() {

    val tasks: StateFlow<List<DownloadTask>> = repository.allTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun cancel(id: Long) {
        viewModelScope.launch { repository.cancel(id) }
    }

    fun retry(id: Long) {
        viewModelScope.launch { repository.retry(id) }
    }

    fun remove(id: Long) {
        viewModelScope.launch { repository.remove(id) }
    }

    fun clearCompleted() {
        viewModelScope.launch { repository.clearCompleted() }
    }

    fun clearFinished() {
        viewModelScope.launch { repository.clearFinished() }
    }
}
