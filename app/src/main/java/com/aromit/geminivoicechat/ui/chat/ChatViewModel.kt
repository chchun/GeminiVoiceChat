package com.aromit.geminivoicechat.ui.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aromit.geminivoicechat.a2ui.A2UISurface
import com.aromit.geminivoicechat.a2ui.A2UIScenarios
import com.aromit.geminivoicechat.a2ui.deepCopyModel
import com.aromit.geminivoicechat.a2ui.setAtPath
import com.aromit.geminivoicechat.domain.model.ChatMessage
import com.aromit.geminivoicechat.domain.model.SenderType
import com.aromit.geminivoicechat.domain.repository.AiRepository
import com.aromit.geminivoicechat.ui.voice.VoiceController
import com.aromit.geminivoicechat.ui.voice.VoiceEvent
import com.aromit.geminivoicechat.ui.voice.VoiceMode
import com.aromit.geminivoicechat.ui.voice.VoiceState
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

    init {
        viewModelScope.launch {
            voiceController.events.collect { event -> handleVoiceEvent(event) }
        }
    }

    // ---------- 텍스트 채팅 ----------

    fun onInputChanged(value: String) {
        _state.update { it.copy(inputText = value) }
    }

    fun onSendClicked() {
        val current = _state.value
        if (!current.isSendEnabled) return
        val prompt = current.inputText.trim()
        _state.update { it.copy(inputText = "") }
        submitUserPrompt(prompt)
    }

    // ---------- 인라인 음성 입력 (한 번만 받아쓰기) ----------

    fun startVoiceMode() {
        if (_state.value.voice.isActive) return
        Log.d(TAG, "startVoiceMode")
        // 메시지 TTS 재생 중이면 중단
        if (_state.value.playingMessageId != null) {
            voiceController.stopSpeaking()
            _state.update { it.copy(playingMessageId = null) }
        }
        _state.update {
            it.copy(
                voice = VoiceState(
                    isActive = true,
                    mode = VoiceMode.LISTENING
                )
            )
        }
        voiceController.startListening()
    }

    fun endVoiceMode() {
        Log.d(TAG, "endVoiceMode")
        voiceController.stopListening()
        _state.update { it.copy(voice = VoiceState()) }
    }

    // ---------- 메시지별 TTS 토글 ----------

    fun onMessageTtsToggled(message: ChatMessage) {
        if (message.text.isBlank()) return
        val current = _state.value
        // 동일 메시지 재생 중 → 정지
        if (current.playingMessageId == message.id) {
            Log.d(TAG, "TTS toggle off: ${message.id}")
            voiceController.stopSpeaking()
            _state.update { it.copy(playingMessageId = null) }
            return
        }
        // 다른 재생 또는 음성 입력 중이면 정리
        if (current.voice.isActive) {
            voiceController.stopListening()
            _state.update { it.copy(voice = VoiceState()) }
        }
        voiceController.stopSpeaking()
        Log.d(TAG, "TTS play: ${message.id}")
        _state.update { it.copy(playingMessageId = message.id) }
        voiceController.speak(message.text)
    }

    // ---------- VoiceEvent 처리 ----------

    private fun handleVoiceEvent(event: VoiceEvent) {
        when (event) {
            is VoiceEvent.Rms -> {
                if (!_state.value.voice.isActive) return
                val normalized = ((event.value + 2f) / 12f).coerceIn(0f, 1f)
                _state.update { it.copy(voice = it.voice.copy(amplitude = normalized)) }
            }
            is VoiceEvent.Partial -> {
                if (!_state.value.voice.isActive) return
                _state.update { it.copy(voice = it.voice.copy(partialText = event.text)) }
            }
            is VoiceEvent.Final -> {
                if (!_state.value.voice.isActive) return
                Log.d(TAG, "Final recognized: \"${event.text}\"")
                onSpeechRecognized(event.text)
            }
            is VoiceEvent.EndOfSpeech -> {
                if (!_state.value.voice.isActive) return
                _state.update {
                    it.copy(voice = it.voice.copy(mode = VoiceMode.THINKING, amplitude = 0f))
                }
            }
            is VoiceEvent.SpeakingStarted -> {
                Log.d(TAG, "SpeakingStarted")
            }
            is VoiceEvent.SpeakingFinished -> {
                Log.d(TAG, "SpeakingFinished")
                _state.update { it.copy(playingMessageId = null) }
            }
            is VoiceEvent.Error -> {
                Log.w(TAG, "Voice error: ${event.message}")
                _state.update {
                    it.copy(
                        voice = VoiceState(errorMessage = event.message),
                        playingMessageId = null
                    )
                }
                voiceController.stopListening()
            }
            VoiceEvent.ReadyForSpeech,
            VoiceEvent.BeginningOfSpeech -> {
                if (!_state.value.voice.isActive) return
                _state.update { it.copy(voice = it.voice.copy(mode = VoiceMode.LISTENING)) }
            }
        }
    }

    private fun onSpeechRecognized(text: String) {
        val cleaned = text.trim()
        // 인식 결과 수신 시 즉시 음성 모드 종료 (한 번만 받아쓰기)
        _state.update { it.copy(voice = VoiceState()) }
        if (cleaned.isBlank()) return
        submitUserPrompt(cleaned)
    }

    // ---------- 공통 송신 흐름 ----------

    private fun submitUserPrompt(prompt: String) {
        val userMessage = ChatMessage(text = prompt, senderType = SenderType.USER)

        // A2UI 키워드 매칭 — 매칭되면 로컬 시나리오 응답
        val scenario = A2UIScenarios.match(prompt)
        if (scenario != null) {
            val surface = scenario.surface()
            val aiMsg = ChatMessage(
                text = scenario.replyMd,
                senderType = SenderType.AI,
                isStreaming = false,
                surfaceId = surface.surfaceId,
            )
            _state.update {
                it.copy(
                    messages = it.messages + userMessage + aiMsg,
                    surfaces = it.surfaces + (surface.surfaceId to surface),
                )
            }
            return
        }

        // 일반 메시지 → 서버 스트리밍
        val aiPlaceholder = ChatMessage(text = "", senderType = SenderType.AI, isStreaming = true)
        _state.update {
            it.copy(
                messages = it.messages + userMessage + aiPlaceholder,
                isAiResponding = true,
            )
        }
        viewModelScope.launch {
            streamAiResponse(prompt, aiPlaceholder.id)
        }
    }

    private suspend fun streamAiResponse(prompt: String, aiMessageId: String) {
        runCatching {
            aiRepository.sendMessage(prompt).collect { chunk ->
                appendChunkToMessage(aiMessageId, chunk)
            }
        }
        finalizeMessage(aiMessageId)
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
                isAiResponding = false,
            )
        }
    }

    // ---------- A2UI 이벤트 처리 ----------

    fun onA2UIData(surfaceId: String, path: String, value: Any?) {
        _state.update { state ->
            val surface = state.surfaces[surfaceId] ?: return@update state
            val newModel = deepCopyModel(surface.dataModel)
            setAtPath(newModel, path, value)
            val newSurface = surface.copy(dataModel = newModel)
            state.copy(surfaces = state.surfaces + (surfaceId to newSurface))
        }
    }

    fun onA2UIAction(surfaceId: String, eventName: String, context: Map<String, Any?>) {
        Log.i(TAG, "A2UI action: $eventName  surface=$surfaceId  ctx=$context")
        when (eventName) {
            "create_incident" -> {
                val id = 1000 + (Math.random() * 9000).toInt()
                val service = context["service"]?.toString() ?: "-"
                val severity = context["severity"]?.toString() ?: "-"
                val summary = context["summary"]?.toString() ?: ""
                val impact = context["customerImpact"] as? Boolean ?: false
                addAiReply(
                    "✅ **인시던트가 생성되었습니다.** `INC-$id`\n\n" +
                    "- 서비스: `$service`\n- 심각도: **$severity**\n- 고객 영향: ${if (impact) "있음" else "없음"}\n\n> $summary\n\n온콜 담당자에게 페이지를 발송했어요."
                )
            }
            "start_deploy" -> {
                val canary = context["canary"]?.toString() ?: "?"
                val service = context["service"]?.toString() ?: "서비스"
                val version = context["version"]?.toString() ?: ""
                addAiReply(
                    "🚀 **배포를 시작했습니다.** `$service $version`\n\n" +
                    "카나리 트래픽 **${canary}%** 로 롤아웃 중입니다. p99 와 에러율을 모니터링하다가 임계값 초과 시 자동 롤백돼요."
                )
            }
            "cancel_deploy" -> {
                addAiReply("배포를 취소했어요. 변경 사항은 적용되지 않았습니다.")
            }
            "refresh_status" -> {
                val range = context["range"]?.toString() ?: "1h"
                addAiReply("🔄 `$range` 기준으로 지표를 다시 조회했어요. 위 카드의 값이 갱신되었습니다.")
            }
            "generate_risk" -> {
                @Suppress("UNCHECKED_CAST")
                val trades = (context["trades"] as? List<String>) ?: emptyList()
                val count = context["count"]?.toString()?.toIntOrNull() ?: 5
                val location = context["location"]?.toString() ?: "일반"
                if (trades.isEmpty()) {
                    addAiReply("공종을 1개 이상 선택해 주세요.")
                    return
                }
                val resultSurface = A2UIScenarios.buildRiskResultSurface(trades, count)
                val replyText = "**${trades.size}개 공종** (${trades.joinToString(", ")}) · " +
                    "${location} · ${count}개/공종 기준으로 위험성평가를 생성했어요. 검토 후 수정하여 활용하세요."
                addAiReplyWithSurface(replyText, resultSurface)
            }
            else -> addAiReply("`$eventName` 액션을 접수했어요.")
        }
    }

    private fun addAiReply(text: String) {
        _state.update {
            it.copy(messages = it.messages + ChatMessage(text = text, senderType = SenderType.AI))
        }
    }

    private fun addAiReplyWithSurface(text: String, surface: A2UISurface) {
        val msg = ChatMessage(text = text, senderType = SenderType.AI, surfaceId = surface.surfaceId)
        _state.update {
            it.copy(
                messages = it.messages + msg,
                surfaces = it.surfaces + (surface.surfaceId to surface),
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
