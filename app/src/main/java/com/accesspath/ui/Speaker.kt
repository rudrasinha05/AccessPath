package com.accesspath.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/** Reads directions aloud with the phone's text-to-speech engine, in the app's current language. */
class Speaker(context: Context) {
    var available by mutableStateOf(false); private set
    var speaking by mutableStateOf(false); private set
    private var ready = false

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        available = ready
    }.apply {
        setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { speaking = true }
            override fun onDone(utteranceId: String?) { if (utteranceId == LAST) speaking = false }
            @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) { speaking = false }
            override fun onError(utteranceId: String?, errorCode: Int) { speaking = false }
        })
    }

    /** Returns false if speech is not possible on this device. */
    fun speak(lines: List<String>, locale: Locale): Boolean {
        if (!ready || lines.isEmpty()) return false
        val result = tts.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            if (tts.setLanguage(Locale.ENGLISH) < TextToSpeech.LANG_AVAILABLE) return false
        }
        tts.stop()
        lines.forEachIndexed { i, line ->
            val id = if (i == lines.lastIndex) LAST else "line$i"
            tts.speak(line, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, id)
        }
        speaking = true
        return true
    }

    fun stop() { tts.stop(); speaking = false }
    fun shutdown() { tts.stop(); tts.shutdown() }

    private companion object { const val LAST = "last" }
}

@Composable
fun rememberSpeaker(): Speaker {
    val context = LocalContext.current
    val speaker = remember { Speaker(context) }
    DisposableEffect(speaker) { onDispose { speaker.shutdown() } }
    return speaker
}
