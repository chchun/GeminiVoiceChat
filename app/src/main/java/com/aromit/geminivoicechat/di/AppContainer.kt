package com.aromit.geminivoicechat.di

import android.content.Context
import com.aromit.geminivoicechat.data.repository.MockAiRepository
import com.aromit.geminivoicechat.domain.repository.AiRepository
import com.aromit.geminivoicechat.ui.voice.AndroidVoiceController
import com.aromit.geminivoicechat.ui.voice.VoiceController

interface AppContainer {
    val aiRepository: AiRepository
    val voiceController: VoiceController
}

class DefaultAppContainer(
    private val appContext: Context
) : AppContainer {
    // 향후 RemoteAiRepository로 교체 시 이 한 줄만 바꾸면 UI 코드는 그대로 유지됩니다.
    override val aiRepository: AiRepository by lazy { MockAiRepository() }

    override val voiceController: VoiceController by lazy { AndroidVoiceController(appContext) }
}
