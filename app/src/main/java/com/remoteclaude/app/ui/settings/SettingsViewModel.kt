package com.remoteclaude.app.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ThemeMode { SYSTEM, DARK, LIGHT }
enum class BellMode { VIBRATE, SOUND, NONE }

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val fontFamily: String = "JetBrains Mono",
    val fontSize: Int = 14,
    val scrollbackLines: Int = 10_000,
    val bellMode: BellMode = BellMode.VIBRATE,
    val showFontPicker: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ViewModel() {

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val FONT_FAMILY = stringPreferencesKey("font_family")
        val FONT_SIZE = intPreferencesKey("font_size")
        val SCROLLBACK_LINES = intPreferencesKey("scrollback_lines")
        val BELL_MODE = stringPreferencesKey("bell_mode")
    }

    val uiState: StateFlow<SettingsUiState> = dataStore.data
        .map { prefs ->
            SettingsUiState(
                themeMode = prefs[THEME_MODE]?.let { ThemeMode.valueOf(it) } ?: ThemeMode.SYSTEM,
                fontFamily = prefs[FONT_FAMILY] ?: "JetBrains Mono",
                fontSize = prefs[FONT_SIZE] ?: 14,
                scrollbackLines = prefs[SCROLLBACK_LINES] ?: 10_000,
                bellMode = prefs[BELL_MODE]?.let { BellMode.valueOf(it) } ?: BellMode.VIBRATE,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState(),
        )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            dataStore.edit { it[THEME_MODE] = mode.name }
        }
    }

    fun setFontSize(size: Int) {
        viewModelScope.launch {
            dataStore.edit { it[FONT_SIZE] = size.coerceIn(8, 24) }
        }
    }

    fun setFontFamily(family: String) {
        viewModelScope.launch {
            dataStore.edit { it[FONT_FAMILY] = family }
        }
    }

    fun setBellMode(mode: BellMode) {
        viewModelScope.launch {
            dataStore.edit { it[BELL_MODE] = mode.name }
        }
    }

    fun showFontPicker() {
        // Handled via local state in the composable
    }
}
