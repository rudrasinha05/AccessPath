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

/**
 * Reads directions aloud with the phone's text-to-speech engine.
 * The engine is only started the first time someone taps "Read aloud", so opening or leaving
 * the Navigate tab never binds or unbinds the speech service on the main thread.
 */
class Speaker(context: Context) {
    private val appContext = context.applicationContext
    var speaking by mutableStateOf(false); private set
    private var tts: TextToSpeech? = null
    private var ready = false
    private var failed = false
    private var pending: Pair<List<String>, Locale>? = null

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) { speaking = true }
        override fun onDone(utteranceId: String?) { if (utteranceId == LAST) speaking = false }
        @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) { speaking = false }
        override fun onError(utteranceId: String?, errorCode: Int) { speaking = false }
    }

    /** Returns false if speech is known to be impossible on this device. */
    fun speak(lines: List<String>, locale: Locale): Boolean {
        if (failed || lines.isEmpty()) return false
        val engine = tts
        if (engine == null) {
            pending = lines to locale
            speaking = true
            tts = TextToSpeech(appContext) { status ->
                ready = status == TextToSpeech.SUCCESS
                if (!ready) { failed = true; speaking = false; return@TextToSpeech }
                tts?.setOnUtteranceProgressListener(listener)
                pending?.let { (l, loc) -> pending = null; say(l, loc) }
            }
            return true
        }
        if (!ready) { pending = lines to locale; return true }
        return say(lines, locale)
    }

    private fun say(lines: List<String>, locale: Locale): Boolean {
        val engine = tts ?: return false
        val result = engine.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            if (engine.setLanguage(Locale.ENGLISH) < TextToSpeech.LANG_AVAILABLE) { speaking = false; return false }
        }
        engine.stop()
        lines.forEachIndexed { i, line ->
            engine.speak(line, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, if (i == lines.lastIndex) LAST else "line$i")
        }
        speaking = true
        return true
    }

    fun stop() { pending = null; tts?.stop(); speaking = false }
    fun shutdown() { pending = null; tts?.stop(); tts?.shutdown(); tts = null; ready = false }

    private companion object { const val LAST = "last" }
}

/** One speaker for the whole app, so switching tabs does not restart the speech engine. */
@Composable
fun rememberSpeaker(): Speaker {
    val context = LocalContext.current
    val speaker = remember { Speaker(context) }
    DisposableEffect(speaker) { onDispose { speaker.shutdown() } }
    return speaker
}
