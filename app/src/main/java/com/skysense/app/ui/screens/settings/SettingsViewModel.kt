package com.skysense.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.skysense.app.data.model.PromptProfile
import com.skysense.app.data.repository.GnssRepository
import com.skysense.app.data.store.SecurePreferencesManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isAiEnabled: Boolean = false,
    val apiKeyMasked: String = "",
    val promptProfile: PromptProfile = PromptProfile.BEGINNER,
    val customPrompt: String = "",
    val historyRetentionHours: Int = 24,
    val isSavingKey: Boolean = false,
    val savedMessage: String? = null
)

class SettingsViewModel(
    private val prefsManager: SecurePreferencesManager,
    private val repository: GnssRepository
) : ViewModel() {

    private val _isSavingKey = MutableStateFlow(false)
    private val _savedMessage = MutableStateFlow<String?>(null)
    private val _apiKeyDraft = MutableStateFlow("")

    val uiState: StateFlow<SettingsUiState> = combine(
        prefsManager.isAiEnabled,
        prefsManager.apiKey,
        prefsManager.promptProfile,
        prefsManager.customPrompt,
        prefsManager.historyRetentionHours,
        _isSavingKey,
        _savedMessage
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val enabled = values[0] as Boolean
        val key = values[1] as String?
        val profile = values[2] as PromptProfile
        val customPrompt = values[3] as String
        val retention = values[4] as Int
        val saving = values[5] as Boolean
        val msg = values[6] as String?
        SettingsUiState(
            isAiEnabled = enabled,
            apiKeyMasked = if (!key.isNullOrBlank()) "••••••••${key.takeLast(4)}" else "",
            promptProfile = profile,
            customPrompt = customPrompt,
            historyRetentionHours = retention,
            isSavingKey = saving,
            savedMessage = msg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState()
    )

    fun toggleAiEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsManager.setAiEnabled(enabled) }
    }

    fun saveApiKey(key: String) {
        viewModelScope.launch {
            _isSavingKey.value = true
            prefsManager.saveApiKey(key.trim())
            _isSavingKey.value = false
            _savedMessage.value = "API key saved securely"
        }
    }

    fun clearApiKey() {
        viewModelScope.launch {
            prefsManager.clearApiKey()
            _savedMessage.value = "API key removed"
        }
    }

    fun setPromptProfile(profile: PromptProfile) {
        viewModelScope.launch { prefsManager.setPromptProfile(profile) }
    }

    fun setCustomPrompt(text: String) {
        viewModelScope.launch { prefsManager.setCustomPrompt(text) }
    }

    fun setRetentionHours(hours: Int) {
        viewModelScope.launch { prefsManager.setHistoryRetentionHours(hours) }
    }

    fun clearHistory() {
        viewModelScope.launch { repository.clearAllHistory() }
    }

    fun dismissSavedMessage() { _savedMessage.value = null }

    class Factory(
        private val prefsManager: SecurePreferencesManager,
        private val repository: GnssRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(prefsManager, repository) as T
        }
    }
}
