package com.yeokma.saju.car

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 설정에서 고르는 차량 연출 허용 여부. 꺼진 항목은 앱이 차를 건드리지 않는다. 기기에 저장된다.
 */
object CarEffects {
    enum class Kind(val key: String, val label: String, val desc: String) {
        Ambient("ambient", "앰비언트 라이트", "장면마다 실내 조명 색을 바꿔요"),
        Cabin("cabin", "실내등", "대기 화면에서 켜고 탑승 장면에서 꺼요"),
        Breeze("breeze", "송풍", "풍량을 \"중\"으로 맞춰요. 온도·에어컨·풍향은 그대로예요"),
    }

    private var prefs: android.content.SharedPreferences? = null
    private val state = Kind.entries.associateWith { mutableStateOf(true) }
    /** CarMood가 듣는다: 항목을 끄면 원래 값으로 되돌리고, 켜면 지금 장면을 다시 켠다 */
    var onChange: ((Kind, Boolean) -> Unit)? = null

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("car_effects", Context.MODE_PRIVATE)
        prefs = p
        Kind.entries.forEach { state.getValue(it).value = p.getBoolean(it.key, true) }
    }

    fun allowed(kind: Kind) = state.getValue(kind).value

    fun set(kind: Kind, on: Boolean) {
        if (allowed(kind) == on) return
        state.getValue(kind).value = on
        prefs?.edit()?.putBoolean(kind.key, on)?.apply()
        onChange?.invoke(kind, on)
    }
}
