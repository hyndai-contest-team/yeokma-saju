package com.yeokma.saju.intro

import android.graphics.ImageDecoder
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.RawResourceDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW

/**
 * 화면 전체를 채우는 영상 한 장. 글씨는 이 위에 그림자로만 얹는다.
 * TextureView를 써서 확대, 투명도 같은 Compose 변형이 영상에도 먹게 한다.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoLayer(
    resId: Int,
    loop: Boolean,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
    onStarted: () -> Unit = {},
    onEnded: () -> Unit = {},
    blur: Float = 0f,
) {
    val context = LocalContext.current
    val started by rememberUpdatedState(onStarted)
    val ended by rememberUpdatedState(onEnded)
    val player = remember(resId) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(RawResourceDataSource.buildRawResourceUri(resId)))
            repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            volume = if (muted) 0f else 1f
            playWhenReady = true
            prepare()
        }
    }
    // 주행 중(기어가 P가 아님)에는 영상과 소리를 멈춘다
    val locked = com.yeokma.saju.car.DriveLock.locked
    androidx.compose.runtime.LaunchedEffect(player, locked) { player.playWhenReady = !locked }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() = started()
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) ended()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    // 화면 비율이 16:9가 아니어도 늘이지 않고, 꽉 채운 뒤 넘치는 쪽을 잘라 낸다 (ContentScale.Crop과 같게)
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
        val boxRatio = maxWidth / maxHeight
        val w = if (boxRatio > VIDEO_RATIO) maxWidth else maxHeight * VIDEO_RATIO
        val h = if (boxRatio > VIDEO_RATIO) maxWidth / VIDEO_RATIO else maxHeight
        PlayerSurface(player = player, modifier = Modifier.requiredSize(w, h).then(if (blur > 0f) Modifier.blur(blur.dp) else Modifier), surfaceType = SURFACE_TYPE_TEXTURE_VIEW)
    }
}

private const val VIDEO_RATIO = 16f / 9f

/** 정지 장면(AVIF). 영상이 아직 없을 때 대신 보여 준다. v1_end: V1 마지막 장면, v3_end: V3 마지막 장면(클로즈업) */
@Composable
fun PosterImage(modifier: Modifier = Modifier, asset: String = "v1_end.avif") {
    val context = LocalContext.current
    val bitmap: ImageBitmap = remember(asset) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.assets, asset)).asImageBitmap()
    }
    Image(bitmap = bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier.fillMaxSize())
}
