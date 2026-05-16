package com.aromit.geminivoicechat.ui.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aromit.geminivoicechat.domain.model.ChatMessage
import com.aromit.geminivoicechat.domain.model.SenderType
import com.aromit.geminivoicechat.domain.repository.AiRepository
import com.aromit.geminivoicechat.ui.voice.VoiceController
import com.aromit.geminivoicechat.ui.voice.VoiceEvent
import com.aromit.geminivoicechat.ui.voice.VoiceMode
import com.aromit.geminivoicechat.ui.voice.VoiceState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(
    private val aiRepository: AiRepository,
    private val voiceController: VoiceController
) : ViewModel() {

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private var voiceEventsJob: Job? = null

    // ---------- 텍스트 채팅 ----------

    fun onInputChanged(value: String) {
        _state.update { it.copy(inputText = value) }
    }

    fun onSendClicked() {
        val current = _state.value
        if (!current.isSendEnabled) return
        val prompt = current.inputText.trim()
        _state.update { it.copy(inputText = "") }
        submitUserPrompt(prompt, speakResult = false)
    }

    // ---------- 보이스 모드 ----------

    fun startVoiceMode() {
        if (_state.value.voice.isActive) return
        Log.d(TAG, "startVoiceMode")
        _state.update {
            it.copy(
                voice = VoiceState(
                    isActive = true,
                    mode = VoiceMode.LISTENING
                )
            )
        }
        collectVoiceEvents()
        voiceController.startListening()
    }

    fun endVoiceMode() {
        Log.d(TAG, "endVoiceMode")
        voiceController.stopListening()
        voiceController.stopSpeaking()
        voiceEventsJob?.cancel()
        voiceEventsJob = null
        _state.update { it.copy(voice = VoiceState()) }
    }

    private fun collectVoiceEvents() {
        voiceEventsJob?.cancel()
        voiceEventsJob = viewModelScope.launch {
            voiceController.events.collect { event -> handleVoiceEvent(event) }
        }
    }

    private fun handleVoiceEvent(event: VoiceEvent) {
        if (!_state.value.voice.isActive) return
        when (event) {
            is VoiceEvent.Rms -> {
                // 일반적으로 SpeechRecognizer rmsdB 는 약 -2 ~ 10 범위. 0..1로 정규화.
                val normalized = ((event.value + 2f) / 12f).coerceIn(0f, 1f)
                _state.update { it.copy(voice = it.voice.copy(amplitude = normalized)) }
            }
            is VoiceEvent.Partial -> {
                _state.update { it.copy(voice = it.voice.copy(partialText = event.text)) }
            }
            is VoiceEvent.Final -> {
                Log.d(TAG, "Final recognized text: \"${event.text}\"")
                onSpeechRecognized(event.text)
            }
            is VoiceEvent.EndOfSpeech -> {
                Log.d(TAG, "EndOfSpeech -> THINKING")
                _state.update {
                    it.copy(voice = it.voice.copy(mode = VoiceMode.THINKING, amplitude = 0f))
                }
            }
            is VoiceEvent.SpeakingStarted -> {
                Log.d(TAG, "SpeakingStarted -> SPEAKING")
                _state.update { it.copy(voice = it.voice.copy(mode = VoiceMode.SPEAKING)) }
            }
            is VoiceEvent.SpeakingFinished -> {
                Log.d(TAG, "SpeakingFinished -> resume listening")
                resumeListeningIfActive()
            }
            is VoiceEvent.Error -> {
                Log.w(TAG, "Voice error: ${event.message}")
                _state.update {
                    it.copy(voice = it.voice.copy(errorMessage = event.message, isActive = false))
                }
                voiceController.stopListening()
                voiceEventsJob?.cancel()
                voiceEventsJob = null
            }
            VoiceEvent.ReadyForSpeech,
            VoiceEvent.BeginningOfSpeech -> {
                _state.update {
                    it.copy(voice = it.voice.copy(mode = VoiceMode.LISTENING))
                }
            }
        }
    }

    private fun onSpeechRecognized(text: String) {
        val cleaned = text.trim()
        if (cleaned.isBlank()) {
            // 인식 실패 → 다시 듣기
            resumeListeningIfActive()
            return
        }
        _state.update { it.copy(voice = it.voice.copy(partialText = "")) }
        submitUserPrompt(cleaned, speakResult = true)
    }

    private fun resumeListeningIfActive() {
        if (!_state.value.voice.isActive) return
        _state.update {
            it.copy(
                voice = it.voice.copy(
                    mode = VoiceMode.LISTENING,
                    amplitude = 0f,
                    partialText = ""
                )
            )
        }
        voiceController.startListening()
    }

    // ---------- 공통 송신 흐름 ----------

    private fun submitUserPrompt(prompt: String, speakResult: Boolean) {
        val userMessage = ChatMessage(text = prompt, senderType = SenderType.USER)
        val aiPlaceholder = ChatMessage(
            text = "",
            senderType = SenderType.AI,
            isStreaming = true
        )
        _state.update {
            it.copy(
                messages = it.messages + userMessage + aiPlaceholder,
                isAiResponding = true
            )
        }
        viewModelScope.launch {
            val fullText = streamAiResponse(prompt, aiPlaceholder.id)
            if (speakResult && fullText.isNotBlank() && _state.value.voice.isActive) {
                voiceController.speak(fullText)
            } else if (speakResult && _state.value.voice.isActive) {
                // 응답이 비었을 경우에도 다시 듣기로 복귀
                resumeListeningIfActive()
            }
        }
    }

    private suspend fun streamAiResponse(prompt: String, aiMessageId: String): String {
        val builder = StringBuilder()
        runCatching {
            aiRepository.sendMessage(prompt).collect { chunk ->
                builder.append(chunk)
                appendChunkToMessage(aiMessageId, chunk)
            }
        }
        finalizeMessage(aiMessageId)
        return builder.toString()
    }

    private fun appendChunkToMessage(messageId: String, chunk: String) {
        _state.update { current ->
            current.copy(
                messages = current.messages.map { msg ->
                    if (msg.id == messageId) msg.copy(text = msg.text + chunk) else msg
                }
            )
        }
    }

    private fun finalizeMessage(messageId: String) {
        _state.update { current ->
            current.copy(
                messages = current.messages.map { msg ->
                    if (msg.id == messageId) msg.copy(isStreaming = false) else msg
                },
                isAiResponding = false
            )
        }
    }

    override fun onCleared() {
        voiceController.stopListening()
        voiceController.stopSpeaking()
        super.onCleared()
    }

    companion object {
        private const val TAG = "ChatViewModel"
    }

    class Factory(
        private val aiRepository: AiRepository,
        private val voiceController: VoiceController
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ChatViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }
            return ChatViewModel(aiRepository, voiceController) as T
        }
    }
}
