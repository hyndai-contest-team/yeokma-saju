package com.yeokma.saju.ui

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeokma.saju.data.Profile
import com.yeokma.saju.intro.IntroMedia
import com.yeokma.saju.intro.PosterImage
import com.yeokma.saju.intro.VideoLayer

/** 모든 화면의 배경. V4(마주 앉은 루프)가 오면 그 영상, 없으면 V3 끝 장면. 화면 전체를 채운다. */
@Composable
fun Backdrop() {
    val v4 = IntroMedia.v4Seated()
    val blurred by LocalBackdropBlur.current
    val radius by androidx.compose.animation.core.animateDpAsState(if (blurred) 28.dp else 0.dp, label = "blur")
    Box(Modifier.fillMaxSize().blur(radius)) {
        if (v4 != null) VideoLayer(resId = v4, loop = true, muted = true, blur = radius.value) else PosterImage(asset = "v3_end.avif")
    }
    if (blurred) Box(Modifier.fillMaxSize().background(Ink.copy(alpha = .35f)))
}

/** 키보드 입력 중에는 배경 전체를 가우시안 블러로 깐다 */
val LocalBackdropBlur = androidx.compose.runtime.compositionLocalOf<androidx.compose.runtime.MutableState<Boolean>> { androidx.compose.runtime.mutableStateOf(false) }

@Composable
internal fun BottomScrim(height: Int = 320) = Box(Modifier.fillMaxSize()) {
    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(height.dp)
        .background(Brush.verticalGradient(listOf(Color.Transparent, Ink.copy(alpha = .72f), Ink.copy(alpha = .92f)))))
}

internal val CardColors = listOf(Color(0xFF2FB8A6), Color(0xFFD9B26A), Color(0xFF7B93F7), Color(0xFFE8684A), Color(0xFFC9A0DC))

/**
 * 02 오셨군요: 저장된 사람(최대 5명) 또는 새로 추가.
 * 좌석 센서가 동승자를 찾으면 먼저 "혼자가 아니시군요?"를 묻고 사주 보기와 궁합 보기로 나눈다.
 */
@Composable
fun ProfilesScreen(saved: List<Profile>, companions: List<String>, onPick: (Profile) -> Unit, onNew: () -> Unit, onMatch: () -> Unit) {
    var solo by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        BottomScrim(320)
        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text("오셨군요", style = TitleOnVideo.copy(fontSize = 34.sp))
            if (companions.isNotEmpty() && !solo) {
                Text("어라, 혼자가 아니시군요?", style = LineOnVideo.copy(fontSize = 26.sp, color = Gold))
                Text("${companions.joinToString(", ")}에 함께 타신 분이 있어요", color = Paper.copy(alpha = .8f), fontSize = 17.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton(
                        onClick = { solo = true }, border = BorderStroke(1.dp, Paper.copy(alpha = .4f)),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Ink.copy(alpha = .5f), contentColor = Paper),
                        modifier = Modifier.height(64.dp).width(220.dp),
                    ) { Text("사주 보기", fontSize = 20.sp) }
                    Button(
                        onClick = onMatch, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Night),
                        modifier = Modifier.height(64.dp).width(220.dp),
                    ) { Text("궁합 보기", fontSize = 20.sp, fontWeight = FontWeight.SemiBold) }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    saved.forEachIndexed { i, p -> PersonCard(p, CardColors[i % CardColors.size]) { onPick(p) } }
                    if (saved.size < 5) NewCard(onNew)
                }
            }
        }
    }
}

@Composable
internal fun PersonCard(p: Profile, color: Color, onClick: () -> Unit) = Row(
    Modifier.width(232.dp).height(92.dp).clip(RoundedCornerShape(18.dp)).background(Ink.copy(alpha = .62f))
        .border(1.dp, Paper.copy(alpha = .18f), RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(horizontal = 16.dp),
    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
) {
    Box(Modifier.size(46.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        Text(p.name.takeLast(1), color = Night, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
    Column {
        Text(p.name, color = Paper, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(p.summary, color = Color(0xFFB9B7CA), fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun NewCard(onClick: () -> Unit) = Box(
    Modifier.width(180.dp).height(92.dp).clip(RoundedCornerShape(18.dp)).background(Ink.copy(alpha = .45f))
        .border(1.dp, Gold.copy(alpha = .6f), RoundedCornerShape(18.dp)).clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
) { Text("+ 새로 추가", color = Gold, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }

/** 06 확인: 왼쪽은 입력한 정보(줄을 누르면 그 질문으로 돌아가 고친다), 오른쪽은 서버가 계산한 명식 */
@Composable
fun ConfirmScreen(p: Profile, chart: com.yeokma.saju.data.Chart?, chartLoading: Boolean, onEdit: (Int) -> Unit, onOk: () -> Unit) {
    val rows = listOf(
        "이름" to p.name, "성별" to p.gender, "생년월일" to (p.birth + if (p.lunar) " (음력)" else " (양력)"),
        "태어난 시간" to p.time, "태어난 곳" to p.place, "이메일" to p.email.ifEmpty { "받지 않음" },
    )
    Box(Modifier.fillMaxSize().background(Ink.copy(alpha = .62f)).padding(start = 56.dp, end = 56.dp, top = 92.dp, bottom = 36.dp)) {
        Row(Modifier.fillMaxSize().padding(bottom = 84.dp), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("이렇게 맞나요?", style = TitleOnVideo.copy(fontSize = 38.sp))
                rows.chunked(2).forEachIndexed { r, pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEachIndexed { c, (k, v) ->
                            val idx = r * 2 + c
                            Row(
                                Modifier.weight(1f).height(84.dp).clip(RoundedCornerShape(16.dp)).background(Ink.copy(alpha = .55f))
                                    .border(1.dp, Paper.copy(alpha = .14f), RoundedCornerShape(16.dp)).clickable { onEdit(idx) }.padding(horizontal = 18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(k, color = Color(0xFFB9B7CA), fontSize = 16.sp)
                                    Text(v, color = Paper, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text("고치기", color = Color(0xFFB9B7CA), fontSize = 15.sp)
                            }
                        }
                    }
                }
            }
            ChartPanel(chart, chartLoading, Modifier.weight(1f).fillMaxHeight())
        }
        Button(
            onClick = onOk, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Night),
            modifier = Modifier.align(Alignment.BottomStart).height(60.dp),
        ) { Text("맞아요, 고민 말하러 가기", fontSize = 18.sp) }
    }
}
