package com.yeokma.saju.intro

import androidx.annotation.DrawableRes
import com.yeokma.saju.R

/** Coordinates are in the 1920 × 1080 intro canvas. See assets/intro/layout.json. */
internal data class IntroImage(@DrawableRes val resource: Int, val x: Float, val y: Float, val width: Float, val height: Float)

internal val IntroTitleImages = listOf(
    IntroImage(R.drawable.intro_title_01, 74.8619f, 79.1326f, 246.9613f, 232.4586f),
    IntroImage(R.drawable.intro_title_02, 88.1215f, 320.7072f, 219.6133f, 211.3260f),
    IntroImage(R.drawable.intro_title_03, 87.7072f, 568.4972f, 227.4862f, 147.0994f),
    IntroImage(R.drawable.intro_title_04, 72.7901f, 741.7017f, 250.6906f, 199.7238f),
)

internal val IntroDetailImages = listOf(
    IntroImage(R.drawable.intro_center_original, 660f, 650f, 800f, 319.9193f),
    IntroImage(R.drawable.intro_button_original, 845f, 930f, 430f, 143.3333f),
)
