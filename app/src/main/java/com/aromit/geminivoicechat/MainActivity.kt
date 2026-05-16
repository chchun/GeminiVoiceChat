package com.aromit.geminivoicechat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.aromit.geminivoicechat.ui.chat.ChatScreen
import com.aromit.geminivoicechat.ui.chat.ChatViewModel
import com.aromit.geminivoicechat.ui.theme.GeminiVoiceChatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as GeminiVoiceChatApp
        val viewModelFactory = ChatViewModel.Factory(
            aiRepository = app.container.aiRepository,
            voiceController = app.container.voiceController
        )

        setContent {
            GeminiVoiceChatTheme {
                ChatScreen(factory = viewModelFactory)
            }
        }
    }
}
