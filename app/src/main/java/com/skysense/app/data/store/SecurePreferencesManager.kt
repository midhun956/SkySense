package com.skysense.app.data.store

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.skysense.app.data.model.PromptProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.Base64

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "skysense_prefs")

/**
 * Modern secure preferences manager using Jetpack DataStore + Google Tink + Android Keystore.
 *
 * - API keys are AES256-GCM encrypted with a key anchored in Android Keystore (hardware-backed)
 * - Tink handles all cryptographic operations (never deprecated, actively maintained by Google)
 * - DataStore is async/coroutine-based (no ANR risk unlike EncryptedSharedPreferences)
 */
class SecurePreferencesManager(private val context: Context) {

    companion object {
        private const val TAG = "SecurePrefs"
        private const val KEYSET_NAME = "skysense_keyset"
        private const val KEYSET_PREF_NAME = "skysense_keyset_prefs"
        private const val KEY_URI = "android-keystore://skysense_master_key"
        private val ASSOCIATED_DATA = "skysense_api_key".toByteArray()

        // DataStore keys
        private val KEY_AI_ENABLED = booleanPreferencesKey("ai_enabled")
        private val KEY_ENCRYPTED_API_KEY = stringPreferencesKey("encrypted_api_key")
        private val KEY_PROMPT_PROFILE = stringPreferencesKey("prompt_profile")
        private val KEY_CUSTOM_PROMPT = stringPreferencesKey("custom_prompt")
        private val KEY_HISTORY_RETENTION_HOURS = intPreferencesKey("history_retention_hours")
    }

    private val aead: Aead by lazy {
        AeadConfig.register()
        AndroidKeysetManager.Builder()
            .withSharedPref(context, KEYSET_NAME, KEYSET_PREF_NAME)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(KEY_URI)
            .build()
            .keysetHandle
            .getPrimitive(Aead::class.java)
    }

    /** Encrypts a plaintext string using Tink AEAD and returns Base64 ciphertext. */
    private fun encrypt(plaintext: String): String {
        val ciphertext = aead.encrypt(plaintext.toByteArray(Charsets.UTF_8), ASSOCIATED_DATA)
        return Base64.getEncoder().encodeToString(ciphertext)
    }

    /** Decrypts a Base64 ciphertext string using Tink AEAD. Returns null on failure. */
    private fun decrypt(ciphertext: String): String? = try {
        val bytes = Base64.getDecoder().decode(ciphertext)
        aead.decrypt(bytes, ASSOCIATED_DATA).toString(Charsets.UTF_8)
    } catch (e: Exception) {
        Log.e(TAG, "Decryption failed", e)
        null
    }

    // ── AI Settings ───────────────────────────────────────────────────────────

    val isAiEnabled: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_AI_ENABLED] ?: false }

    suspend fun setAiEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AI_ENABLED] = enabled }
    }

    /** Securely stores the Gemini API key encrypted with Tink + Android Keystore. */
    suspend fun saveApiKey(apiKey: String) {
        val encrypted = encrypt(apiKey)
        context.dataStore.edit { it[KEY_ENCRYPTED_API_KEY] = encrypted }
    }

    /** Returns the decrypted API key, or null if not set / decryption fails. */
    val apiKey: Flow<String?> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            prefs[KEY_ENCRYPTED_API_KEY]?.let { decrypt(it) }
        }

    suspend fun clearApiKey() {
        context.dataStore.edit { it.remove(KEY_ENCRYPTED_API_KEY) }
    }

    // ── Prompt Profile ────────────────────────────────────────────────────────

    val promptProfile: Flow<PromptProfile> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            prefs[KEY_PROMPT_PROFILE]?.let {
                runCatching { PromptProfile.valueOf(it) }.getOrNull()
            } ?: PromptProfile.BEGINNER
        }

    suspend fun setPromptProfile(profile: PromptProfile) {
        context.dataStore.edit { it[KEY_PROMPT_PROFILE] = profile.name }
    }

    val customPrompt: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_CUSTOM_PROMPT] ?: "" }

    suspend fun setCustomPrompt(prompt: String) {
        context.dataStore.edit { it[KEY_CUSTOM_PROMPT] = prompt }
    }

    // ── History Settings ──────────────────────────────────────────────────────

    val historyRetentionHours: Flow<Int> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_HISTORY_RETENTION_HOURS] ?: 24 }

    suspend fun setHistoryRetentionHours(hours: Int) {
        context.dataStore.edit { it[KEY_HISTORY_RETENTION_HOURS] = hours }
    }

    suspend fun clearAllPreferences() {
        context.dataStore.edit { it.clear() }
    }
}
