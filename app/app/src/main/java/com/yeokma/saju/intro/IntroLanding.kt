package com.yeokma.saju.intro

import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** V2 대기 화면. 영상은 다른 화면처럼 화면을 꽉 채우고, 제목·버튼 그림은 그 위에 화면에 맞춰 얹는다. */
@Composable
internal fun IntroLanding(video: Int?, onStart: () -> Unit) {
    val resources = LocalResources.current
    val images by produceState<Map<Int, ImageBitmap>?>(null, resources) {
        value = withContext(Dispatchers.IO) {
            (IntroTitleImages + IntroDetailImages).associate { asset ->
                asset.resource to BitmapFactory.decodeResource(resources, asset.resource).asImageBitmap()
            }
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (video != null) VideoLayer(resId = video, loop = true) else PosterImage()
        images?.let { IntroArtwork(it, onStart) }
    }
}

@Composable
private fun IntroArtwork(images: Map<Int, ImageBitmap>, onStart: () -> Unit) {
    var reveal by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { reveal = true }

    // 驛 → 馬 → 四 → 柱, then all supporting elements together.
    val detailsAlpha by animateFloatAsState(
        targetValue = if (reveal) 1f else 0f,
        animationSpec = tween(durationMillis = 600, delayMillis = 1950),
        label = "introDetails",
    )
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // 1920×1080 배치를 화면 안에 다 들어오게 줄이고, 가로는 가운데·세로는 아래에 붙인다(영상의 아래쪽에 글씨가 오게)
        val scale = minOf(maxWidth.value / 1920f, maxHeight.value / 1080f)
        val ox = (maxWidth.value - 1920f * scale) / 2f
        val oy = maxHeight.value - 1080f * scale
        // 세로 한자 제목은 원래 디자인처럼 화면 높이 전체에 걸치게 높이 기준으로 맞추고 왼쪽에 붙인다
        val titleScale = maxHeight.value / 1080f
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    .53f to Color.Transparent,
                    .67f to Color.Black.copy(alpha = .16f),
                    1f to Color.Black.copy(alpha = .48f),
                )
            )
        )
        Box(Modifier.fillMaxSize().semantics { contentDescription = "역마사주" }) {
            IntroTitleImages.forEachIndexed { index, asset ->
                val alpha by animateFloatAsState(
                    targetValue = if (reveal) 1f else 0f,
                    animationSpec = tween(durationMillis = 680, delayMillis = 180 + index * 310),
                    label = "introTitle$index",
                )
                IntroAsset(asset, images.getValue(asset.resource), titleScale, 0f, 0f, alpha)
            }
        }
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = detailsAlpha }
                .semantics {
                    if (detailsAlpha >= .999f) {
                        contentDescription = "역마살의 운명을 타고난 역마아씨. 길 위에서, 당신의 운명을 읽다."
                    }
                }
        ) {
            IntroDetailImages.forEach { IntroAsset(it, images.getValue(it.resource), scale, ox, oy) }
        }
        // Keep the visual composition fixed while retaining a 48dp minimum touch target.
        // Invisible / fading-in controls cannot start boarding or receive accessibility focus.
        if (detailsAlpha >= .999f) {
            val touchWidth = maxOf(360f * scale, 48f)
            val touchHeight = maxOf(96f * scale, 48f)
            Box(
                Modifier
                    .offset(
                        x = (ox + 1060f * scale - touchWidth / 2f).dp,
                        y = (oy + 1002f * scale - touchHeight / 2f).dp,
                    )
                    .size(touchWidth.dp, touchHeight.dp)
                    .semantics { contentDescription = "탑승하기" }
                    .clickable(role = Role.Button, onClick = onStart)
            )
        }
    }
}

@Composable
private fun IntroAsset(asset: IntroImage, bitmap: ImageBitmap, scale: Float, ox: Float, oy: Float, alpha: Float = 1f) {
    Image(
        bitmap = bitmap,
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = Modifier
            .offset((ox + asset.x * scale).dp, (oy + asset.y * scale).dp)
            .size((asset.width * scale).dp, (asset.height * scale).dp)
            .graphicsLayer { this.alpha = alpha },
    )
}
