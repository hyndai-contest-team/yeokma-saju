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
    IntroImage(R.drawable.intro_knot, 940.0000f, 745.0000f, 240.0000f, 22.9158f),
    IntroImage(R.drawable.intro_frame, 880.0000f, 954.0000f, 360.0000f, 86.2959f),
    IntroImage(R.drawable.intro_story_01, 762.1994f, 793.0000f, 34.3109f, 54.9853f),
    IntroImage(R.drawable.intro_story_02, 798.7097f, 793.0000f, 40.9091f, 54.9853f),
    IntroImage(R.drawable.intro_story_03, 839.1789f, 793.0000f, 40.9091f, 54.9853f),
    IntroImage(R.drawable.intro_story_04, 879.6481f, 793.0000f, 35.1906f, 54.9853f),
    IntroImage(R.drawable.intro_story_05, 928.9150f, 793.0000f, 41.3490f, 54.9853f),
    IntroImage(R.drawable.intro_story_06, 970.2639f, 793.0000f, 34.3109f, 54.9853f),
    IntroImage(R.drawable.intro_story_07, 1007.6540f, 793.0000f, 40.4692f, 54.9853f),
    IntroImage(R.drawable.intro_story_08, 1059.1202f, 793.0000f, 39.1496f, 54.9853f),
    IntroImage(R.drawable.intro_story_09, 1097.8299f, 793.0000f, 38.2698f, 54.9853f),
    IntroImage(R.drawable.intro_story_10, 1135.6598f, 793.0000f, 40.9091f, 54.9853f),
    IntroImage(R.drawable.intro_story_11, 1193.7243f, 799.9162f, 34.1584f, 42.4726f),
    IntroImage(R.drawable.intro_story_12, 1230.9619f, 799.9162f, 40.8699f, 42.4726f),
    IntroImage(R.drawable.intro_story_13, 1274.9110f, 799.9162f, 39.7680f, 42.4726f),
    IntroImage(R.drawable.intro_story_14, 1317.7582f, 799.9162f, 37.3639f, 42.4726f),
    IntroImage(R.drawable.intro_subtitle_01, 793.6172f, 857.0000f, 34.1053f, 56.8421f),
    IntroImage(R.drawable.intro_subtitle_02, 844.7751f, 857.0000f, 34.6220f, 56.8421f),
    IntroImage(R.drawable.intro_subtitle_03, 884.0478f, 857.0000f, 35.1388f, 56.8421f),
    IntroImage(R.drawable.intro_subtitle_04, 922.2871f, 857.0000f, 34.6220f, 56.8421f),
    IntroImage(R.drawable.intro_subtitle_05, 962.0766f, 857.0000f, 10.3349f, 56.8421f),
    IntroImage(R.drawable.intro_subtitle_06, 986.3636f, 857.0000f, 39.2727f, 56.8421f),
    IntroImage(R.drawable.intro_subtitle_07, 1025.6364f, 857.0000f, 35.1388f, 56.8421f),
    IntroImage(R.drawable.intro_subtitle_08, 1063.8756f, 857.0000f, 34.1053f, 56.8421f),
    IntroImage(R.drawable.intro_subtitle_09, 1115.5502f, 857.0000f, 39.2727f, 56.8421f),
    IntroImage(R.drawable.intro_subtitle_10, 1155.3397f, 857.0000f, 33.5885f, 56.8421f),
    IntroImage(R.drawable.intro_subtitle_11, 1192.5455f, 857.0000f, 39.2727f, 56.8421f),
    IntroImage(R.drawable.intro_subtitle_12, 1248.3541f, 857.0000f, 33.0718f, 56.8421f),
    IntroImage(R.drawable.intro_subtitle_13, 1286.0766f, 857.0000f, 39.7895f, 56.8421f),
    IntroImage(R.drawable.intro_cta_01, 974.0000f, 975.0000f, 41.7584f, 43.6841f),
    IntroImage(R.drawable.intro_cta_02, 1018.5963f, 975.0000f, 42.6706f, 43.6841f),
    IntroImage(R.drawable.intro_cta_03, 1064.1049f, 975.0000f, 43.4814f, 43.6841f),
    IntroImage(R.drawable.intro_cta_04, 1110.4243f, 975.0000f, 35.5757f, 43.6841f),
)
