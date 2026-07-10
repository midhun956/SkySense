package com.skysense.app.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.platform.LocalView
import com.skysense.app.util.*
import android.view.HapticFeedbackConstants
import com.skysense.app.data.model.PromptProfile
import com.skysense.app.data.store.SecurePreferencesManager
import com.skysense.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    prefsManager: SecurePreferencesManager,
    onClearHistory: () -> Unit,
    onBack: () -> Unit
) {
    SettingsContent(
        prefsManager = prefsManager,
        onClearHistory = onClearHistory,
        onBack = onBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    prefsManager: SecurePreferencesManager,
    onClearHistory: () -> Unit,
    onBack: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val view = LocalView.current
    // Use a simple local state approach for the Settings page since we don't have full DI here
    var isAiEnabled by remember { mutableStateOf(false) }
    var apiKeyInput by remember { mutableStateOf("") }
    var apiKeyVisible by remember { mutableStateOf(false) }
    var selectedProfile by remember { mutableStateOf(PromptProfile.BEGINNER) }
    var customPrompt by remember { mutableStateOf("") }
    var savedMessage by remember { mutableStateOf<String?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }

    // Collect from DataStore
    val isAiEnabledFlow by prefsManager.isAiEnabled.collectAsStateWithLifecycle(initialValue = false)
    val apiKey by prefsManager.apiKey.collectAsStateWithLifecycle(initialValue = null)
    val profileFlow by prefsManager.promptProfile.collectAsStateWithLifecycle(initialValue = PromptProfile.BEGINNER)
    val customPromptFlow by prefsManager.customPrompt.collectAsStateWithLifecycle(initialValue = "")

    LaunchedEffect(isAiEnabledFlow) { isAiEnabled = isAiEnabledFlow }
    LaunchedEffect(profileFlow) { selectedProfile = profileFlow }
    LaunchedEffect(customPromptFlow) { customPrompt = customPromptFlow }

    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = StarWhite) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = MoonGrey)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SpaceDeep)
            )
        },
        containerColor = SpaceBlack,
        snackbarHost = {
            savedMessage?.let { msg ->
                LaunchedEffect(msg) {
                    kotlinx.coroutines.delay(2000)
                    savedMessage = null
                }
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    containerColor = SpaceCardElevated,
                    contentColor = StarWhite
                ) { Text(msg) }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── AI Section ────────────────────────────────────────────────────
            SettingsSection(title = "Gemini AI", icon = Icons.Default.AutoAwesome) {
                // AI Enable toggle
                SettingsItem {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Enable AI", style = MaterialTheme.typography.titleSmall, color = StarWhite)
                            Text(
                                "Use Gemini to explain your GPS data",
                                style = MaterialTheme.typography.bodySmall,
                                color = DimGrey
                            )
                        }
                        Switch(
                            checked = isAiEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) view.performHapticToggleOn() else view.performHapticToggleOff()
                                isAiEnabled = enabled
                                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                    prefsManager.setAiEnabled(enabled)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = SpaceBlack,
                                checkedTrackColor = CosmicBlue
                            )
                        )
                    }
                }

                AnimatedVisibility(visible = isAiEnabled) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // API Key
                        SettingsItem {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Gemini API Key", style = MaterialTheme.typography.titleSmall, color = StarWhite)
                                if (!apiKey.isNullOrBlank()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(shape = RoundedCornerShape(8.dp), color = SignalExcellent.copy(alpha = 0.1f)) {
                                            Text(
                                                "Key set: ••••${apiKey!!.takeLast(4)}",
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = SignalExcellent
                                            )
                                        }
                                        TextButton(
                                            onClick = {
                                                view.performHapticReject()
                                                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                                    prefsManager.clearApiKey()
                                                    savedMessage = "API key removed"
                                                }
                                            }
                                        ) {
                                            Text("Remove", color = SignalPoor, style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                                OutlinedTextField(
                                    value = apiKeyInput,
                                    onValueChange = { apiKeyInput = it },
                                    placeholder = { Text("Enter Gemini API key…", color = DimGrey) },
                                    visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                                            Icon(
                                                if (apiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                null, tint = DimGrey
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = CosmicBlue,
                                        unfocusedBorderColor = SpaceDivider,
                                        focusedTextColor = StarWhite,
                                        unfocusedTextColor = StarWhite,
                                        cursorColor = CosmicBlue,
                                        focusedContainerColor = SpaceCard,
                                        unfocusedContainerColor = SpaceCard
                                    )
                                )
                                Text(
                                    "🔒 Keys are encrypted using AES-256-GCM with Android Keystore (hardware-backed). Never sent anywhere except Gemini.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DimGrey
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Don't have a key? Get a free one from Google AI Studio",
                                    style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.Underline),
                                    color = CosmicBlue,
                                    modifier = Modifier.clickable { uriHandler.openUri("https://aistudio.google.com/api-keys/") }
                                )
                                Button(
                                    onClick = {
                                        view.performHapticConfirm()
                                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                            prefsManager.saveApiKey(apiKeyInput)
                                            apiKeyInput = ""
                                            savedMessage = "API key saved securely ✓"
                                        }
                                    },
                                    enabled = apiKeyInput.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(containerColor = CosmicBlue),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Save Key", color = SpaceBlack, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        // Prompt Profile
                        SettingsItem {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Prompt Profile", style = MaterialTheme.typography.titleSmall, color = StarWhite)
                                Text("How AI explains things to you", style = MaterialTheme.typography.bodySmall, color = DimGrey)
                                PromptProfile.values().forEach { profile ->
                                    ProfileOption(
                                        profile = profile,
                                        selected = selectedProfile == profile,
                                        onClick = {
                                            view.performHapticSegmentTick()
                                            selectedProfile = profile
                                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                                prefsManager.setPromptProfile(profile)
                                            }
                                        }
                                    )
                                }
                                AnimatedVisibility(visible = selectedProfile == PromptProfile.CUSTOM) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = customPrompt,
                                            onValueChange = { customPrompt = it },
                                            placeholder = { Text("Enter your custom AI prompt style…", color = DimGrey) },
                                            modifier = Modifier.fillMaxWidth().height(100.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = CosmicBlue,
                                                unfocusedBorderColor = SpaceDivider,
                                                focusedTextColor = StarWhite,
                                                unfocusedTextColor = StarWhite,
                                                cursorColor = CosmicBlue,
                                                focusedContainerColor = SpaceCard,
                                                unfocusedContainerColor = SpaceCard
                                            )
                                        )
                                        Button(
                                            onClick = {
                                                view.performHapticConfirm()
                                                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                                    prefsManager.setCustomPrompt(customPrompt)
                                                    savedMessage = "Custom prompt saved ✓"
                                                }
                                            },
                                            enabled = customPrompt.isNotBlank(),
                                            colors = ButtonDefaults.buttonColors(containerColor = CosmicBlue),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Save Custom Prompt", color = SpaceBlack, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Data Section ──────────────────────────────────────────────────
            SettingsSection(title = "Data & History", icon = Icons.Default.Storage) {
                SettingsItem {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Clear History", style = MaterialTheme.typography.titleSmall, color = StarWhite)
                            Text("Delete all stored GNSS history", style = MaterialTheme.typography.bodySmall, color = DimGrey)
                        }
                        TextButton(onClick = { showClearConfirm = true }) {
                            Text("Clear", color = SignalPoor)
                        }
                    }
                }
            }

            // ── About Section ─────────────────────────────────────────────────
            SettingsSection(title = "About", icon = Icons.Default.Info) {
                SettingsItem {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        AboutRow("App", "SkySense v2.0.0")
                        AboutRow("Architecture", "MVVM + Repository + StateFlow")
                        AboutRow("Storage", "Room Database + DataStore")
                        AboutRow("Security", "AES-256-GCM (Tink + Android Keystore)")
                        AboutRow("AI", "Gemini API (Live GNSS Injection)")
                        AboutRow("UI/UX", "Material 3 + Rich Contextual Haptics")
                    }
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear History?", color = StarWhite) },
            text = { Text("This will permanently delete all stored GNSS history. This cannot be undone.", color = MoonGrey) },
            confirmButton = {
                TextButton(onClick = {
                    view.performHapticReject()
                    showClearConfirm = false
                    onClearHistory()
                    savedMessage = "History cleared"
                }) {
                    Text("Delete", color = SignalPoor)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel", color = MoonGrey)
                }
            },
            containerColor = SpaceCardElevated
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, null, tint = CosmicBlue, modifier = Modifier.size(16.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, color = DimGrey)
        }
        content()
    }
}

@Composable
private fun SettingsItem(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SpaceCard)
    ) {
        Box(modifier = Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun ProfileOption(
    profile: PromptProfile,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) CosmicBlue.copy(alpha = 0.15f) else SpaceCardElevated,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(selectedColor = CosmicBlue)
            )
            Column {
                Text(profile.displayName, style = MaterialTheme.typography.titleSmall, color = if (selected) CosmicBlue else StarWhite)
                Text(profile.description, style = MaterialTheme.typography.bodySmall, color = DimGrey)
            }
        }
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = DimGrey)
        Text(value, style = MaterialTheme.typography.bodySmall, color = MoonGrey)
    }
}
