package com.yeokma.saju.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 입력받은 한 사람. 날짜는 yyyy.MM.dd, 시간은 HH:mm 또는 "모름". */
data class Profile(
    val name: String = "",
    val gender: String = "",
    val birth: String = "",
    val lunar: Boolean = false,
    val time: String = "",
    val place: String = "",
    val email: String = "",
) {
    val isComplete get() = name.isNotBlank() && gender.isNotBlank() && birth.isNotBlank() && time.isNotBlank() && place.isNotBlank()
    val summary get() = listOf(
        gender.take(1), birth + if (lunar) " 음력" else " 양력", time, place.split(" ").getOrElse(0) { "" },
    ).filter { it.isNotBlank() }.joinToString(" · ")
}

/** 저장된 사람은 최대 5명. 최근에 쓴 사람이 앞에 온다. 차 안(앱 저장소)에만 둔다. */
class ProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("profiles", Context.MODE_PRIVATE)

    fun all(): List<Profile> = runCatching {
        val arr = JSONArray(prefs.getString(KEY, "[]"))
        (0 until arr.length()).map { arr.getJSONObject(it).toProfile() }
    }.getOrDefault(emptyList())

    fun save(p: Profile) {
        val rest = all().filterNot { it.name == p.name && it.birth == p.birth }
        val list = (listOf(p) + rest).take(MAX)
        prefs.edit().putString(KEY, JSONArray(list.map { it.toJson() }).toString()).apply()
    }

    private fun Profile.toJson() = JSONObject()
        .put("name", name).put("gender", gender).put("birth", birth).put("lunar", lunar)
        .put("time", time).put("place", place).put("email", email)

    private fun JSONObject.toProfile() = Profile(
        optString("name"), optString("gender"), optString("birth"), optBoolean("lunar"),
        optString("time"), optString("place"), optString("email"),
    )

    companion object {
        const val MAX = 5
        private const val KEY = "list"
    }
}
