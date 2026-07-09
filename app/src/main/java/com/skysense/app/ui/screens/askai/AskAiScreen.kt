package com.skysense.app.ui.screens.askai

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.skysense.app.data.network.NetworkMonitor
import com.skysense.app.data.remote.GeminiApiClient
import com.skysense.app.data.repository.GnssRepository
import com.skysense.app.data.store.SecurePreferencesManager
import com.skysense.app.ui.theme.*

private val suggestedQuestions = listOf(
    "Why is my GPS accuracy poor?",
    "What is Galileo and how does it help?",
    "Why are some satellites not being used?",
    "What is the difference between L1 and L5?",
    "How does GPS actually know where I am?",
    "What's my currnet location?"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskAiScreen(
    repository: GnssRepository,
    prefsManager: SecurePreferencesManager,
    geminiClient: GeminiApiClient,
    networkMonitor: NetworkMonitor,
    onNavigateToSettings: () -> Unit
) {
    val activity = LocalContext.current as ComponentActivity
    val viewModel: AskAiViewModel = viewModel(
        viewModelStoreOwner = activity,
        factory = AskAiViewModel.Factory(repository, prefsManager, geminiClient)
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isOnline by networkMonitor.isOnline.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var showModelSelector by remember { mutableStateOf(false) }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.AutoAwesome, null, tint = CosmicBlue, modifier = Modifier.size(20.dp))
                            Text(if (isOnline) "Ask AI" else "Ask AI (Offline)", color = if (isOnline) StarWhite else SignalPoor)
                            if (state.isAiEnabled && state.apiKeySet) {
                                Surface(shape = RoundedCornerShape(50), color = SignalExcellent.copy(alpha = 0.15f)) {
                                    Text(
                                        state.promptProfile.displayName,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SignalExcellent
                                    )
                                }
                            }
                        }
                        if (state.isAiEnabled && state.apiKeySet && state.selectedModel != null) {
                            Row(
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { showModelSelector = true },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Model: ${state.selectedModel}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DimGrey
                                )
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Change Model", tint = DimGrey, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                },
                actions = {
                    if (state.messages.isNotEmpty()) {
                        IconButton(onClick = viewModel::clearConversation) {
                            Icon(Icons.Default.DeleteSweep, "Clear", tint = DimGrey)
                        }
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "Settings", tint = DimGrey)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SpaceDeep)
            )
        },
        containerColor = SpaceBlack,
        bottomBar = {
            if (state.isAiEnabled && state.apiKeySet && isOnline) {
                ChatInputBar(
                    value = state.currentInput,
                    onValueChange = viewModel::onInputChanged,
                    onSend = viewModel::sendMessage,
                    isLoading = state.isLoading
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!isOnline) {
                Surface(
                    color = SignalPoor.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.WifiOff, "Offline", tint = SignalPoor, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("You are offline. Connect to the internet to ask AI.", color = SignalPoor, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (!state.isAiEnabled || !state.apiKeySet) {
                AiDisabledState(
                    isAiEnabled = state.isAiEnabled,
                    apiKeySet = state.apiKeySet,
                    onNavigateToSettings = onNavigateToSettings
                )
            } else if (state.messages.isEmpty()) {
                WelcomeState(
                    onQuestionClick = {
                        viewModel.onInputChanged(it)
                        viewModel.sendMessage()
                    }
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.messages, key = { it.id }) { msg ->
                        ChatBubble(msg)
                    }
                    item { Spacer(Modifier.height(20.dp)) }
                }
            }
        }
    }

    if (showModelSelector) {
        ModalBottomSheet(
            onDismissRequest = { showModelSelector = false },
            containerColor = SpaceCard
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    "Select AI Model",
                    style = MaterialTheme.typography.titleMedium,
                    color = StarWhite,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    "Free-tier limits are hardcoded approximations. Actual limits may vary based on your Google account.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DimGrey,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.availableModels) { model ->
                        val isSelected = model == state.selectedModel
                        val limitText = when {
                            model.contains("gemini-3.1-flash-lite") -> "15 RPM / 500 RPD"
                            model.contains("gemini-2.5-flash-lite") -> "10 RPM / 20 RPD"
                            model.contains("gemini-1.5-flash") -> "15 RPM / 1500 RPD"
                            model.contains("flash") -> "5 RPM / 20 RPD"
                            else -> "Standard Limit"
                        }

                        Surface(
                            onClick = {
                                viewModel.onModelSelected(model)
                                showModelSelector = false
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) CosmicBlue.copy(alpha = 0.2f) else SpaceDeep,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(model, color = if (isSelected) CosmicBlue else StarWhite, style = MaterialTheme.typography.bodyMedium)
                                    Text(limitText, color = MoonGrey, style = MaterialTheme.typography.labelSmall)
                                }
                                if (isSelected) {
                                    Icon(Icons.Default.Check, "Selected", tint = CosmicBlue)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun WelcomeState(onQuestionClick: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        // Animated AI orb
        val infiniteTransition = rememberInfiniteTransition(label = "orb")
        val orbScale by infiniteTransition.animateFloat(
            initialValue = 0.92f, targetValue = 1.08f,
            animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Reverse),
            label = "orbScale"
        )

        Box(
            modifier = Modifier
                .size(80.dp * orbScale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(listOf(CosmicBlue.copy(alpha = 0.4f), CosmicBlue.copy(alpha = 0f)))
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(CosmicBlueDark, SpaceCard))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.AutoAwesome, null, tint = StarWhite, modifier = Modifier.size(28.dp))
            }
        }

        Text(
            "SkySense AI",
            style = MaterialTheme.typography.headlineSmall,
            color = StarWhite
        )
        Text(
            "Ask anything about your GPS signal, satellites, or how GNSS works. Your live location context is automatically included.",
            style = MaterialTheme.typography.bodyMedium,
            color = MoonGrey
        )

        HorizontalDivider(color = SpaceDivider, modifier = Modifier.padding(vertical = 4.dp))

        Text("Suggested questions", style = MaterialTheme.typography.titleSmall, color = DimGrey)

        suggestedQuestions.forEach { question ->
            SuggestedQuestionChip(question, onClick = { onQuestionClick(question) })
        }
    }
}

@Composable
private fun SuggestedQuestionChip(question: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = SpaceCard,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.QuestionAnswer, null, tint = CosmicBlue, modifier = Modifier.size(16.dp))
            Text(question, style = MaterialTheme.typography.bodyMedium, color = MoonGrey, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, null, tint = DimGrey, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun ChatBubble(msg: ChatMessage) {
    val bubbleShape = if (msg.isUser)
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
    else
        RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!msg.isUser) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(CosmicBlueDark),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.AutoAwesome, null, tint = StarWhite, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(8.dp))
        }

        Surface(
            shape = bubbleShape,
            color = when {
                msg.isUser -> CosmicBlueDark
                msg.isError -> SignalPoor.copy(alpha = 0.15f)
                else -> SpaceCardElevated
            },
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            if (msg.isLoading) {
                LoadingDots()
            } else {
                Text(
                    parseMarkdown(msg.text),
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (msg.isError) SignalPoor else StarWhite
                )
            }
        }

        if (msg.isUser) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(SpaceCard),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, null, tint = MoonGrey, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun LoadingDots() {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    val dot1 by infiniteTransition.animateFloat(0.3f, 1f, infiniteRepeatable(tween(600, 0), RepeatMode.Reverse), "d1")
    val dot2 by infiniteTransition.animateFloat(0.3f, 1f, infiniteRepeatable(tween(600, 150), RepeatMode.Reverse), "d2")
    val dot3 by infiniteTransition.animateFloat(0.3f, 1f, infiniteRepeatable(tween(600, 300), RepeatMode.Reverse), "d3")

    Row(
        modifier = Modifier.padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(dot1, dot2, dot3).forEach { alpha ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MoonGrey.copy(alpha = alpha))
            )
        }
    }
}

