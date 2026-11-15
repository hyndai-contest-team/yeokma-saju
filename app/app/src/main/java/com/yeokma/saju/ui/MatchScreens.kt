package com.yeokma.saju.ui

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeokma.saju.data.Match
import com.yeokma.saju.data.Profile
import com.yeokma.saju.intro.PosterImage

/** 궁합: 두 자리(나, 함께 탄 분)를 채운다. 아래 저장된 사람을 누르면 고른 자리에 들어가고, 새로 입력도 할 수 있다 */
@Composable
fun MatchPickScreen(
    saved: List<Profile>, a: Profile?, b: Profile?, slot: Int, companions: List<String>,
    onSlot: (Int) -> Unit, onPick: (Profile) -> Unit, onNew: () -> Unit, onGo: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        BottomScrim(460)
        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text("누구와 누구의 궁합을 볼까요?", style = TitleOnVideo.copy(fontSize = 32.sp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Slot("운전석 · 나", a, slot == 0) { onSlot(0) }
                Text("♥", color = Gold, fontSize = 30.sp)
                Slot("${companions.firstOrNull() ?: "동승석"} · 함께 탄 분", b, slot == 1) { onSlot(1) }
            }
            Text(if (slot == 0) "운전석에 앉은 분을 골라 주세요" else "함께 탄 분을 골라 주세요", color = Paper.copy(alpha = .8f), fontSize = 17.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                saved.forEachIndexed { i, p -> PersonCard(p, CardColors[i % CardColors.size]) { onPick(p) } }
                if (saved.size < 5) NewCard(onNew)
            }
            Button(
                onClick = onGo, enabled = a != null && b != null && a != b,
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Night),
                modifier = Modifier.height(60.dp),
            ) { Text("궁합 보기", fontSize = 19.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun Slot(label: String, p: Profile?, selected: Boolean, onClick: () -> Unit) = Column(
    Modifier.width(260.dp).height(110.dp).clip(RoundedCornerShape(20.dp)).background(Ink.copy(alpha = .65f))
        .border(if (selected) 2.dp else 1.dp, if (selected) Gold else Paper.copy(alpha = .2f), RoundedCornerShape(20.dp))
        .clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.Center,
) {
    Text(label, color = Color(0xFFB9B7CA), fontSize = 15.sp)
    Text(p?.name ?: "고르기", color = if (p != null) Paper else Gold, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    if (p != null) Text(p.summary, color = Color(0xFFB9B7CA), fontSize = 14.sp, maxLines = 1)
}

/** 궁합 결과: 왼쪽은 점수와 두 사람의 명식, 오른쪽은 제목과 소제목 3개 */
@Composable
fun MatchReportScreen(a: Profile, b: Profile, m: Match, onHome: () -> Unit) {
    Row(Modifier.fillMaxSize().background(Ink)) {
        Box(Modifier.weight(0.42f).fillMaxHeight()) {
            PosterImage(asset = "v3_end.avif")
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Ink.copy(alpha = .55f), 0.7f to Ink.copy(alpha = .75f), 1f to Ink)))
            Column(
                Modifier.fillMaxSize().padding(start = 40.dp, end = 16.dp, top = 92.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("${a.name} ♥ ${b.name}", color = Paper, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.size(118.dp).clip(CircleShape).border(3.dp, Gold, CircleShape).background(Ink.copy(alpha = .6f)), contentAlignment = Alignment.Center) {
                        Text("${m.score}", color = Gold, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 50.sp)
                    }
                    Column {
                        Text("궁합 점수", color = Color(0xFFB9B7CA), fontSize = 15.sp)
                        Text(m.kind, color = Gold, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 36.sp)
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(a.name, color = Paper.copy(alpha = .85f), fontSize = 15.sp)
                ChartStrip(m.a)
                Text(b.name, color = Paper.copy(alpha = .85f), fontSize = 15.sp)
                ChartStrip(m.b)
            }
        }
        Column(Modifier.weight(0.58f).fillMaxHeight().padding(start = 12.dp, end = 48.dp, top = 36.dp, bottom = 24.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("두 분께 드리는 궁합 풀이", color = Gold, fontSize = 17.sp)
                Text(m.report.title, color = Paper, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 42.sp)
                m.report.sections.forEachIndexed { i, (h, body) ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("0${i + 1}  $h", color = Color(0xFFE9CD92), fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                        Text(body, color = Paper.copy(alpha = .92f), fontSize = 19.sp, lineHeight = 31.sp)
                    }
                }
            }
            Row(Modifier.padding(top = 14.dp)) {
                Spacer(Modifier.weight(1f))
                Button(onClick = onHome, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Night), modifier = Modifier.height(58.dp)) { Text("처음으로", fontSize = 18.sp) }
            }
        }
    }
}
