package com.pocketssh.app.ui.connections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketssh.app.data.model.ConnectionProfile
import com.pocketssh.app.data.repository.ConnectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConnectionListUiState(
    val connections: List<ConnectionProfile> = emptyList(),
    val quickConnectProfile: ConnectionProfile? = null,
)

@HiltViewModel
class ConnectionListViewModel @Inject constructor(
    private val repository: ConnectionRepository,
) : ViewModel() {

    val uiState: StateFlow<ConnectionListUiState> = repository.observeAll()
        .map { connections ->
            val sorted = connections.sortedByDescending { it.lastConnectedAt ?: 0L }
            ConnectionListUiState(
                connections = sorted,
                quickConnectProfile = sorted.firstOrNull { it.lastConnectedAt != null },
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ConnectionListUiState(),
        )

    fun deleteConnection(profile: ConnectionProfile) {
        viewModelScope.launch {
            repository.delete(profile)
        }
    }
}
