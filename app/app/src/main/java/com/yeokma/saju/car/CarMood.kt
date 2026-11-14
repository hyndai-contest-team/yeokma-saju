package com.yeokma.saju.car

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.control.FanDirectionType
import ai.pleos.playground.vehicle.constant.control.LightSwitch

/**
 * 차 연출. 처음 바꾸는 항목마다 원래 값을 한 번만 기억해 두고, 앱을 떠나면(화면이 안 보이게 되면) 모두 되돌린다.
 *  - 대기 화면: 앰비언트 무드등 색, 실내등 켬, 송풍(전원 켬, AUTO·에어컨 끔, 풍량 중간, 얼굴 방향)
 *  - 탑승 영상(V3): 5초에 실내등 끔, 6초에 앰비언트 빨강 (영상 속 차 안과 맞춘다)
 * 지원하지 않는 차나 권한이 없으면 해당 항목만 건너뛴다.
 */
class CarMood(context: Context) {
    private val vehicle = Vehicle(context.applicationContext).apply { initialize() }
    private val light get() = vehicle.getLight()
    private val hvac get() = vehicle.getHVAC()
    private val undo = LinkedHashMap<String, () -> Unit>()
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    private fun keep(key: String, restore: () -> Unit) { if (key !in undo) undo[key] = restore }
    private fun SeatArea.key() = values.joinToString { it.name }

    /** 대기 화면 연출 */
    fun idle(moodColor: Int = MOOD_GOLD) {
        ambient(moodColor)
        cabin(true)
        breeze()
    }

    fun ambient(color: Int) {
        light.checkAmbientLightBrightnessColorCapabilities({ _, areas ->
            areas.forEach { area ->
                light.getAmbientLightBrightnessColor(area, { old ->
                    if (old != null) keep("ambient:" + area.key()) { light.setAmbientLightBrightnessColor(area, old, { _, _ -> }, ::warn) }
                    light.setAmbientLightBrightnessColor(area, color, { _, _ -> }, ::warn)
                }, ::warn)
            }
        }, ::warn)
    }

    fun cabin(on: Boolean) {
        light.getCabinLightSwitch({ old ->
            if (old != null) keep("cabin") { light.setCabinLightSwitch(old, {}, ::warn) }
            light.setCabinLightSwitch(if (on) LightSwitch.ON else LightSwitch.OFF, {}, ::warn)
        }, ::warn)
    }

    /** 송풍: 전원을 먼저 켠 뒤 AUTO 끄기 → 에어컨 끄기, 풍량 중간, 얼굴 방향. 항목마다 차가 지원하는 영역이 다르다. */
    fun breeze() {
        hvac.checkHvacPowerCapabilities({ areas ->
            var left = areas.size
            if (left == 0) autoOff()
            areas.forEach { area ->
                hvac.getHvacPowerState(area, { old ->
                    if (old != null) keep("power:" + area.key()) { hvac.setHvacPower(area, old, { _, _ -> }, ::warn) }
                    hvac.setHvacPower(area, true, { _, _ -> if (--left == 0) autoOff() }, { warn(it); if (--left == 0) autoOff() })
                }, { warn(it); if (--left == 0) autoOff() })
            }
        }, { warn(it); autoOff() })
    }

    /** AUTO 모드에서는 풍량과 풍향을 직접 못 바꾼다. AUTO를 먼저 끈다. */
    private fun autoOff() {
        hvac.checkHvacAutoCapabilities({ areas ->
            var left = areas.size
            if (left == 0) fan()
            areas.forEach { area ->
                hvac.getHvacAutoState(area, { old ->
                    if (old != null) keep("auto:" + area.key()) { hvac.setHvacAuto(area, old, { _, _ -> }, ::warn) }
                    hvac.setHvacAuto(area, false, { _, _ -> if (--left == 0) fan() }, { warn(it); if (--left == 0) fan() })
                }, { warn(it); if (--left == 0) fan() })
            }
        }, { warn(it); fan() })
    }

    // 전원·AUTO 변경이 차에 반영될 시간을 조금 준다
    private fun fan() = main.postDelayed(::fanNow, 800)

    private fun fanNow() {
        hvac.checkHvacAcCapabilities({ areas ->
            areas.forEach { area ->
                hvac.getHvacAcState(area, { old ->
                    if (old != null) keep("ac:" + area.key()) { hvac.setHvacAc(area, old, { _, _ -> }, ::warn) }
                    hvac.setHvacAc(area, false, { _, _ -> }, ::warn)
                }, ::warn)
            }
        }, ::warn)
        hvac.checkHvacFanSpeedCapabilities({ bounds ->
            bounds.forEach { (area, range) ->
                hvac.getHvacFanSpeed(area, { old ->
                    if (old != null) keep("fan:" + area.key()) { hvac.setHvacFanSpeed(area, old, { _, _ -> }, ::warn) }
                    // 풍량은 중간 (사용자 요청 11/15)
                    hvac.setHvacFanSpeed(area, (range.minValue + range.maxValue + 1) / 2, { _, _ -> }, ::warn)
                }, ::warn)
            }
        }, ::warn)
        hvac.checkHvacFanDirectionCapabilities({ dirs ->
            dirs.keys.forEach { area ->
                hvac.getHvacFanDirection(area, { old ->
                    if (old != null) keep("dir:" + area.key()) { hvac.setHvacFanDirection(area, old, { _, _ -> }, ::warn) }
                    hvac.setHvacFanDirection(area, FanDirectionType.FACE, { _, _ -> }, ::warn)
                }, ::warn)
            }
        }, ::warn)
    }

    fun restore() {
        main.removeCallbacksAndMessages(null)
        undo.values.toList().asReversed().forEach { runCatching(it).onFailure(::warn) }
        undo.clear()
    }

    fun release() {
        restore()
        vehicle.release()
    }

    private fun warn(e: Throwable) = Log.w(TAG, "차 연출 건너뜀: ${e.message}")

    companion object {
        const val TAG = "CarMood"
        // 차는 색을 0xRRGGBB 정수로 받는다 (흰색 = 16777215)
        /** 무드등 색: 토 · 황금빛 */
        const val MOOD_GOLD = 0xD9B26A
        /** 탑승 영상(V3)의 붉은 앰비언트 */
        const val BOARD_RED = 0xD81E1E
    }
}

/** 앱 전체에서 하나만 쓴다. 차에 연결하지 못하면 null */
val LocalCarMood = staticCompositionLocalOf<CarMood?> { null }

/** 앱이 화면에서 사라지면(ON_STOP) 차 설정을 원래대로, 앱이 끝나면 연결도 끊는다 */
@Composable
fun rememberCarMood(): CarMood? {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val mood = androidx.compose.runtime.remember { runCatching { CarMood(context) }.onFailure { Log.w(CarMood.TAG, "차 연결 실패", it) }.getOrNull() }
    DisposableEffect(owner, mood) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) runCatching { mood?.restore() } }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs); runCatching { mood?.release() } }
    }
    return mood
}
