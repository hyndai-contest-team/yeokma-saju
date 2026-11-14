package com.yeokma.saju

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeokma.saju.data.Profile
import com.yeokma.saju.data.ProfileStore
import com.yeokma.saju.input.AskScreen
import com.yeokma.saju.input.PROFILE_STEPS
import com.yeokma.saju.input.Parse
import com.yeokma.saju.input.StepId
import com.yeokma.saju.input.WORRY_EXAMPLES
import com.yeokma.saju.input.WORRY_STEP
import com.yeokma.saju.intro.IntroFlow
import com.yeokma.saju.car.LocalCarMood
import com.yeokma.saju.car.rememberCarMood
import androidx.compose.runtime.CompositionLocalProvider
import com.yeokma.saju.ui.Backdrop
import com.yeokma.saju.ui.ConfirmScreen
import com.yeokma.saju.ui.Gold
import com.yeokma.saju.ui.Ink
import com.yeokma.saju.ui.Paper
import com.yeokma.saju.ui.PayScreen
import com.yeokma.saju.ui.LoadingScreen
import com.yeokma.saju.ui.ReportScreen
import com.yeokma.saju.data.Api
import com.yeokma.saju.data.Report
import com.yeokma.saju.data.ReadingResult
import com.yeokma.saju.data.deviceId
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.yeokma.saju.ui.ProfilesScreen

enum class Screen { Intro, Profiles, Input, Confirm, Worry, Pay, Loading, Report }

