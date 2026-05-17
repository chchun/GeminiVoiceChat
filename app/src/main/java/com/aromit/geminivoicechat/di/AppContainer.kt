package com.aromit.geminivoicechat.di

import android.content.Context
import com.aromit.geminivoicechat.BuildConfig
import com.aromit.geminivoicechat.data.repository.MockAiRepository
import com.aromit.geminivoicechat.data.repository.RemoteAiRepository
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
    override val aiRepository: AiRepository by lazy {
        if (BuildConfig.USE_REMOTE) {
            RemoteAiRepository(
                serverUrl = BuildConfig.SERVER_URL,
                apiKey = BuildConfig.WS_API_KEY,
            )
        } else {
            MockAiRepository()
        }
    }

    override val voiceController: VoiceController by lazy { AndroidVoiceController(appContext) }
}
