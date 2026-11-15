package com.yeokma.saju.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.yeokma.saju.data.Chart

/** 오행 색: 목 청록, 화 주홍, 토 황금, 금 흰빛, 수 남보라 */
fun elementColor(e: String) = when (e) {
    "목" -> Color(0xFF55B99B)
    "화" -> Color(0xFFE2674A)
    "토" -> Color(0xFFD9B26A)
    "금" -> Color(0xFFE9E4D6)
    else -> Color(0xFF8193F2)
}

private val ORDER = listOf("시주", "일주", "월주", "연주")

/** 확인 화면의 명식: 시주·일주·월주·연주 네 기둥. 일주(나)는 금색 테두리 */
@Composable
fun ChartPanel(chart: Chart?, loading: Boolean, modifier: Modifier = Modifier) = Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
    Text("명식", style = TitleOnVideo.copy(fontSize = 30.sp))
    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ORDER.forEach { label ->
            val p = chart?.pillar(label)
            val me = label == "일주"
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(16.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF1A1826).copy(alpha = .85f), Ink.copy(alpha = .85f))))
                    .border(1.dp, if (me) Gold.copy(alpha = .8f) else Paper.copy(alpha = .14f), RoundedCornerShape(16.dp))
                    .padding(vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(label, color = Color(0xFFB9B7CA), fontSize = 16.sp)
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Glyph(p?.hanja?.getOrNull(0)?.toString(), p?.stem, p?.stemElement, loading)
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Glyph(p?.hanja?.getOrNull(1)?.toString(), p?.branch, p?.branchElement, loading)
                }
            }
        }
    }
    val note = when {
        chart == null && loading -> "만세력을 계산하고 있어요"
        chart == null -> "서버에 연결되면 명식이 나와요"
        chart.correctionMinutes != null -> "태어난 곳의 경도로 시간을 ${kotlin.math.abs(chart.correctionMinutes)}분 보정했어요. 시간을 모르시면 시주는 비워 두고 풀이해요."
        else -> "태어난 시간을 몰라 시주는 비워 두고 풀이해요."
    }
    Text(note, color = Paper.copy(alpha = .8f), fontSize = 15.sp)
}

@Composable
private fun Glyph(hanja: String?, hangul: String?, element: String?, loading: Boolean) = Column(horizontalAlignment = Alignment.CenterHorizontally) {
    val c = element?.let { elementColor(it) } ?: Paper.copy(alpha = .3f)
    Text(hanja ?: if (loading) "·" else "?", color = c, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 54.sp, lineHeight = 60.sp)
    if (hangul != null && element != null) Text("${if (Chart.yang(hangul)) "양" else "음"}$element", color = Paper.copy(alpha = .75f), fontSize = 14.sp)
}

/** 리포트 위쪽의 작은 명식 줄 */
@Composable
fun ChartStrip(chart: Chart) = Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
    ORDER.forEach { label ->
        val p = chart.pillar(label)
        val me = label == "일주"
        Column(
            Modifier.width(74.dp).clip(RoundedCornerShape(12.dp)).background(Ink.copy(alpha = .7f))
                .border(1.dp, if (me) Gold.copy(alpha = .8f) else Paper.copy(alpha = .14f), RoundedCornerShape(12.dp)).padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, color = Color(0xFFB9B7CA), fontSize = 13.sp)
            listOf(p?.hanja?.getOrNull(0) to p?.stemElement, p?.hanja?.getOrNull(1) to p?.branchElement).forEach { (h, e) ->
                Text(h?.toString() ?: "?", color = e?.let { elementColor(it) } ?: Paper.copy(alpha = .3f), fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 32.sp)
            }
        }
    }
}