/** 앱 흐름: 인트로 → 오셨군요(저장된 사람/새로 추가) → 입력 6단계 → 확인 → 고민 → 풀이 */
@Composable
fun SajuApp() {
    val store = ProfileStore(LocalContext.current)
    var screen by remember { mutableStateOf(Screen.Intro) }
    var saved by remember { mutableStateOf(store.all()) }
    var profile by remember { mutableStateOf(Profile()) }
    var step by remember { mutableIntStateOf(0) }
    var backToConfirm by remember { mutableStateOf(false) }
    var voiceMode by remember { mutableStateOf(false) }
    var worry by remember { mutableStateOf("") }
    val ctx = LocalContext.current
    val device = remember { deviceId(ctx) }
    val scope = rememberCoroutineScope()
    var balance by remember { mutableIntStateOf(0) }
    var paying by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<Report?>(null) }
    var failed by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }

    // 결제(포인트)가 먼저다. 포인트가 모자라면 복채 화면, 있으면 대기 화면에서 서버가 포인트를 빼고 나서 LLM을 부른다
    fun startReading() {
        scope.launch {
            balance = runCatching { Api.wallet(device) }.getOrDefault(-1)
            when {
                balance < 0 -> { failed = "서버에 연결하지 못했어요"; report = null; screen = Screen.Loading }
                balance < Api.PRICE -> screen = Screen.Pay
                else -> { failed = null; report = null; attempt++; screen = Screen.Loading }
            }
        }
    }

    val car = rememberCarMood()
    val backdropBlur = remember { mutableStateOf(false) }
    CompositionLocalProvider(LocalCarMood provides car, com.yeokma.saju.ui.LocalBackdropBlur provides backdropBlur) {
    Box(Modifier.fillMaxSize().background(Ink)) {
        if (screen != Screen.Intro && screen != Screen.Report) Backdrop()
        AnimatedContent(targetState = screen to step, transitionSpec = { fadeIn(tween(560)) togetherWith fadeOut(tween(400)) }, label = "screen") { (s, i) ->
            when (s) {
                Screen.Intro -> IntroFlow(onDone = { saved = store.all(); screen = Screen.Profiles })
                Screen.Profiles -> ProfilesScreen(
                    saved = saved,
                    onPick = { profile = it; worry = ""; screen = Screen.Worry },
                    onNew = { profile = Profile(); step = 0; backToConfirm = false; screen = Screen.Input },
                )
                Screen.Input -> {
                    val st = PROFILE_STEPS[i]
                    AskScreen(
                        step = st, progress = i to PROFILE_STEPS.size, autoListen = voiceMode,
                        submit = { raw -> Parse.apply(st.id, raw, profile)?.let { (p, shown) -> profile = p; shown } },
                        onNext = { usedVoice ->
                            voiceMode = usedVoice
                            if (backToConfirm || i == PROFILE_STEPS.lastIndex) screen = Screen.Confirm else step = i + 1
                        },
                        onSkip = when (st.id) {
                            StepId.Email -> ({ profile = profile.copy(email = ""); screen = Screen.Confirm })
                            StepId.Time -> ({ profile = profile.copy(time = "모름"); if (backToConfirm) screen = Screen.Confirm else step = i + 1 })
                            else -> null
                        },
                        skipLabel = if (st.id == StepId.Time) "모름" else "건너뛸게요",
                        extra = { pick ->
                            when (st.id) {
                                StepId.Gender -> Choices(listOf("여성", "남성"), pick)
                                StepId.Birth -> Choices(listOf("양력", "음력"), { profile = profile.copy(lunar = it == "음력") }, selected = if (profile.lunar) "음력" else "양력")
                                else -> {}
                            }
                        },
                    )
                }
                Screen.Confirm -> ConfirmScreen(
                    p = profile,
                    onEdit = { idx -> step = idx; backToConfirm = true; voiceMode = false; screen = Screen.Input },
                    onOk = { store.save(profile); saved = store.all(); worry = ""; screen = Screen.Worry },
                )
                Screen.Worry -> AskScreen(
                    step = WORRY_STEP, progress = null, autoListen = voiceMode,
                    submit = { raw -> raw.trim().takeIf { it.length >= 2 }?.also { worry = it } },
                    onNext = { startReading() },
                    extra = { pick -> Hints(pick) },
                )
                Screen.Pay -> PayScreen(worry, balance, paying,
                    onPay = { pack ->
                        paying = true
                        scope.launch {
                            // TODO 결제 연동: 지금은 서버의 목업 충전
                            balance = runCatching { Api.charge(device, pack) }.getOrDefault(balance)
                            paying = false
                            startReading()
                        }
                    },
                    onBack = { screen = Screen.Worry },
                )
                Screen.Loading -> {
                    LaunchedEffect(attempt) {
                        if (failed != null) return@LaunchedEffect
                        when (val r = Api.reading(device, profile, worry)) {
                            is ReadingResult.Ok -> { balance = r.balance; report = r.report; delay(400); screen = Screen.Report }
                            is ReadingResult.NeedPoints -> { balance = r.balance; screen = Screen.Pay }
                            is ReadingResult.Failed -> failed = r.message
                        }
                    }
                    LoadingScreen(done = report != null, failed = failed, onRetry = { failed = null; startReading() }, onHome = { screen = Screen.Intro })
                }
                Screen.Report -> ReportScreen(profile.name, report!!, onHome = { screen = Screen.Intro })
            }
        }
    }
    }
}

@Composable
private fun Choices(options: List<String>, pick: (String) -> Unit, selected: String? = null) =
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        options.forEach { o ->
            val on = o == selected
            Text(
                o, color = if (on) Ink else Paper, fontSize = 20.sp,
                modifier = Modifier.clip(RoundedCornerShape(26.dp)).background(if (on) Gold else Ink.copy(alpha = .5f))
                    .border(1.dp, Gold.copy(alpha = .6f), RoundedCornerShape(26.dp)).clickable { pick(o) }
                    .padding(horizontal = 30.dp, vertical = 12.dp),
            )
        }
    }

/** 고민 힌트 상자: 중요한 일정에 가는 길의 고민 예시. 누르면 그 문장으로 답한다 */
@Composable
private fun Hints(pick: (String) -> Unit) = Column(
    Modifier.clip(RoundedCornerShape(16.dp)).background(Ink.copy(alpha = .55f)).border(1.dp, Paper.copy(alpha = .14f), RoundedCornerShape(16.dp)).padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
) {
    Text("이런 고민도 괜찮아요", color = Gold, fontSize = 17.sp)
    WORRY_EXAMPLES.forEach { e -> Text("· $e", color = Paper, fontSize = 17.sp, modifier = Modifier.clickable { pick(e) }) }
}
