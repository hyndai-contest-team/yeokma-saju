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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
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
import com.yeokma.saju.input.EMAIL_ADDRESS_STEP
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
import com.yeokma.saju.ui.MatchPickScreen
import com.yeokma.saju.ui.MatchReportScreen

enum class Screen { Intro, Profiles, Input, Confirm, Worry, Pay, Loading, Report, MatchPick, MatchReport }

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
    var offline by remember { mutableStateOf(false) }
    var chart by remember { mutableStateOf<com.yeokma.saju.data.Chart?>(null) }
    var chartLoading by remember { mutableStateOf(false) }
    // 확인 화면에 오면 서버 만세력으로 명식을 계산한다(무료, LLM 없음). 리포트 위쪽에도 같이 쓴다
    LaunchedEffect(screen, profile) {
        if (screen == Screen.Confirm || (screen == Screen.Worry && chart == null)) {
            chartLoading = true; chart = Api.manse(profile); chartLoading = false
        }
    }
    var fromSaved by remember { mutableStateOf(false) }
    // 궁합: 좌석 센서가 찾은 동승 자리, 두 자리(0 운전석, 1 함께 탄 분), 지금 고르는 자리
    var companions by remember { mutableStateOf(emptyList<String>()) }
    var matchMode by remember { mutableStateOf(false) }
    var matchA by remember { mutableStateOf<Profile?>(null) }
    var matchB by remember { mutableStateOf<Profile?>(null) }
    var matchSlot by remember { mutableIntStateOf(0) }
    var match by remember { mutableStateOf<com.yeokma.saju.data.Match?>(null) }
    fun fill(p: Profile) {
        if (matchSlot == 0) { matchA = p; if (matchB == null) matchSlot = 1 } else { matchB = p; if (matchA == null) matchSlot = 0 }
    }
    var emailWanted by remember { mutableStateOf(false) }
    val places = remember { com.yeokma.saju.data.Places.get(ctx) }
    var placeOptions by remember { mutableStateOf(emptyList<com.yeokma.saju.data.Places.Place>()) }
    var placeAsk by remember { mutableStateOf("") }
    var province by remember { mutableStateOf<com.yeokma.saju.data.Places.Place?>(null) }

    // 태어난 곳: 목록에서 찾아 하나면 정하고, 같은 이름이 여러 곳이면 어느 곳인지 묻는다
    fun submitPlace(raw: String): String? {
        val text = Parse.place(raw) ?: return null
        fun choose(p: com.yeokma.saju.data.Places.Place): String? {
            val kids = places.children(p)
            if (kids.isNotEmpty()) { // 도만 말했으면 시·군을 다시 묻는다
                province = p
                placeOptions = if (kids.size <= 6) kids else emptyList()
                placeAsk = "${p.label} 어느 시·군인가요?"
                return null
            }
            placeOptions = emptyList(); province = null; placeAsk = ""
            profile = profile.copy(place = p.full); return p.full
        }
        // 도를 말한 뒤라면 그 도 안에서 먼저 찾는다
        province?.let { pv ->
            places.find("${pv.full} $text").second.singleOrNull()?.takeIf { it.full.startsWith(pv.full + " ") }?.let { return choose(it) }
        }
        if (placeOptions.isNotEmpty()) {
            placeOptions.firstOrNull { it.label == raw.trim() || it.full == raw.trim() }?.let { return choose(it) }
            val said = places.find(text).second
            val pick = placeOptions.filter { o -> said.any { x -> o.full == x.full || o.full.startsWith(x.full + " ") } }.singleOrNull()
                ?: placeOptions.filter { o -> o.full.replace(" ", "").contains(text.replace(" ", "")) }.singleOrNull()
            if (pick != null) return choose(pick)
        }
        val (word, found) = places.find(text)
        return when (found.size) {
            0 -> { placeOptions = emptyList(); province = null; placeAsk = ""; profile = profile.copy(place = text); text } // 목록에 없으면 말한 그대로
            1 -> choose(found[0])
            else -> { placeOptions = found; province = null; placeAsk = "어느 ${word}인가요? 골라 주세요"; null }
        }
    }

    // 왼쪽 위 '이전' 버튼과 기기 뒤로 가기: 한 단계 앞 화면으로
    fun goBack() {
        placeOptions = emptyList(); province = null; placeAsk = ""
        when (screen) {
            Screen.Intro -> {}
            Screen.Profiles -> screen = Screen.Intro
            Screen.MatchPick -> { matchMode = false; screen = Screen.Profiles }
            Screen.MatchReport -> screen = Screen.MatchPick
            Screen.Input -> when {
                PROFILE_STEPS[step].id == StepId.Email && emailWanted -> emailWanted = false
                backToConfirm -> screen = Screen.Confirm
                step > 0 -> step -= 1
                else -> screen = Screen.Profiles
            }
            Screen.Confirm -> { step = PROFILE_STEPS.lastIndex; backToConfirm = false; screen = Screen.Input }
            Screen.Worry -> screen = if (fromSaved) Screen.Profiles else Screen.Confirm
            Screen.Pay -> screen = if (matchMode) Screen.MatchPick else Screen.Worry
            Screen.Loading -> if (failed != null) screen = if (matchMode) Screen.MatchPick else Screen.Worry
            Screen.Report -> screen = Screen.Worry
        }
    }

    // 결제(포인트)가 먼저다. 포인트가 모자라면 복채 화면, 있으면 대기 화면에서 서버가 포인트를 빼고 나서 LLM을 부른다
    // 궁합도 포인트가 먼저다. 서버가 꺼져 있으면 궁합은 볼 수 없다고 알린다
    fun startMatch() {
        scope.launch {
            balance = runCatching { Api.wallet(device) }.getOrDefault(-1)
            when {
                balance < 0 -> { failed = "서버에 연결되면 궁합을 볼 수 있어요"; match = null; screen = Screen.Loading }
                balance < Api.PRICE -> screen = Screen.Pay
                else -> { failed = null; match = null; attempt++; screen = Screen.Loading }
            }
        }
    }

    fun startReading() {
        if (matchMode) { startMatch(); return }
        scope.launch {
            balance = runCatching { Api.wallet(device) }.getOrDefault(-1)
            when {
                // 서버가 꺼져 있으면 앱 안의 목업 풀이를 보여 준다(포인트 없음)
                balance < 0 -> { offline = true; failed = null; report = null; attempt++; screen = Screen.Loading }
                balance < Api.PRICE -> screen = Screen.Pay
                else -> { offline = false; failed = null; report = null; attempt++; screen = Screen.Loading }
            }
        }
    }

    val car = rememberCarMood()
    // 기어가 P가 아니면 잠근다. 다시 P가 되면 대기 화면의 차 연출(무드등, 실내등, 송풍)을 되살린다
    val parked = car?.parked ?: true
    LaunchedEffect(parked) {
        val was = com.yeokma.saju.car.DriveLock.locked
        com.yeokma.saju.car.DriveLock.locked = !parked
        if (parked && was) runCatching { car?.idle() }
    }
    // "오셨군요"에 오면 좌석 센서로 동승자를 찾는다
    LaunchedEffect(screen) { if (screen == Screen.Profiles) car?.companions { companions = it } }
    val backdropBlur = remember { mutableStateOf(false) }
    CompositionLocalProvider(LocalCarMood provides car, com.yeokma.saju.ui.LocalBackdropBlur provides backdropBlur) {
    Box(Modifier.fillMaxSize().background(Ink)) {
        if (screen != Screen.Intro && screen != Screen.Report) Backdrop()
        androidx.activity.compose.BackHandler(enabled = screen != Screen.Intro && parked) { goBack() }
        AnimatedContent(targetState = screen to step, transitionSpec = { fadeIn(tween(560)) togetherWith fadeOut(tween(400)) }, label = "screen") { (s, i) ->
            when (s) {
                Screen.Intro -> IntroFlow(onDone = { saved = store.all(); screen = Screen.Profiles })
                Screen.Profiles -> ProfilesScreen(
                    saved = saved, companions = companions,
                    onMatch = { matchMode = true; matchA = null; matchB = null; matchSlot = 0; screen = Screen.MatchPick },
                    onPick = { profile = it; worry = ""; fromSaved = true; chart = null; screen = Screen.Worry },
                    onNew = { profile = Profile(); chart = null; step = 0; backToConfirm = false; fromSaved = false; placeOptions = emptyList(); province = null; placeAsk = ""; emailWanted = false; screen = Screen.Input },
                )
                Screen.Input -> {
                    val st = PROFILE_STEPS[i].let { if (it.id == StepId.Email && emailWanted) EMAIL_ADDRESS_STEP else it }
                    AskScreen(
                        step = st, progress = i to PROFILE_STEPS.size, autoListen = voiceMode,
                        submit = { raw ->
                            when {
                                st.id == StepId.Place -> submitPlace(raw)
                                st.id == StepId.Email && Parse.email(raw).isNullOrEmpty() && Parse.emailWant(raw) == true -> { emailWanted = true; null }
                                else -> Parse.apply(st.id, raw, profile)?.let { (p, shown) -> profile = p; shown }
                            }
                        },
                        rejectHint = {
                            when {
                                st.id == StepId.Place && placeAsk.isNotEmpty() -> placeAsk
                                st.id == StepId.Email -> "받으실지 골라 주세요"
                                else -> null
                            }
                        },
                        onNext = { usedVoice ->
                            voiceMode = usedVoice
                            if (backToConfirm || i == PROFILE_STEPS.lastIndex) screen = Screen.Confirm else step = i + 1
                        },
                        onSkip = when (st.id) {
                            StepId.EmailAddress -> ({ profile = profile.copy(email = ""); screen = Screen.Confirm })
                            StepId.Time -> ({ profile = profile.copy(time = "모름"); if (backToConfirm) screen = Screen.Confirm else step = i + 1 })
                            else -> null
                        },
                        skipLabel = when (st.id) { StepId.Time -> "모름"; StepId.EmailAddress -> "받지 않을게요"; else -> "건너뛸게요" },
                        extra = { pick ->
                            when (st.id) {
                                StepId.Gender -> Choices(listOf("여자", "남자"), pick)
                                StepId.Email -> EmailChoice(pick)
                                StepId.Birth -> Choices(listOf("양력", "음력"), { profile = profile.copy(lunar = it == "음력") }, selected = if (profile.lunar) "음력" else "양력")
                                StepId.Place -> if (placeOptions.isNotEmpty()) PlaceChoices(placeOptions.map { it.label }, pick)
                                else -> {}
                            }
                        },
                    )
                }
                Screen.Confirm -> ConfirmScreen(
                    p = profile, chart = chart, chartLoading = chartLoading,
                    onEdit = { idx -> step = idx; backToConfirm = true; voiceMode = false; emailWanted = false; screen = Screen.Input },
                    onOk = {
                        store.save(profile); saved = store.all(); worry = ""; fromSaved = false
                        if (matchMode) { fill(profile); screen = Screen.MatchPick } else screen = Screen.Worry
                    },
                )
                Screen.Worry -> AskScreen(
                    step = WORRY_STEP, progress = null, autoListen = voiceMode,
                    submit = { raw -> raw.trim().takeIf { it.length >= 2 }?.also { worry = it } },
                    onNext = { startReading() },
                    extra = { pick -> Hints(pick) },
                )
                Screen.Pay -> PayScreen(if (matchMode) "${matchA?.name} ♥ ${matchB?.name} 궁합" else worry, balance, paying,
                    onPay = { pack ->
                        paying = true
                        scope.launch {
                            // TODO 결제 연동: 지금은 서버의 목업 충전
                            balance = runCatching { Api.charge(device, pack) }.getOrDefault(balance)
                            paying = false
                            startReading()
                        }
                    },
                    onBack = { screen = if (matchMode) Screen.MatchPick else Screen.Worry },
                )
                Screen.Loading -> {
                    LaunchedEffect(attempt) {
                        if (failed != null) return@LaunchedEffect
                        if (matchMode) {
                            when (val r = Api.match(device, matchA!!, matchB!!)) {
                                is com.yeokma.saju.data.MatchResult.Ok -> { balance = r.balance; match = r.match; delay(400); screen = Screen.MatchReport }
                                is com.yeokma.saju.data.MatchResult.NeedPoints -> { balance = r.balance; screen = Screen.Pay }
                                is com.yeokma.saju.data.MatchResult.Failed -> failed = r.message
                            }
                            return@LaunchedEffect
                        }
                        if (offline) { delay(2500); report = com.yeokma.saju.data.Offline.report(profile, worry); delay(400); screen = Screen.Report; return@LaunchedEffect }
                        val began = System.currentTimeMillis()
                        when (val r = Api.reading(device, profile, worry)) {
                            // 답이 빨리 와도 무드등이 오행 색을 한 바퀴 돌 만큼은 대기 화면을 보여 준다
                            is ReadingResult.Ok -> { balance = r.balance; report = r.report; delay(maxOf(400L, 3500L - (System.currentTimeMillis() - began))); screen = Screen.Report }
                            is ReadingResult.NeedPoints -> { balance = r.balance; screen = Screen.Pay }
                            is ReadingResult.Failed -> failed = r.message
                        }
                    }
                    LoadingScreen(done = if (matchMode) match != null else report != null, failed = failed, onRetry = { failed = null; startReading() }, onHome = { screen = Screen.Intro })
                }
                Screen.Report -> ReportScreen(profile.name, report!!, chart, onHome = { screen = Screen.Intro })
                Screen.MatchPick -> MatchPickScreen(
                    saved = saved, a = matchA, b = matchB, slot = matchSlot, companions = companions,
                    onSlot = { matchSlot = it },
                    onPick = { fill(it) },
                    onNew = { profile = Profile(); chart = null; step = 0; backToConfirm = false; placeOptions = emptyList(); province = null; placeAsk = ""; emailWanted = false; screen = Screen.Input },
                    onGo = { startMatch() },
                )
                Screen.MatchReport -> MatchReportScreen(matchA!!, matchB!!, match!!, onHome = { matchMode = false; screen = Screen.Intro })
            }
        }
        if (!parked) DriveLockScreen()
        if (parked && screen != Screen.Intro && (screen != Screen.Loading || failed != null)) BackButton(Modifier.align(Alignment.TopStart).padding(start = 20.dp, top = 18.dp)) { goBack() }
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

/** 메일 받기/안 받기. 5초 동안 고르지 않으면 받지 않는 쪽으로 넘어간다(받지 않을게요 버튼 안의 게이지로 남은 시간 표시) */
@Composable
private fun EmailChoice(pick: (String) -> Unit) = Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
    val left = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(Unit) {
        left.animateTo(0f, tween(5000, easing = androidx.compose.animation.core.LinearEasing))
        pick("받지 않을게요")
    }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        listOf("받을게요", "받지 않을게요").forEachIndexed { k, o ->
            val dflt = k == 1
            Box(
                Modifier.clip(RoundedCornerShape(26.dp)).background(if (dflt) Ink.copy(alpha = .5f) else Gold)
                    .border(1.dp, Gold.copy(alpha = .6f), RoundedCornerShape(26.dp)).clickable { pick(o) },
                contentAlignment = androidx.compose.ui.Alignment.CenterStart,
            ) {
                // 받지 않는 쪽 버튼 안에 남은 시간을 채운다
                if (dflt) Box(Modifier.matchParentSize().padding(0.dp)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(left.value).background(Gold.copy(alpha = .28f)))
                }
                Text(o, color = if (dflt) Paper else Ink, fontSize = 20.sp, modifier = Modifier.padding(horizontal = 30.dp, vertical = 12.dp))
            }
        }
    }
    Text("${kotlin.math.ceil(left.value * 5).toInt()}초 뒤 받지 않는 쪽으로 넘어가요", color = Paper.copy(alpha = .7f), fontSize = 16.sp)
}

/** 기어가 P가 아닐 때 화면 전체를 덮는다. 아래 화면은 누를 수 없고, 영상·소리·음성 입력은 멈춘다 */
@Composable
private fun DriveLockScreen() = Box(
    Modifier.fillMaxSize().background(Ink.copy(alpha = .96f))
        .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {},
    contentAlignment = androidx.compose.ui.Alignment.Center,
) {
    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("P", color = Gold, fontSize = 64.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            modifier = Modifier.size(120.dp).border(3.dp, Gold, RoundedCornerShape(24.dp)).padding(top = 14.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text("주행 중에는 잠시 멈춰요", color = Paper, fontSize = 34.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Text("기어를 P에 두면 보던 곳부터 이어서 볼 수 있어요", color = Paper.copy(alpha = .85f), fontSize = 21.sp)
        Text("영상과 소리는 멈췄고, 조명과 공조는 원래대로 돌려놨어요", color = Paper.copy(alpha = .6f), fontSize = 17.sp)
    }
}
