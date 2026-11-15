package com.yeokma.saju.input

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeokma.saju.ui.Gold
import com.yeokma.saju.ui.Ink
import com.yeokma.saju.ui.LineOnVideo
import com.yeokma.saju.ui.Night
import com.yeokma.saju.ui.Paper
import com.yeokma.saju.ui.TitleOnVideo

enum class AskMode { Idle, Listening, Done }

/**
 * 한 화면에 질문 하나. 글씨는 아래 띠에만 둔다(배경은 화면 전체 영상/그림).
 * 음성으로 답하면 1.6초 원형 카운트다운 뒤 자동으로 다음으로 넘어가고, '고칠래'를 누르면 멈추고 키보드가 열린다.
 * 키보드를 열면 입력줄만 키보드(IME) 높이 위에 붙고 아래 띠는 사라진다.
 *
 * submit: 원문을 받아 화면에 보여 줄 값을 돌려준다. 못 알아들으면 null.
 */
@Composable
fun AskScreen(
    step: Step,
    progress: Pair<Int, Int>?,
    autoListen: Boolean,
    submit: (String) -> String?,
    onNext: (usedVoice: Boolean) -> Unit,
    onSkip: (() -> Unit)? = null,
    skipLabel: String = "건너뛸게요",
    extra: @Composable (pick: (String) -> Unit) -> Unit = {},
    rejectHint: () -> String? = { null },
) {
    var mode by remember(step.id) { mutableStateOf(AskMode.Idle) }
    var shown by remember(step.id) { mutableStateOf("") }
    var hint by remember(step.id) { mutableStateOf("") }
    var kb by remember(step.id) { mutableStateOf(false) }
    var draft by remember(step.id) { mutableStateOf("") }
    var viaVoice by remember(step.id) { mutableStateOf(false) }
    val blur = com.yeokma.saju.ui.LocalBackdropBlur.current
    LaunchedEffect(kb) { blur.value = kb }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { blur.value = false } }

    fun take(raw: String, voice: Boolean) {
        val v = submit(raw)
        if (v == null) { hint = rejectHint() ?: "잘 못 알아들었어요. 다시 말씀해 주세요"; mode = AskMode.Idle; return }
        hint = ""; shown = v; viaVoice = voice; mode = AskMode.Done
        if (!voice) { kb = false; onNext(false) }
    }

    val (voice, listen) = rememberVoice(
        onFinal = { take(it, true) },
        onFail = { mode = AskMode.Idle; hint = "잘 못 들었어요. 다시 눌러 말씀해 주세요" },
    )
    LaunchedEffect(voice.listening) { if (voice.listening) mode = AskMode.Listening else if (mode == AskMode.Listening) mode = AskMode.Idle }
    LaunchedEffect(step.id) { if (autoListen && voice.available) listen() }
    LaunchedEffect(voice.available) { if (!voice.available) kb = true }

    // 음성으로 답한 뒤 카운트다운
    val count = remember(step.id) { Animatable(0f) }
    LaunchedEffect(mode, viaVoice) {
        if (mode == AskMode.Done && viaVoice) {
            count.snapTo(0f)
            count.animateTo(1f, tween(1600, easing = LinearEasing))
            onNext(true)
        }
    }

    Box(Modifier.fillMaxSize()) {
        // 아래 띠만 어둡게
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(340.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Ink.copy(alpha = .72f), Ink.copy(alpha = .92f))))
        )
        progress?.let { (i, n) ->
            Row(Modifier.align(Alignment.TopCenter).padding(top = 28.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(n) { k ->
                    Box(Modifier.height(5.dp).width(if (k == i) 26.dp else 8.dp).clip(RoundedCornerShape(3.dp))
                        .background(if (k <= i) Gold else Paper.copy(alpha = .3f)))
                }
            }
        }

        AnimatedVisibility(
            visible = !kb, enter = fadeIn() + slideInVertically { it / 5 }, exit = fadeOut() + slideOutVertically { it / 5 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(bottom = 32.dp, start = 48.dp, end = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(step.question, style = TitleOnVideo.copy(fontSize = 36.sp), textAlign = TextAlign.Center)
                val answer = when (mode) {
                    AskMode.Listening -> voice.partial.ifEmpty { "듣고 있어요…" }
                    AskMode.Done -> shown
                    else -> hint
                }
                if (answer.isNotEmpty()) Text(answer, style = LineOnVideo.copy(fontSize = if (mode == AskMode.Idle) 18.sp else 28.sp, color = if (mode == AskMode.Idle) Paper.copy(alpha = .8f) else Gold), textAlign = TextAlign.Center)
                extra { take(it, false) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    Pill("키보드") { voice.stop(); draft = ""; kb = true }
                    if (voice.available) Orb(mode, count.value) {
                        when (mode) {
                            AskMode.Done -> { mode = AskMode.Idle; draft = shown; kb = true } // 고칠래
                            AskMode.Listening -> voice.stop()
                            else -> listen()
                        }
                    }
                    if (onSkip != null) Pill(skipLabel) { voice.stop(); onSkip() } else Box(Modifier.width(96.dp))
                }
                if (mode == AskMode.Done && viaVoice) Text("누르면 멈추고 고칠 수 있어요", color = Paper.copy(alpha = .7f), fontSize = 17.sp)
            }
        }

        if (kb) KeyboardBar(step, draft, onDraft = { draft = it }, onDone = { if (draft.isNotBlank()) take(draft, false) else hint = "" }, onClose = { kb = false }, hint = hint)
    }
}

