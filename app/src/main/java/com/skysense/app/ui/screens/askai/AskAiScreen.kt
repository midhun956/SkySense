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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import kotlinx.coroutines.launch
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skysense.app.util.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.skysense.app.data.network.NetworkMonitor
import com.skysense.app.data.remote.GeminiApiClient
import com.skysense.app.data.repository.GnssRepository
import com.skysense.app.data.store.SecurePreferencesManager
import com.skysense.app.ui.theme.*

private val suggestedQuestions = listOf(
    "Why is my GPS accuracy poor?",
    "Why are some satellites not being used?",
    "What is the difference between L1 and L5?",
    "What's my current location?"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskAiScreen(
    repository: GnssRepository,
    environmentRepository: com.skysense.app.data.repository.EnvironmentRepository,
    prefsManager: SecurePreferencesManager,
    geminiClient: GeminiApiClient,
    networkMonitor: NetworkMonitor,
    onNavigateToSettings: () -> Unit
) {
    val activity = LocalContext.current as ComponentActivity
    val viewModel: AskAiViewModel = viewModel(
        viewModelStoreOwner = activity,
        factory = AskAiViewModel.Factory(repository, environmentRepository, prefsManager, geminiClient)
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isOnline by networkMonitor.isOnline.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var showModelSelector by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    var previousIsLoading by remember { mutableStateOf(state.isLoading) }

    LaunchedEffect(state.isLoading) {
        if (previousIsLoading && !state.isLoading) {
            val lastMsg = state.messages.lastOrNull()
            if (lastMsg != null && !lastMsg.isUser) {
                if (lastMsg.isError) {
                    view.performHapticReject()
                } else {
                    view.performHapticConfirm()
                }
            }
        }
        previousIsLoading = state.isLoading
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    val infiniteTransition = rememberInfiniteTransition()
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Reverse)
    )

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Blue flowing gradient at the bottom, behind Scaffold
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, CosmicBlueDark.copy(alpha = glowAlpha))
                    )
                )
        )

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
                                        .clickable { 
                                            view.performHapticSegmentTick()
                                            showModelSelector = true 
                                        },
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
                        val view = LocalView.current
                        if (state.messages.isNotEmpty()) {
                            IconButton(onClick = {
                                view.performHapticReject()
                                viewModel.clearConversation()
                            }) {
                                Icon(Icons.Default.DeleteSweep, "Clear", tint = DimGrey)
                            }
                        }
                        IconButton(onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            onNavigateToSettings()
                        }) {
                            Icon(Icons.Default.Settings, "Settings", tint = DimGrey)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            containerColor = Color.Transparent
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
                        modifier = Modifier.fillMaxSize().weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.messages, key = { it.id }) { msg ->
                            ChatBubble(msg)
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }
        }
        
        // Floating Input Bar
        if (state.isAiEnabled && state.apiKeySet && isOnline) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(bottom = 16.dp)
            ) {
                ChatInputBar(
                    value = state.currentInput,
                    onValueChange = viewModel::onInputChanged,
                    onSend = viewModel::sendMessage,
                    isLoading = state.isLoading
                )
            }
        }
    }

    if (showModelSelector) {
        ModalBottomSheet(
            onDismissRequest = { showModelSelector = false },
            sheetState = sheetState,
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
                                scope.launch {
                                    sheetState.hide()
                                    showModelSelector = false
                                }
                                viewModel.onModelSelected(model)
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
            .padding(horizontal = 24.dp)
            .padding(top = 60.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "orb")
        val orbScale by infiniteTransition.animateFloat(
            initialValue = 0.95f, targetValue = 1.05f,
            animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Reverse),
            label = "orbScale"
        )
        
        Icon(
            Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = CosmicBlue,
            modifier = Modifier.size(48.dp).scale(orbScale)
        )

        Text(
            "What should we focus on?",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Medium),
            color = StarWhite,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        Text("Suggested questions", style = MaterialTheme.typography.titleSmall, color = DimGrey)

        suggestedQuestions.forEach { question ->
            SuggestedQuestionChip(question, onClick = { onQuestionClick(question) })
        }
    }
}

@Composable
private fun SuggestedQuestionChip(question: String, onClick: () -> Unit) {
    val view = LocalView.current
    Surface(
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onClick()
        },
        shape = RoundedCornerShape(percent = 50),
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(1.dp, SpaceCard),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, null, tint = DimGrey, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(12.dp))
            Text(question, style = MaterialTheme.typography.bodyMedium, color = StarWhite)
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
    Box(modifier = Modifier.padding(16.dp)) {
        Surface(
            color = SpaceDeep,
            shape = RoundedCornerShape(percent = 50),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                
                androidx.compose.foundation.text.BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = StarWhite),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(CosmicBlue),
                    maxLines = 3,
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (value.isEmpty()) {
                                Text("Ask Gemini", color = DimGrey, style = MaterialTheme.typography.bodyLarge)
                            }
                            innerTextField()
                        }
                    }
                )
                val view = LocalView.current
                FilledIconButton(
                    onClick = {
                        onSend()
                    },
                    enabled = value.isNotBlank() && !isLoading,
                    modifier = Modifier.size(40.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = CosmicBlue.copy(alpha = 0.2f))
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = StarWhite,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Send, "Send", tint = StarWhite, modifier = Modifier.size(18.dp))
                    }
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
