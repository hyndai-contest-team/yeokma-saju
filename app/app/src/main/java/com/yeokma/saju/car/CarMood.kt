package com.yeokma.saju.car

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.control.LightSwitch

/**
 * 차 연출. 처음 바꾸는 항목마다 원래 값을 한 번만 기억해 두고, 앱을 떠나면(화면이 안 보이게 되면) 모두 되돌린다.
 *  - 대기 화면: 앰비언트 무드등 색, 실내등 켬, 송풍(전원 켬, 풍량 "중". 온도·에어컨·풍향은 사용자 설정 그대로)
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

    /**
     * 기어가 P(주차)인지. 차에서는 P일 때만 열고, 기어 값이 UNKNOWN이거나 비어 있으면 안전하게 잠근다.
     * 기어 신호 자체가 없는 기기(차가 아닌 안드로이드 에뮬레이터 등)는 처음 값 true 그대로라 잠그지 않는다.
     */
    var parked by androidx.compose.runtime.mutableStateOf(true); private set
    private val gearListener = object : ai.pleos.playground.vehicle.listener.GearSelectionListener {
        override fun onSelected(gear: ai.pleos.playground.vehicle.constant.control.VehicleGear?) { main.post { onGear(gear) } }
        override fun onFailed(e: Exception) { warn(e) }
    }
    private fun onGear(g: ai.pleos.playground.vehicle.constant.control.VehicleGear?) {
        val p = g == ai.pleos.playground.vehicle.constant.control.VehicleGear.GEAR_PARK
        Log.i(TAG, "gear $g parked=$p")
        if (parked && !p) restore()   // P를 벗어나면 바꿔 둔 조명·공조를 바로 되돌린다
        val back = !parked && p
        parked = p
        if (back) resume()            // 다시 P가 되면 잠기기 전 장면의 조명·공조를 그대로 다시 켠다
    }
    init {
        runCatching {
            val gear = vehicle.getGear()
            gear.getCurrentGearSelectionState({ g -> main.post { onGear(g) } }, ::warn)
            gear.registerGearSelection(gearListener)
        }.onFailure(::warn)
    }
    private fun SeatArea.key() = values.joinToString { it.name }

    /**
     * 좌석 센서로 운전석 말고 사람이 앉은 자리를 찾는다(조수석, 뒷좌석 …). 지원하지 않거나 실패하면 빈 목록.
     * 결과는 메인 스레드에서 한 번만 돌려준다.
     */
    fun companions(done: (List<String>) -> Unit) {
        var sent = false
        fun send(v: List<String>) = main.post { if (!sent) { sent = true; done(v) } }
        main.postDelayed({ send(emptyList()) }, 2500)
        runCatching {
            val seat = vehicle.getSeat()
            seat.checkSeatOccupancyCapabilities({ areas ->
                val others = areas.filter { a -> a.values.none { it.name == "ROW_1_LEFT" } }
                if (others.isEmpty()) { send(emptyList()); return@checkSeatOccupancyCapabilities }
                val found = java.util.Collections.synchronizedList(mutableListOf<String>())
                var left = others.size
                others.forEach { area ->
                    seat.getCurrentSeatOccupancy(area, { vacant ->
                        // Vehicle SDK 2.3.2는 빈 자리에 true, 앉은 자리에 false를 돌려준다(2026-11-15 Pleos 에뮬레이터 가상 콘솔로 확인)
                        Log.i(TAG, "seat ${area.key()} value=$vacant")
                        if (vacant == false) found += seatName(area)
                        synchronized(found) { left -= 1; if (left == 0) send(found.sorted()) }
                    }, { e -> warn(e); synchronized(found) { left -= 1; if (left == 0) send(found.sorted()) } })
                }
            }, { e -> warn(e); send(emptyList()) })
        }.onFailure { Log.w(TAG, "seat", it); send(emptyList()) }
    }

    private fun seatName(a: SeatArea) = a.values.joinToString(", ") {
        when (it.name) {
            "ROW_1_RIGHT" -> "조수석"; "ROW_1_CENTER" -> "앞좌석 가운데"
            "ROW_2_LEFT" -> "뒷좌석 왼쪽"; "ROW_2_CENTER" -> "뒷좌석 가운데"; "ROW_2_RIGHT" -> "뒷좌석 오른쪽"
            else -> "셋째 줄"
        }
    }

    /** 대기 화면 연출 */
    fun idle(moodColor: Int = MOOD_GOLD) {
        ambient(moodColor)
        cabin(true)
        breeze()
    }

    // 마지막으로 켠 장면. 잠금이 풀리면 이것을 다시 켠다
    private var sceneAmbient: Int? = null
    private var sceneCabin: Boolean? = null
    private var sceneBreeze = false

    private fun resume() {
        sceneAmbient?.let { ambient(it) }
        sceneCabin?.let { cabin(it) }
        if (sceneBreeze) breeze()
    }

    fun ambient(color: Int) {
        sceneAmbient = color
        if (!parked) return
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
        sceneCabin = on
        if (!parked) return
        light.getCabinLightSwitch({ old ->
            if (old != null) keep("cabin") { light.setCabinLightSwitch(old, {}, ::warn) }
            light.setCabinLightSwitch(if (on) LightSwitch.ON else LightSwitch.OFF, {}, ::warn)
        }, ::warn)
    }

    /** 송풍: 공조 전원을 켜고, AUTO면 잠시 끈 뒤 풍량을 "중"으로. 온도·에어컨·풍향은 건드리지 않는다. 항목마다 차가 지원하는 영역이 다르다. */
    fun breeze() {
        sceneBreeze = true
        if (!parked) return
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

    /** AUTO 모드에서는 풍량을 직접 못 바꾼다. AUTO만 잠시 끄고(원래 값은 기억) 풍량을 맞춘다. */
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
    private fun fan() = main.postDelayed(::fanNow, CAR_TASK, 800)

    /**
     * 사용자가 맞춰 둔 온도·에어컨·풍향은 그대로 둔다(바꾸지 않으니 되돌릴 필요도 없다).
     * 풍량만 기존 값을 읽어 기억해 두고 "중"(지원 범위의 가운데)으로 맞춘다. 앱을 떠나거나 P를 벗어나면 기존 값으로 돌린다.
     */
    private fun fanNow() {
        hvac.checkHvacFanSpeedCapabilities({ bounds ->
            bounds.forEach { (area, range) ->
                hvac.getHvacFanSpeed(area, { old ->
                    if (old != null) keep("fan:" + area.key()) { hvac.setHvacFanSpeed(area, old, { _, _ -> }, ::warn) }
                    hvac.setHvacFanSpeed(area, (range.minValue + range.maxValue + 1) / 2, { _, _ -> }, ::warn)
                }, ::warn)
            }
        }, ::warn)
    }

    fun restore() {
        main.removeCallbacksAndMessages(CAR_TASK)   // 차 연출 예약만 지운다(동승자 감지 결과 같은 다른 일은 그대로)
        undo.values.toList().asReversed().forEach { runCatching(it).onFailure(::warn) }
        undo.clear()
    }

    fun release() {
        runCatching { vehicle.getGear().unregisterGearSelection(gearListener) }
        restore()
        vehicle.release()
    }

    private fun warn(e: Throwable) = Log.w(TAG, "차 연출 건너뜀: ${e.message}")

    companion object {
        const val TAG = "CarMood"
        private val CAR_TASK = Any()
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
