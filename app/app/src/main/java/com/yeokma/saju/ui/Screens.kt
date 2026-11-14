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
private fun BottomScrim(height: Int = 320) = Box(Modifier.fillMaxSize()) {
    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(height.dp)
        .background(Brush.verticalGradient(listOf(Color.Transparent, Ink.copy(alpha = .72f), Ink.copy(alpha = .92f)))))
}

private val CardColors = listOf(Color(0xFF2FB8A6), Color(0xFFD9B26A), Color(0xFF7B93F7), Color(0xFFE8684A), Color(0xFFC9A0DC))

/** 02 오셨군요: 저장된 사람(최대 5명) 또는 새로 추가 */
@Composable
fun ProfilesScreen(saved: List<Profile>, onPick: (Profile) -> Unit, onNew: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        BottomScrim(300)
        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text("오셨군요", style = TitleOnVideo.copy(fontSize = 34.sp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                saved.forEachIndexed { i, p -> PersonCard(p, CardColors[i % CardColors.size]) { onPick(p) } }
                if (saved.size < 5) NewCard(onNew)
            }
        }
    }
}

@Composable
private fun PersonCard(p: Profile, color: Color, onClick: () -> Unit) = Row(
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
private fun NewCard(onClick: () -> Unit) = Box(
    Modifier.width(180.dp).height(92.dp).clip(RoundedCornerShape(18.dp)).background(Ink.copy(alpha = .45f))
        .border(1.dp, Gold.copy(alpha = .6f), RoundedCornerShape(18.dp)).clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
) { Text("+ 새로 추가", color = Gold, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }

/** 06 확인: 화면 전체. 줄을 누르면 그 질문으로 돌아가 고친다 */
@Composable
fun ConfirmScreen(p: Profile, onEdit: (Int) -> Unit, onOk: () -> Unit) {
    val rows = listOf(
        "이름" to p.name, "성별" to p.gender, "생년월일" to (p.birth + if (p.lunar) " (음력)" else " (양력)"),
        "태어난 시간" to p.time, "태어난 곳" to p.place, "이메일" to p.email.ifEmpty { "받지 않음" },
    )
    Box(Modifier.fillMaxSize().background(Ink.copy(alpha = .58f)).padding(horizontal = 64.dp, vertical = 40.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("이렇게 맞나요?", style = TitleOnVideo.copy(fontSize = 38.sp))
            rows.chunked(2).forEachIndexed { r, pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    pair.forEachIndexed { c, (k, v) ->
                        val idx = r * 2 + c
                        Row(
                            Modifier.weight(1f).height(84.dp).clip(RoundedCornerShape(16.dp)).background(Ink.copy(alpha = .55f))
                                .border(1.dp, Paper.copy(alpha = .14f), RoundedCornerShape(16.dp)).clickable { onEdit(idx) }.padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(k, color = Color(0xFFB9B7CA), fontSize = 17.sp)
                                Text(v, color = Paper, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text("고치기", color = Color(0xFFB9B7CA), fontSize = 17.sp)
                        }
                    }
                }
            }
        }
        Button(
            onClick = onOk, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Night),
            modifier = Modifier.align(Alignment.BottomEnd).height(60.dp),
        ) { Text("맞아요, 고민 말하러 가기", fontSize = 18.sp) }
    }
}