@Composable
private fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    isLoading: Boolean
) {
    Surface(
        color = SpaceDeep,
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text("Ask about your GPS…", color = DimGrey) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(20.dp),
                maxLines = 3,
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
            FilledIconButton(
                onClick = onSend,
                enabled = value.isNotBlank() && !isLoading,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = CosmicBlue)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = StarWhite,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Send, "Send", tint = SpaceBlack)
                }
            }
        }
    }
}

@Composable
private fun AiDisabledState(
    isAiEnabled: Boolean,
    apiKeySet: Boolean,
    onNavigateToSettings: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(Icons.Default.Lock, null, tint = DimGrey, modifier = Modifier.size(56.dp))
            Text(
                if (!isAiEnabled) "AI is disabled" else "No API key",
                style = MaterialTheme.typography.titleLarge,
                color = StarWhite
            )
            Text(
                if (!isAiEnabled)
                    "Enable AI in Settings to use Gemini-powered explanations."
                else
                    "Add your Gemini API key in Settings to get started.",
                style = MaterialTheme.typography.bodyMedium,
                color = MoonGrey
            )
            Button(
                onClick = onNavigateToSettings,
                colors = ButtonDefaults.buttonColors(containerColor = CosmicBlue)
            ) {
                Icon(Icons.Default.Settings, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Go to Settings", color = SpaceBlack, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun parseMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        var currentIndex = 0
        val regex = Regex("\\*\\*(.*?)\\*\\*")
        val matches = regex.findAll(text)
        for (match in matches) {
            append(text.substring(currentIndex, match.range.first))
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(match.groupValues[1])
            }
            currentIndex = match.range.last + 1
        }
        append(text.substring(currentIndex, text.length))
    }
}
