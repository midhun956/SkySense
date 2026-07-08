package com.skysense.app.ui.screens.askai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.skysense.app.data.remote.GeminiApiClient
import com.skysense.app.data.repository.GnssRepository
import com.skysense.app.data.store.SecurePreferencesManager
import com.skysense.app.data.model.PromptProfile
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val isError: Boolean = false,
    val isLoading: Boolean = false
)

data class AskAiUiState(
    val messages: List<ChatMessage> = emptyList(),
    val currentInput: String = "",
    val isLoading: Boolean = false,
    val isAiEnabled: Boolean = false,
    val apiKeySet: Boolean = false,
    val promptProfile: PromptProfile = PromptProfile.BEGINNER
)

class AskAiViewModel(
    private val repository: GnssRepository,
    private val prefsManager: SecurePreferencesManager,
    private val geminiClient: GeminiApiClient
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val _currentInput = MutableStateFlow("")
    private val _isLoading = MutableStateFlow(false)

    val uiState: StateFlow<AskAiUiState> = combine(
        _messages,
        _currentInput,
        _isLoading,
        prefsManager.isAiEnabled,
        prefsManager.apiKey,
        prefsManager.promptProfile
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val messages = values[0] as List<ChatMessage>
        val input = values[1] as String
        val loading = values[2] as Boolean
        val enabled = values[3] as Boolean
        val key = values[4] as String?
        val profile = values[5] as PromptProfile
        AskAiUiState(
            messages = messages,
            currentInput = input,
            isLoading = loading,
            isAiEnabled = enabled,
            apiKeySet = !key.isNullOrBlank(),
            promptProfile = profile
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AskAiUiState()
    )

    fun onInputChanged(text: String) { _currentInput.value = text }

    fun sendMessage() {
        val text = _currentInput.value.trim()
        if (text.isEmpty() || _isLoading.value) return
        _currentInput.value = ""

        val userMsg = ChatMessage(text = text, isUser = true)
        _messages.value = _messages.value + userMsg

        viewModelScope.launch {
            _isLoading.value = true
            val loadingMsg = ChatMessage(text = "…", isUser = false, isLoading = true)
            _messages.value = _messages.value + loadingMsg

            val apiKey = prefsManager.apiKey.first()
            if (apiKey.isNullOrBlank()) {
                _messages.value = _messages.value.dropLast(1) + ChatMessage(
                    text = "No API key set. Please add your Gemini API key in Settings.",
                    isUser = false, isError = true
                )
                _isLoading.value = false
                return@launch
            }

            val snapshot = repository.liveSnapshot.value
            val profile = prefsManager.promptProfile.first()
            val customPrompt = prefsManager.customPrompt.first()

            val result = geminiClient.ask(
                userQuestion = text,
                snapshot = snapshot,
                profile = profile,
                customPromptText = customPrompt,
                apiKey = apiKey
            )

            _messages.value = _messages.value.dropLast(1) + result.fold(
                onSuccess = { response -> ChatMessage(text = response, isUser = false) },
                onFailure = { e -> ChatMessage(
                    text = "Error: ${e.message ?: "Failed to get response"}",
                    isUser = false, isError = true
                )}
            )
            _isLoading.value = false
        }
    }

    fun clearConversation() { _messages.value = emptyList() }

    class Factory(
        private val repository: GnssRepository,
        private val prefsManager: SecurePreferencesManager,
        private val geminiClient: GeminiApiClient
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return AskAiViewModel(repository, prefsManager, geminiClient) as T
        }
    }
}
