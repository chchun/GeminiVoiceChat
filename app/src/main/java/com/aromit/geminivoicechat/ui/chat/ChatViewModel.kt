package com.aromit.geminivoicechat.ui.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aromit.geminivoicechat.a2ui.A2UIComponent
import com.aromit.geminivoicechat.a2ui.A2UIEnvelopeApplier
import com.aromit.geminivoicechat.a2ui.A2UIPickedFile
import com.aromit.geminivoicechat.a2ui.A2UISurface
import com.aromit.geminivoicechat.a2ui.A2UIScenarios
import com.aromit.geminivoicechat.a2ui.A2UIValue
import com.aromit.geminivoicechat.a2ui.deepCopyModel
import com.aromit.geminivoicechat.a2ui.setAtPath
import com.aromit.geminivoicechat.domain.model.A2UIFile
import com.aromit.geminivoicechat.domain.model.AiStreamEvent
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
            aiRepository.sendMessage(prompt).collect { event ->
                when (event) {
                    is AiStreamEvent.TextChunk -> appendChunkToMessage(aiMessageId, event.text)
                    is AiStreamEvent.A2UIEnvelope -> applyEnvelope(aiMessageId, event.envelopeJson)
                }
            }
        }
        finalizeMessage(aiMessageId)
    }

    /** v0.9 엔벨로프 1건을 서피스에 누적 적용하고, 첫 서피스를 메시지에 부착한다. */
    private fun applyEnvelope(messageId: String, envelopeJson: String) {
        val surfaceId = A2UIEnvelopeApplier.surfaceIdOf(envelopeJson) ?: return
        _state.update { current ->
            val updated = A2UIEnvelopeApplier.apply(current.surfaces[surfaceId], envelopeJson)
                ?: return@update current
            current.copy(
                messages = current.messages.map { msg ->
                    if (msg.id == messageId && msg.surfaceId == null) {
                        msg.copy(surfaceId = surfaceId)
                    } else {
                        msg
                    }
                },
                surfaces = current.surfaces + (surfaceId to updated),
            )
        }
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
            // 서버 폼(아차사고/안전제안) 제출 — /a2ui/action 서버 왕복 (spec 002)
            "register_safety_report" -> submitActionToServer(surfaceId, eventName, context)

            else -> addAiReply("`$eventName` 액션을 접수했어요.")
        }
    }

    private fun addAiReply(text: String) {
        _state.update {
            it.copy(messages = it.messages + ChatMessage(text = text, senderType = SenderType.AI))
        }
    }

    /**
     * A2UI 액션을 서버로 전송하고 응답 md 를 표시한다 (spec 002 D4).
     * 성공 시 해당 서피스의 제출 버튼을 완료 텍스트로 치환해 중복 등록을 막는다 (D3).
     */
    private fun submitActionToServer(
        surfaceId: String,
        eventName: String,
        context: Map<String, Any?>,
    ) {
        val placeholder = ChatMessage(text = "", senderType = SenderType.AI, isStreaming = true)
        _state.update { it.copy(messages = it.messages + placeholder) }

        // 첨부 파일(A2UIPickedFile)을 context 에서 분리 — JSON 에는 파일명만, bytes 는
        // 도메인 A2UIFile 로 multipart files 파트에 실린다 (spec 002 D5)
        val files = mutableListOf<A2UIFile>()
        val sanitizedContext = context.mapValues { (_, value) ->
            if (value is List<*> && value.any { it is A2UIPickedFile }) {
                val pickedFiles = value.filterIsInstance<A2UIPickedFile>()
                files += pickedFiles.map { A2UIFile(it.name, it.mimeType, it.bytes) }
                pickedFiles.map { mapOf("name" to it.name) }
            } else {
                value
            }
        }

        viewModelScope.launch {
            runCatching {
                aiRepository.sendA2UIAction(eventName, surfaceId, sanitizedContext, files)
            }.onSuccess { result ->
                appendChunkToMessage(placeholder.id, result.markdown)
                if (result.ok) markActionCompleted(surfaceId, eventName)
            }.onFailure { e ->
                Log.w(TAG, "A2UI action 전송 실패: $eventName", e)
                appendChunkToMessage(
                    placeholder.id,
                    "⚠️ 등록 요청을 보내지 못했어요 (${e.message}). 폼의 등록 버튼으로 다시 시도해 주세요.",
                )
            }
            finalizeMessage(placeholder.id)
        }
    }

    /** 서피스에서 같은 eventName 의 제출 버튼을 찾아 완료 텍스트로 치환한다 (spec 002 D3). */
    private fun markActionCompleted(surfaceId: String, eventName: String) {
        _state.update { current ->
            val surface = current.surfaces[surfaceId] ?: return@update current
            val components = surface.components.mapValues { (_, comp) ->
                if (comp is A2UIComponent.ButtonComp && comp.action?.eventName == eventName) {
                    A2UIComponent.TextComp(
                        id = comp.id,
                        text = A2UIValue.Static("✅ 등록이 완료되었습니다."),
                        variant = "caption",
                    )
                } else {
                    comp
                }
            }
            current.copy(
                surfaces = current.surfaces + (surfaceId to surface.copy(components = components)),
            )
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
