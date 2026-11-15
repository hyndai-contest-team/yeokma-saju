package com.yeokma.saju.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeokma.saju.car.CarEffects

/** 오른쪽 위 설정(톱니바퀴) 버튼 */
@Composable
fun SettingsButton(modifier: Modifier = Modifier, onClick: () -> Unit) = Box(
    modifier.size(56.dp).clip(CircleShape).background(Ink.copy(alpha = .55f))
        .border(1.dp, Paper.copy(alpha = .28f), CircleShape)
        .semantics { contentDescription = "차량 연출 설정" }
        .clickable(role = Role.Button, onClick = onClick),
    contentAlignment = Alignment.Center,
) {
    Canvas(Modifier.size(28.dp)) {
        val c = Offset(size.width / 2, size.height / 2); val r = size.minDimension / 2
        repeat(8) { i -> rotate(i * 45f, c) { drawRect(Paper, topLeft = Offset(c.x - r * .16f, c.y - r), size = androidx.compose.ui.geometry.Size(r * .32f, r * .4f)) } }
        drawCircle(Paper, radius = r * .68f, center = c)
        drawCircle(Ink, radius = r * .3f, center = c)
    }
}

/** 가운데 모달: 앰비언트 라이트, 실내등, 송풍을 켜고 끈다. 끄면 앱이 그 장치를 건드리지 않는다 */
@Composable
fun CarSettingsDialog(onClose: () -> Unit) = Box(
    Modifier.fillMaxSize().background(Color.Black.copy(alpha = .6f))
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
    contentAlignment = Alignment.Center,
) {
    Column(
        Modifier.width(560.dp).clip(RoundedCornerShape(24.dp)).background(Night)
            .border(1.dp, Gold.copy(alpha = .5f), RoundedCornerShape(24.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(horizontal = 32.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("차량 연출 설정", color = Paper, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("끄면 역마사주가 그 장치를 바꾸지 않아요", color = Paper.copy(alpha = .7f), fontSize = 17.sp)
        CarEffects.Kind.entries.forEach { k ->
            val on = CarEffects.allowed(k)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Ink.copy(alpha = .6f))
                    .clickable { CarEffects.set(k, !on) }.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(k.label, color = Paper, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Text(k.desc, color = Paper.copy(alpha = .65f), fontSize = 15.sp)
                }
                Switch(
                    checked = on, onCheckedChange = { CarEffects.set(k, it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Night, checkedTrackColor = Gold, uncheckedThumbColor = Paper, uncheckedTrackColor = Ink),
                )
            }
        }
        Button(
            onClick = onClose, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Night),
            modifier = Modifier.align(Alignment.End).height(56.dp),
        ) { Text("닫기", fontSize = 18.sp) }
    }
}
