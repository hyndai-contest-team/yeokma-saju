package com.yeokma.saju.input

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.os.Looper
import android.util.Log
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
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
import java.util.Locale

/**
 * 음성 인식: Pleos SpeechToText SDK의 ON_DEVICE 모드(차 안의 인식 엔진. 앱 등록 키가 필요 없다).
 * Pleos가 아닌 기기에서는 안드로이드 기본 인식기(SpeechRecognizer)로 받고, 그것도 없으면 available=false라 키보드로만 받는다.
 */
class Voice(context: Context) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val stt = SpeechToText(app, Mode.ON_DEVICE)
    private var ready = false
    private var pendingStart = false
    private var delivered = false
    private var released = false
    private var sys: SpeechRecognizer? = null
    private var useAndroid = false

    var available by mutableStateOf(true); private set
    var listening by mutableStateOf(false); private set
    var partial by mutableStateOf(""); private set
    var onFinal: (String) -> Unit = {}
    var onFail: () -> Unit = {}

    private val listener = object : ResultListener {
        override fun onReady() = Unit
        override fun onStartedRecognition() = ui { listening = true }
        override fun onUpdated(stt: String, completed: Boolean) = ui {
            if (delivered) return@ui
            partial = stt
            if (completed) finish(stt)
        }
        override fun onUpdatedEpdData(on: Long, off: Long) = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        // 끝났는데 최종 결과가 안 왔으면 마지막 중간 결과를 쓴다
        override fun onEndedRecognition() = ui { main.postDelayed({ if (!delivered && listening) finish(partial) }, 700) }
        override fun onError(errorType: ErrorType) = ui {
            Log.w(TAG, "stt error $errorType")
            if (!delivered) { delivered = true; listening = false; onFail() }
        }
    }

    /** Pleos 인식기를 못 쓸 때: 안드로이드 기본 인식기가 있으면 그것으로, 없으면 키보드만 */
    private fun fallback() {
        if (SpeechRecognizer.isRecognitionAvailable(app)) {
            useAndroid = true; ready = true
            if (pendingStart) { pendingStart = false; start() }
            return
        }
        available = false
        if (pendingStart) { pendingStart = false; listening = false; onFail() }
    }

    init {
        // Pleos가 아닌 기기에서는 SDK가 권한 예외를 던진다
        runCatching { stt.initialize(object : OnInitializeListener {
            override fun onSuccess() = ui {
                if (released) return@ui
                stt.setLocale(Locale.KOREA)
                stt.addListener(listener)
                ready = true
                if (pendingStart) { pendingStart = false; start() }
            }
            override fun onFailure(errorType: ErrorType) = ui {
                Log.w(TAG, "stt init failed $errorType")
                fallback()
            }
        }) }.onFailure { Log.w(TAG, "pleos stt unavailable", it); fallback() }
    }

    private fun ui(block: () -> Unit) { if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block) }

    private fun finish(text: String) {
        if (delivered) return
        delivered = true
        listening = false
        val t = text.trim()
        if (t.isEmpty()) onFail() else onFinal(t)
    }

    fun hasPermission() = ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun start() {
        if (!available || released) return
        partial = ""
        delivered = false
        listening = true
        if (!ready) { pendingStart = true; return }
        if (useAndroid) { startAndroid(); return }
        runCatching { stt.request() }.onFailure { Log.w(TAG, "request failed", it); delivered = true; listening = false; onFail() }
    }

    fun stop() {
        pendingStart = false
        if (listening) { delivered = true; listening = false; if (ready) runCatching { if (useAndroid) sys?.cancel() else stt.stop() } }
    }

    private fun startAndroid() {
        sys?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(app)
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { listening = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) { Log.w(TAG, "android stt error $error"); if (!delivered) { delivered = true; listening = false; onFail() } }
            override fun onResults(results: Bundle?) {
                finish(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty())
            }
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { if (!delivered) partial = it }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // 고민처럼 긴 말은 중간에 쉬어도 끊지 않게 조금 더 기다린다
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        })
        sys = r
    }

    fun release() {
        stop()
        released = true
        main.removeCallbacksAndMessages(null)
        runCatching { sys?.destroy(); sys = null }
        if (!useAndroid) runCatching { stt.removeListener(listener); stt.release() }
    }

    companion object { private const val TAG = "Voice" }
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
    DisposableEffect(voice) { onDispose { voice.release() } }
    val startWithPermission = { if (voice.hasPermission()) voice.start() else ask.launch(Manifest.permission.RECORD_AUDIO) }
    return voice to startWithPermission
}
