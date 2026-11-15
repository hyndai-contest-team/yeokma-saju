package com.yeokma.saju.car

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 기어가 P가 아니면 true. 영상은 멈추고, 음성 입력은 끄고, 화면은 잠금 안내로 덮는다 */
object DriveLock {
    var locked by mutableStateOf(false)
}
