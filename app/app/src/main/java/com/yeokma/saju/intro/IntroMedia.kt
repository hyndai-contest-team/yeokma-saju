package com.yeokma.saju.intro

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * 인트로 영상 파일. res/raw에 아래 이름으로 넣으면 바로 이어진다.
 *  v1_intro.mp4  도입 (1회)
 *  v2_idle.mp4   V1 끝 장면에서 4초 무한 반복 (버튼 누를 때까지)
 *  v3_board.mp4  탑승: 차에 오르고 캐릭터 클로즈업, 대사 "오셨군요."(영상 안)
 *  v4_seated.mp4 마주 앉아 기다리는 루프
 * 아직 없는 파일은 V1 마지막 장면(assets/v1_end.avif)으로 대신한다.
 */
object IntroMedia {
    const val V1 = "v1_intro"
    const val V2 = "v2_idle"
    const val V3 = "v3_board"
    const val V4 = "v4_seated"

    @SuppressLint("DiscouragedApi")
    fun raw(context: Context, name: String): Int? =
        context.resources.getIdentifier(name, "raw", context.packageName).takeIf { it != 0 }

    @Composable fun v1(): Int? = find(V1)
    @Composable fun v2Idle(): Int? = find(V2)
    @Composable fun v3Board(): Int? = find(V3)
    @Composable fun v4Seated(): Int? = find(V4)

    @Composable
    private fun find(name: String): Int? {
        val ctx = LocalContext.current
        return remember(name) { raw(ctx, name) }
    }
}
