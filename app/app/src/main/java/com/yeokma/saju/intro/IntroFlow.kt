package com.yeokma.saju.intro

import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeokma.saju.car.CarMood
import com.yeokma.saju.car.LocalCarMood
import com.yeokma.saju.ui.Paper
import kotlinx.coroutines.delay
import java.util.Locale

enum class IntroPhase { Prelude, Idle, Boarding }

/** V3가 없을 때만 TTS로 대신 말하는 인사. V3 영상 안의 대사와 같다 */
private const val BOARDING_LINE = "오셨군요"

/**
 * 인트로: V1 한 번 → V2 대기 루프(버튼 누를 때까지) → 버튼 → V3 탑승 → onDone.
 * V2 동안은 차도 같이 연출한다: 앰비언트를 무드등 색으로, 실내등 켜기, 약한 송풍.
 * V1 동안은 글씨 없이 영상만 보여 주고, 오른쪽 아래 '건너뛰기'만 둔다.
 */
@Composable
fun IntroFlow(onDone: () -> Unit) {
    var phase by rememberSaveable { mutableStateOf(IntroPhase.Prelude) }
    val v1 = IntroMedia.v1()

    Box(Modifier.fillMaxSize()) {
        Crossfade(targetState = phase, animationSpec = tween(if (phase == IntroPhase.Idle) 0 else 600), label = "intro") { p ->
            when (p) {
                IntroPhase.Prelude ->
                    if (v1 != null) VideoLayer(resId = v1, loop = false, onEnded = { phase = IntroPhase.Idle })
                    else LaunchedEffect(Unit) { phase = IntroPhase.Idle }
                IntroPhase.Idle -> {
                    val car = LocalCarMood.current
                    LaunchedEffect(Unit) { runCatching { car?.idle() } }
                    IdleLoop(onStart = { phase = IntroPhase.Boarding })
                }
                IntroPhase.Boarding -> Boarding(onDone = onDone)
            }
        }
        if (phase == IntroPhase.Prelude) {
            OutlinedButton(
                onClick = { phase = IntroPhase.Idle },
                border = BorderStroke(1.dp, Paper.copy(alpha = .3f)),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0x7307080F), contentColor = Paper),
                modifier = Modifier.align(Alignment.BottomEnd).padding(28.dp).height(40.dp),
            ) { Text("건너뛰기", fontSize = 17.sp) }
        }
    }
}

/** V2: V1 마지막 장면에서 4초 무한 반복. 없으면 그 장면을 정지 화면으로 둔다. */
@Composable
private fun IdleLoop(onStart: () -> Unit) {
    val v2 = IntroMedia.v2Idle()
    IntroLanding(video = v2, onStart = onStart)
}

/**
 * V3: 탑승 인사와 함께 캐릭터로 클로즈업.
 * V3가 없으면 V1 마지막 장면을 캐릭터 쪽으로 확대해 대신하고 3.9초 뒤 넘어간다.
 * 인사 음성은 res/raw/voice_board 가 있으면 그것을, V3도 음성 파일도 없으면 기기 TTS를 쓴다.
 */
@Composable
private fun Boarding(onDone: () -> Unit) {
    val context = LocalContext.current
    val v3 = IntroMedia.v3Board()
    var v3Started by remember { mutableStateOf(false) }
    val zoom = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        zoom.animateTo(2.3f, tween(3400, easing = CubicBezierEasing(.4f, 0f, .2f, 1f)))
    }
    if (v3 == null) LaunchedEffect(Unit) { delay(3900); onDone() }

    // 탑승 영상에 맞춘 차 연출: 5초에 실내등 끔, 6초에 앰비언트 빨강 (영상 첫 화면이 뜬 순간부터 잰다)
    val car = LocalCarMood.current
    LaunchedEffect(v3Started || v3 == null) {
        if (v3Started || v3 == null) {
            delay(5000); runCatching { car?.cabin(false) }
            delay(1000); runCatching { car?.ambient(CarMood.BOARD_RED) }
        }
    }

    DisposableEffect(Unit) {
        val voice = IntroMedia.raw(context, "voice_board")
        var mp: MediaPlayer? = null
        var tts: TextToSpeech? = null
        when {
            voice != null -> mp = MediaPlayer.create(context, voice)?.apply { start() }
            v3 == null -> {
                var engine: TextToSpeech? = null
                engine = TextToSpeech(context) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        engine?.language = Locale.KOREAN
                        engine?.setSpeechRate(.95f)
                        engine?.speak(BOARDING_LINE, TextToSpeech.QUEUE_FLUSH, null, "board")
                    }
                }
                tts = engine
            }
        }
        onDispose {
            mp?.release()
            tts?.run { stop(); shutdown() }
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (!v3Started) {
            PosterImage(
                Modifier.graphicsLayer {
                    scaleX = zoom.value; scaleY = zoom.value
                    transformOrigin = TransformOrigin(.39f, .44f) // 캐릭터 얼굴 쪽
                }
            )
        }
        if (v3 != null) VideoLayer(resId = v3, loop = false, onStarted = { v3Started = true }, onEnded = onDone)

        // 자막은 띄우지 않는다. 대사 "오셨군요."는 V3 영상 안(12~13초)에 있다
    }
}
