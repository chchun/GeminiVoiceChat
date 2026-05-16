package com.aromit.geminivoicechat

import android.app.Application
import com.aromit.geminivoicechat.di.AppContainer
import com.aromit.geminivoicechat.di.DefaultAppContainer

class GeminiVoiceChatApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(applicationContext)
    }

    override fun onTerminate() {
        container.voiceController.release()
        super.onTerminate()
    }
}
