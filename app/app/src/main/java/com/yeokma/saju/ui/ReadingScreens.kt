package com.yeokma.saju.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeokma.saju.car.LocalCarMood
import com.yeokma.saju.data.Api
import com.yeokma.saju.data.Report
import com.yeokma.saju.intro.PosterImage
import kotlinx.coroutines.delay

private fun won(n: Int) = "%,d".format(n)

/** 08 복채가 필요합니다: 포인트가 부족할 때만, 화면 전체 */
@Composable
fun PayScreen(worry: String, balance: Int, busy: Boolean, onPay: (Int) -> Unit, onBack: () -> Unit) {
    var sel by remember { mutableIntStateOf(1) }
    Box(Modifier.fillMaxSize().background(Ink.copy(alpha = .58f)).padding(start = 56.dp, end = 56.dp, top = 92.dp, bottom = 36.dp)) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("“$worry”", color = Color(0xFFF0D9A6), fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("복채가 필요합니다", style = TitleOnVideo.copy(fontSize = 44.sp))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("남은 포인트", color = Color(0xFFB9B7CA), fontSize = 15.sp)
                    Text("${won(balance)}P", color = Color(0xFFF08A72), fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                    Text("풀이 한 번에 ${won(Api.PRICE)}P", color = Color(0xFFB9B7CA), fontSize = 15.sp)
                }
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Api.PACKS.forEachIndexed { i, (price, times, bonus) ->
                    val on = sel == i
                    Box(
                        Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(22.dp))
                            .background(if (on) Gold.copy(alpha = .22f) else Ink.copy(alpha = .5f))
                            .border(2.dp, if (on) Gold else Paper.copy(alpha = .18f), RoundedCornerShape(22.dp))
                            .clickable { sel = i },
                    ) {
                        if (bonus > 0) Text(
                            "+${bonus}회 더", color = Color(0xFFFFF6EE), fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).clip(RoundedCornerShape(15.dp)).background(Color(0xFFE8684A)).padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("${won(price)}원", color = Paper, fontSize = 40.sp, fontWeight = FontWeight.SemiBold)
                            Text("풀이 ${times + bonus}회", color = Color(0xFFE9CD92), fontSize = 21.sp)
                            Text("${won((times + bonus) * Api.PRICE)}P 충전" + if (bonus > 0) " (${times}회 + ${bonus}회)" else "", color = Color(0xFFB9B7CA), fontSize = 15.sp)
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedButton(onClick = onBack, border = BorderStroke(1.dp, Paper.copy(alpha = .35f)), modifier = Modifier.height(60.dp)) { Text("다음에 할게요", color = Paper, fontSize = 17.sp) }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { onPay(sel) }, enabled = !busy, modifier = Modifier.height(64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Night),
                ) { Text("${won(Api.PACKS[sel].first)}원 충전하고 풀이 보기", fontSize = 19.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

/** 오행 색 (목 화 토 금 수). 대기 중 무드등이 한 칸씩 돈다 */
private val FIVE = listOf(0x2FB8A6, 0xE8684A, 0xD9A93A, 0xE8E6DF, 0x4C6EF5)

/**
 * 09 LLM 대기: 영상(V4)이 주인공. 글씨는 작은 상태 알약 하나와 맨 아래 2px 진행선뿐.
 * failed가 있으면 작은 카드로 실패와 환불을 알리고 다시 시도를 묻는다.
 */
@Composable
fun LoadingScreen(done: Boolean, failed: String?, onRetry: () -> Unit, onHome: () -> Unit) {
    val car = LocalCarMood.current
    val progress = remember { Animatable(0f) }
    var elapsed by remember { mutableIntStateOf(0) }
    LaunchedEffect(failed) {
        if (failed == null) {
            elapsed = 0
            progress.snapTo(0f)
            progress.animateTo(0.9f, tween(14_000, easing = FastOutSlowInEasing))
        }
    }
    LaunchedEffect(failed) { while (failed == null) { delay(1000); elapsed++ } }
    LaunchedEffect(done) { if (done) progress.animateTo(1f, tween(300, easing = LinearEasing)) }
    LaunchedEffect(failed) {
        var i = 0
        while (failed == null) { runCatching { car?.ambient(FIVE[i % 5]) }; i++; delay(2500) }
    }
    val stage = when {
        done -> "다듬는 중"
        elapsed < 1 -> "명식을 계산하는 중"
        elapsed < 3 -> "풀이를 부탁하는 중"
        else -> "풀이 받는 중"
    }
    Box(Modifier.fillMaxSize()) {
        if (failed == null) {
            Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.clip(RoundedCornerShape(22.dp)).background(Ink.copy(alpha = .5f)).padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(color = Paper, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    Text(stage, color = Paper, fontSize = 17.sp)
                }
                if (elapsed >= 15) Text("조금 더 걸리고 있어요", color = Paper.copy(alpha = .75f), fontSize = 15.sp)
            }
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(progress.value).height(2.dp).background(Gold))
        } else {
            Column(
                Modifier.align(Alignment.Center).clip(RoundedCornerShape(20.dp)).background(Ink.copy(alpha = .78f)).padding(24.dp), // 실패 안내는 화면 가운데
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(failed, color = Paper, fontSize = 19.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onHome, border = BorderStroke(1.dp, Paper.copy(alpha = .35f)), modifier = Modifier.height(56.dp)) { Text("처음으로", color = Paper, fontSize = 17.sp) }
                    Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Night), modifier = Modifier.height(56.dp)) { Text("다시 시도", fontSize = 17.sp) }
                }
            }
        }
    }
}

/** 10 리포트: 왼쪽은 역마아씨 클로즈업(크롭), 오른쪽은 어두운 그라데이션 위에 제목과 소제목 3개 */
@Composable
fun ReportScreen(name: String, report: Report, onHome: () -> Unit) {
    Row(Modifier.fillMaxSize().background(Ink)) {
        Box(Modifier.weight(0.4f).fillMaxHeight()) {
            PosterImage(asset = "v3_end.avif")
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0.6f to Color.Transparent, 1f to Ink)))
        }
        Column(Modifier.weight(0.6f).fillMaxHeight().padding(start = 12.dp, end = 48.dp, top = 36.dp, bottom = 24.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("${name}님께 드리는 풀이", color = Gold, fontSize = 17.sp)
                Text(report.title, color = Paper, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 44.sp)
                report.sections.forEachIndexed { i, (h, b) ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("0${i + 1}  $h", color = Color(0xFFE9CD92), fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                        Text(b, color = Paper.copy(alpha = .92f), fontSize = 19.sp, lineHeight = 31.sp)
                    }
                }
                Text("사주 풀이는 재미로 보는 이야기예요.", color = Paper.copy(alpha = .5f), fontSize = 14.sp)
            }
            Row(Modifier.padding(top = 14.dp)) {
                Spacer(Modifier.weight(1f))
                Button(onClick = onHome, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Night), modifier = Modifier.height(58.dp)) { Text("처음으로", fontSize = 18.sp) }
            }
        }
    }
}
