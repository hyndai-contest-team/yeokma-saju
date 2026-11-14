package com.yeokma.saju.input

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * 안드로이드 음성 인식. 기기에 인식기가 없으면 available=false이고 화면은 키보드로만 받는다.
 * (Gleo AI STT로 바꿀 자리: start/stop과 콜백만 맞추면 된다)
 */
class Voice(private val context: Context) {
    val available = SpeechRecognizer.isRecognitionAvailable(context)
    var listening by mutableStateOf(false); private set
    var partial by mutableStateOf(""); private set
    private var recognizer: SpeechRecognizer? = null
    var onFinal: (String) -> Unit = {}
    var onFail: () -> Unit = {}

    fun hasPermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun start() {
        if (!available) return
        stop()
        partial = ""
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { listening = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) { listening = false; onFail() }
            override fun onResults(results: Bundle?) {
                listening = false
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isBlank()) onFail() else onFinal(text)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { partial = it }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        })
        recognizer = r
        listening = true
    }

    fun stop() {
        recognizer?.destroy(); recognizer = null; listening = false
    }
}

@Composable
fun rememberVoice(onFinal: (String) -> Unit, onFail: () -> Unit): Pair<Voice, () -> Unit> {
    val context = LocalContext.current
    val voice = remember { Voice(context) }
    val f by rememberUpdatedState(onFinal)
    val e by rememberUpdatedState(onFail)
    voice.onFinal = { f(it) }
    voice.onFail = { e() }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) voice.start() }
    DisposableEffect(voice) { onDispose { voice.stop() } }
    val startWithPermission = { if (voice.hasPermission()) voice.start() else ask.launch(Manifest.permission.RECORD_AUDIO) }
    return voice to startWithPermission
}