@Composable
private fun Pill(label: String, onClick: () -> Unit) = OutlinedButton(
    onClick = onClick, border = BorderStroke(1.dp, Paper.copy(alpha = .35f)),
    colors = ButtonDefaults.outlinedButtonColors(containerColor = Ink.copy(alpha = .45f), contentColor = Paper),
    modifier = Modifier.height(48.dp).widthIn(min = 120.dp),
) { Text(label, fontSize = 17.sp, maxLines = 1, softWrap = false) }

@Composable
private fun Orb(mode: AskMode, count: Float, onClick: () -> Unit) {
    Box(
        Modifier.size(88.dp).clip(CircleShape).background(if (mode == AskMode.Listening) Paper else Gold).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (mode == AskMode.Done) CircularProgressIndicator(progress = { count }, color = Night, strokeWidth = 3.dp, modifier = Modifier.size(80.dp))
        Text(
            when (mode) { AskMode.Listening -> "듣는 중"; AskMode.Done -> "고칠래"; else -> "말하기" },
            color = Night, fontSize = 17.sp,
        )
    }
}

@Composable
private fun KeyboardBar(step: Step, draft: String, onDraft: (String) -> Unit, onDone: () -> Unit, onClose: () -> Unit, hint: String) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(step.id) { focus.requestFocus() }
    androidx.activity.compose.BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClick = onClose).imePadding()) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 48.dp, end = 48.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(step.question, style = TitleOnVideo.copy(fontSize = 44.sp), textAlign = TextAlign.Center)
            androidx.compose.foundation.text.BasicTextField(
                value = draft, onValueChange = onDraft, singleLine = step.id != StepId.Worry,
                textStyle = LineOnVideo.copy(fontSize = 40.sp, textAlign = TextAlign.Center),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color(0xFF6E7BFF)),
                keyboardOptions = KeyboardOptions(keyboardType = step.keyboard, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (draft.isEmpty()) Text(step.placeholder, style = LineOnVideo.copy(fontSize = 22.sp, color = Paper.copy(alpha = .45f)), textAlign = TextAlign.Center)
                        inner()
                    }
                },
            )
            if (hint.isNotEmpty()) Text(hint, color = Gold, fontSize = 17.sp)
        }
    }
}
