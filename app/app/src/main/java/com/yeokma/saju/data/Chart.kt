package com.yeokma.saju.data

import org.json.JSONObject

/** 명식의 한 기둥. hanja는 "戊午"처럼 천간·지지 두 글자 */
data class Pillar(val label: String, val stem: String, val branch: String, val hanja: String, val stemElement: String, val branchElement: String)

/** 서버 만세력 결과 중 화면에 보여 줄 것 */
data class Chart(val pillars: List<Pillar>, val correctionMinutes: Int?, val trueSolarTime: String?) {
    fun pillar(label: String) = pillars.firstOrNull { it.label == label }

    companion object {
        fun from(j: JSONObject): Chart {
            val arr = j.getJSONArray("pillars")
            val ps = (0 until arr.length()).map { arr.getJSONObject(it) }.map {
                Pillar(it.getString("label"), it.getString("stem"), it.getString("branch"), it.getString("hanja"), it.getString("stem_element"), it.getString("branch_element"))
            }
            return Chart(ps, if (j.isNull("correction_minutes")) null else j.optInt("correction_minutes"), j.optString("true_solar_time").ifEmpty { null })
        }

        /** 천간·지지의 음양: 갑병무경임·자인진오신술은 양 */
        fun yang(ch: String) = ch in setOf("갑", "병", "무", "경", "임", "자", "인", "진", "오", "신", "술")
    }
}
