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
import androidx.compose.ui.Alignment
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
    var fromSaved by remember { mutableStateOf(false) }
    val places = remember { com.yeokma.saju.data.Places.get(ctx) }
    var placeOptions by remember { mutableStateOf(emptyList<com.yeokma.saju.data.Places.Place>()) }
    var placeWord by remember { mutableStateOf("") }

    // 태어난 곳: 목록에서 찾아 하나면 정하고, 같은 이름이 여러 곳이면 어느 곳인지 묻는다
    fun submitPlace(raw: String): String? {
        val text = Parse.place(raw) ?: return null
        fun choose(p: com.yeokma.saju.data.Places.Place): String { placeOptions = emptyList(); profile = profile.copy(place = p.full); return p.full }
        if (placeOptions.isNotEmpty()) {
            placeOptions.firstOrNull { it.label == raw.trim() || it.full == raw.trim() }?.let { return choose(it) }
            val said = places.find(text).second
            val pick = placeOptions.filter { o -> said.any { x -> o.full == x.full || o.full.startsWith(x.full + " ") } }.singleOrNull()
                ?: placeOptions.filter { o -> o.full.replace(" ", "").contains(text.replace(" ", "")) }.singleOrNull()
            if (pick != null) return choose(pick)
        }
        val (word, found) = places.find(text)
        return when (found.size) {
            0 -> { placeOptions = emptyList(); profile = profile.copy(place = text); text } // 목록에 없으면 말한 그대로
            1 -> choose(found[0])
            else -> { placeOptions = found; placeWord = word; null }
        }
    }

    // 왼쪽 위 '이전' 버튼과 기기 뒤로 가기: 한 단계 앞 화면으로
    fun goBack() {
        placeOptions = emptyList()
        when (screen) {
            Screen.Intro -> {}
            Screen.Profiles -> screen = Screen.Intro
            Screen.Input -> when {
                backToConfirm -> screen = Screen.Confirm
                step > 0 -> step -= 1
                else -> screen = Screen.Profiles
            }
            Screen.Confirm -> { step = PROFILE_STEPS.lastIndex; backToConfirm = false; screen = Screen.Input }
            Screen.Worry -> screen = if (fromSaved) Screen.Profiles else Screen.Confirm
            Screen.Pay -> screen = Screen.Worry
            Screen.Loading -> if (failed != null) screen = Screen.Worry
            Screen.Report -> screen = Screen.Worry
        }
    }

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
        androidx.activity.compose.BackHandler(enabled = screen != Screen.Intro) { goBack() }
        AnimatedContent(targetState = screen to step, transitionSpec = { fadeIn(tween(560)) togetherWith fadeOut(tween(400)) }, label = "screen") { (s, i) ->
            when (s) {
                Screen.Intro -> IntroFlow(onDone = { saved = store.all(); screen = Screen.Profiles })
                Screen.Profiles -> ProfilesScreen(
                    saved = saved,
                    onPick = { profile = it; worry = ""; fromSaved = true; screen = Screen.Worry },
                    onNew = { profile = Profile(); step = 0; backToConfirm = false; fromSaved = false; placeOptions = emptyList(); screen = Screen.Input },
                )
                Screen.Input -> {
                    val st = PROFILE_STEPS[i]
                    AskScreen(
                        step = st, progress = i to PROFILE_STEPS.size, autoListen = voiceMode,
                        submit = { raw -> if (st.id == StepId.Place) submitPlace(raw) else Parse.apply(st.id, raw, profile)?.let { (p, shown) -> profile = p; shown } },
                        rejectHint = { if (st.id == StepId.Place && placeOptions.isNotEmpty()) "어느 ${placeWord}인가요? 골라 주세요" else null },
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
                                StepId.Place -> if (placeOptions.isNotEmpty()) PlaceChoices(placeOptions.map { it.label }, pick)
                                else -> {}
                            }
                        },
                    )
                }
                Screen.Confirm -> ConfirmScreen(
                    p = profile,
                    onEdit = { idx -> step = idx; backToConfirm = true; voiceMode = false; screen = Screen.Input },
                    onOk = { store.save(profile); saved = store.all(); worry = ""; fromSaved = false; screen = Screen.Worry },
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
        if (screen != Screen.Intro && (screen != Screen.Loading || failed != null)) BackButton(Modifier.align(Alignment.TopStart).padding(start = 20.dp, top = 18.dp)) { goBack() }
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

/** 왼쪽 위 '이전' 버튼 */
@Composable
private fun BackButton(modifier: Modifier, onClick: () -> Unit) = Text(
    "‹  이전", color = Paper, fontSize = 20.sp,
    modifier = modifier.clip(RoundedCornerShape(28.dp)).background(Ink.copy(alpha = .55f))
        .border(1.dp, Paper.copy(alpha = .28f), RoundedCornerShape(28.dp)).clickable(onClick = onClick)
        .padding(horizontal = 22.dp, vertical = 12.dp),
)

/** 같은 이름의 지역이 여러 곳일 때 고르는 칩. 여러 줄로 감긴다 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PlaceChoices(options: List<String>, pick: (String) -> Unit) = androidx.compose.foundation.layout.FlowRow(
    horizontalArrangement = Arrangement.spacedBy(12.dp, androidx.compose.ui.Alignment.CenterHorizontally),
    verticalArrangement = Arrangement.spacedBy(10.dp),
) {
    options.forEach { o ->
        Text(
            o, color = Paper, fontSize = 20.sp,
            modifier = Modifier.clip(RoundedCornerShape(26.dp)).background(Ink.copy(alpha = .5f))
                .border(1.dp, Gold.copy(alpha = .6f), RoundedCornerShape(26.dp)).clickable { pick(o) }
                .padding(horizontal = 26.dp, vertical = 12.dp),
        )
    }
}
