package com.aromit.geminivoicechat.data.repository

import com.aromit.geminivoicechat.domain.model.AiResponse
import com.aromit.geminivoicechat.domain.repository.AiRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers

class MockAiRepository : AiRepository {

    private val cannedResponses = listOf(
        "안녕하세요! 저는 Gemini 스타일의 데모용 AI 어시스턴트예요. 무엇을 도와드릴까요?",
        "좋은 질문이네요. 잠시 생각해볼게요... 아, 이렇게 답변드리는 게 좋겠어요. 지금은 Mock 데이터로 동작 중이라 실제 추론은 하지 않지만, 스트리밍 UX는 진짜처럼 흘러갑니다.",
        "Jetpack Compose와 Kotlin Flow의 조합은 정말 강력해요. 한 글자씩 emit하는 것만으로도 타이핑 효과가 자연스럽게 표현됩니다.",
        "현재는 가짜 응답을 반환하고 있어요. 나중에 RemoteAiRepository로 바인딩만 바꾸면 동일한 UI에서 실제 서버 응답을 받게 됩니다.",
        "메시지를 잘 받았습니다. 이렇게 단어 단위로 흘려보내면 사용자는 AI가 '생각하면서 말하는' 듯한 느낌을 받게 되죠."
    )

    private var responseIndex = 0

    override suspend fun sendMessage(text: String): Flow<String> = flow {
        delay(INITIAL_THINKING_DELAY_MS)

        val response = cannedResponses[responseIndex % cannedResponses.size]
        responseIndex++

        val words = response.split(" ")
        for ((index, word) in words.withIndex()) {
            val chunk = if (index == 0) word else " $word"
            emit(chunk)
            delay(PER_WORD_DELAY_MS)
        }
    }.flowOn(Dispatchers.IO)

    override fun streamAudio(audioFlow: Flow<ByteArray>): Flow<AiResponse> = flow {
        // 보이스 모드는 후속 단계에서 구현합니다.
        // 현재는 텍스트 채팅 흐름에서만 사용하지 않으므로 빈 Flow를 반환합니다.
    }

    companion object {
        private const val INITIAL_THINKING_DELAY_MS = 800L
        private const val PER_WORD_DELAY_MS = 80L
    }
}
