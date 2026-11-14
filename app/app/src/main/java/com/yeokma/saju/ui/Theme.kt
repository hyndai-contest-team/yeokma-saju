package com.yeokma.saju.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF07080F)
val Paper = Color(0xFFEDE8DC)
val Gold = Color(0xFFD9B26A)
val Night = Color(0xFF14121F)

/** 영상 위 글씨: 그림자로만 읽히게 하고 판을 깔지 않는다. */
private val onVideoShadow = Shadow(color = Color(0xBF000000), offset = Offset(0f, 2f), blurRadius = 14f)

val TitleOnVideo = TextStyle(
    color = Paper, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
    fontSize = 40.sp, shadow = onVideoShadow,
)
val LineOnVideo = TitleOnVideo.copy(fontSize = 30.sp)
