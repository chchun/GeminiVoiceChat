package com.aromit.geminivoicechat.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aromit.geminivoicechat.domain.model.ChatMessage
import com.aromit.geminivoicechat.domain.model.SenderType
import com.aromit.geminivoicechat.ui.voice.VoiceState
import dev.jeziellago.compose.markdowntext.MarkdownText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    factory: ChatViewModel.Factory,
    modifier: Modifier = Modifier
) {
    val viewModel: ChatViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    val onSend: () -> Unit = {
        viewModel.onSendClicked()
        keyboardController?.hide()
        focusManager.clearFocus()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startVoiceMode()
        } else {
            Toast.makeText(context, "마이크 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    val onMicClicked: () -> Unit = {
        keyboardController?.hide()
        focusManager.clearFocus()
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.startVoiceMode()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val voiceError = state.voice.errorMessage
    LaunchedEffect(voiceError) {
        if (voiceError != null) {
            Toast.makeText(context, voiceError, Toast.LENGTH_SHORT).show()
        }
    }

    AutoScrollEffect(
        listState = listState,
        messageCount = state.messages.size,
        lastMessageText = state.messages.lastOrNull()?.text,
        lastUserMessageId = state.messages.lastOrNull { it.senderType == SenderType.USER }?.id
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Gemini Voice Chat") },
                actions = {
                    AnimatedVisibility(visible = state.isVoiceInputActive) {
                        VoiceActiveIndicator()
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            MessageList(
                messages = state.messages,
                playingMessageId = state.playingMessageId,
                onTtsToggled = viewModel::onMessageTtsToggled,
                listState = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
            InputBar(
                inputText = state.inputText,
                isSendEnabled = state.isSendEnabled,
                voice = state.voice,
                onInputChanged = viewModel::onInputChanged,
                onSendClicked = onSend,
                onMicClicked = onMicClicked,
                onVoiceStopClicked = viewModel::endVoiceMode
            )
        }
    }
}

@Composable
private fun VoiceActiveIndicator() {
    Box(
        modifier = Modifier
            .padding(end = 12.dp)
            .size(32.dp)
            .background(color = Color(0xFF34A853), shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Mic,
            contentDescription = "음성 모드 활성",
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun AutoScrollEffect(
    listState: LazyListState,
    messageCount: Int,
    lastMessageText: String?,
    lastUserMessageId: String?
) {
    val isUserAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
            lastVisible.index >= layoutInfo.totalItemsCount - 1
        }
    }

    // 유저가 새 메시지를 보낼 때마다 무조건 최하단으로 이동
    LaunchedEffect(lastUserMessageId) {
        if (lastUserMessageId != null && messageCount > 0) {
            listState.animateScrollToItem(messageCount - 1)
        }
    }

    // AI 스트리밍 등 일반 갱신은 사용자가 하단을 보고 있을 때만 따라감
    LaunchedEffect(messageCount, lastMessageText) {
        if (messageCount == 0) return@LaunchedEffect
        if (isUserAtBottom) {
            listState.animateScrollToItem(messageCount - 1)
        }
    }
}

@Composable
private fun MessageList(
    messages: List<ChatMessage>,
    playingMessageId: String?,
    onTtsToggled: (ChatMessage) -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    if (messages.isEmpty()) {
        EmptyState(modifier = modifier)
        return
    }

    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items = messages, key = { it.id }) { message ->
            when (message.senderType) {
                SenderType.USER -> UserMessageBubble(message = message)
                SenderType.AI -> AiMessage(
                    message = message,
                    isPlaying = playingMessageId == message.id,
                    onTtsToggled = { onTtsToggled(message) }
                )
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = "메시지를 입력하거나 마이크를 눌러 대화를 시작하세요.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(32.dp)
        )
    }
}

@Composable
private fun UserMessageBubble(message: ChatMessage) {
    val bubbleColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onPrimary
    val shape = RoundedCornerShape(
        topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp
    )

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Surface(
            color = bubbleColor,
            shape = shape,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                text = message.text,
                color = textColor,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun AiMessage(
    message: ChatMessage,
    isPlaying: Boolean,
    onTtsToggled: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SparkleHeaderIcon(isStreaming = message.isStreaming)
            Spacer(modifier = Modifier.weight(1f))
            val ttsEnabled = message.text.isNotBlank() && !message.isStreaming
            IconButton(
                onClick = onTtsToggled,
                enabled = ttsEnabled
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = if (isPlaying) "TTS 정지" else "TTS 재생",
                    tint = if (isPlaying) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        if (message.text.isEmpty() && message.isStreaming) {
            ThinkingDots(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp)
            )
        } else {
            MarkdownText(
                markdown = message.text,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    }
}

@Composable
private fun SparkleHeaderIcon(isStreaming: Boolean) {
    val transition = rememberInfiniteTransition(label = "sparkle")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sparkle-rotation"
    )
    val angle = if (isStreaming) rotation else 0f
    Icon(
        imageVector = Icons.Filled.AutoAwesome,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .size(28.dp)
            .rotate(angle)
    )
}

@Composable
private fun ThinkingDots(
    color: Color,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "thinking")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "thinking-phase"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val active = phase.toInt() == index
            val alpha = if (active) 1f else 0.3f
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color = color.copy(alpha = alpha), shape = RoundedCornerShape(50))
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InputBar(
    inputText: String,
    isSendEnabled: Boolean,
    voice: VoiceState,
    onInputChanged: (String) -> Unit,
    onSendClicked: () -> Unit,
    onMicClicked: () -> Unit,
    onVoiceStopClicked: () -> Unit
) {
    Surface(
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (voice.isActive) {
                Spacer(modifier = Modifier.size(8.dp))
                VoiceWaveform(
                    amplitude = voice.amplitude,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                )
                FilledIconButton(
                    onClick = onVoiceStopClicked,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Stop,
                        contentDescription = "음성 입력 취소"
                    )
                }
            } else {
                IconButton(
                    onClick = onMicClicked,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = "음성 입력 시작"
                    )
                }

                TextField(
                    value = inputText,
                    onValueChange = onInputChanged,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("메시지를 입력하세요") },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = MaterialTheme.typography.bodyLarge.fontSize),
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = { if (isSendEnabled) onSendClicked() }
                    )
                )

                FilledIconButton(
                    onClick = onSendClicked,
                    enabled = isSendEnabled
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "메시지 전송"
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceWaveform(
    amplitude: Float,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "waveform")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveform-phase"
    )
    val barCount = 22
    val color = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(barCount) { index ->
            val wave = (kotlin.math.sin(phase + index * 0.55f) + 1f) / 2f
            val intensity = (0.15f + amplitude.coerceIn(0f, 1f) * 0.85f) * wave
            val heightDp = (4 + (intensity * 36f)).dp
            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = heightDp)
                    .background(color = color, shape = RoundedCornerShape(2.dp))
            )
        }
    }
}

