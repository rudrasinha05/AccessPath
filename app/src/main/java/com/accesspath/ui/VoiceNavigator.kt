package com.accesspath.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class VoiceNavigator(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var initialized = false
    private var currentLang = "en"

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            initialized = true
            applyLanguage()
        }
    }

    fun setLanguage(lang: String) {
        currentLang = lang
        applyLanguage()
    }

    private fun applyLanguage() {
        if (!initialized) return
        val locale = if (currentLang == "hi") Locale("hi", "IN") else Locale.US
        val result = tts?.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts?.language = Locale.US
        }
    }

    fun speak(text: String) {
        if (!initialized || text.isBlank()) return
        tts?.stop()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "accesspath_speech")
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        initialized = false
    }
}
