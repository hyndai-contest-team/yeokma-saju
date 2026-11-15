package com.yeokma.saju.data

import android.content.Context
import com.yeokma.saju.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class Report(val title: String, val sections: List<Pair<String, String>>)

sealed interface ReadingResult {
    data class Ok(val report: Report, val balance: Int) : ReadingResult
    data class NeedPoints(val balance: Int) : ReadingResult
    data class Failed(val message: String, val refunded: Boolean) : ReadingResult
}

/** 궁합 결과: 점수(38~97), 유형(귀인·단짝·동반자·자극제·숙제), 두 사람의 명식 */
data class Match(val report: Report, val score: Int, val kind: String, val a: Chart, val b: Chart)

sealed interface MatchResult {
    data class Ok(val match: Match, val balance: Int) : MatchResult
    data class NeedPoints(val balance: Int) : MatchResult
    data class Failed(val message: String) : MatchResult
}

/** 앱이 처음 실행될 때 만든 기기 번호. 포인트 지갑을 구분한다 */
fun deviceId(context: Context): String {
    val p = context.getSharedPreferences("device", Context.MODE_PRIVATE)
    return p.getString("id", null) ?: UUID.randomUUID().toString().also { p.edit().putString("id", it).apply() }
}

/** 로컬 서버(Rust). LLM 키는 서버에만 있다. 서버가 꺼져 있으면 앱은 Offline 목업 풀이를 쓴다 */
object Api {
    const val PRICE = 990
    /** 충전 단위: (가격, 지급 회수, 더 주는 회수) */
    val PACKS = listOf(Triple(990, 1, 0), Triple(9_900, 10, 1), Triple(99_000, 100, 15))

    private suspend fun call(method: String, path: String, body: JSONObject? = null, timeoutMs: Int = 15_000): Pair<Int, JSONObject> =
        withContext(Dispatchers.IO) {
            val c = URL(BuildConfig.API_BASE + path).openConnection() as HttpURLConnection
            c.requestMethod = method
            c.connectTimeout = 8_000
            c.readTimeout = timeoutMs
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = c.responseCode
            val text = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            c.disconnect()
            code to (runCatching { JSONObject(text) }.getOrDefault(JSONObject()))
        }

    /** 만세력(무료, LLM 없음). 서버가 꺼져 있거나 실패하면 null */
    suspend fun manse(p: Profile): Chart? = runCatching {
        val body = JSONObject().put("name", p.name).put("gender", p.gender).put("birth", p.birth)
            .put("lunar", p.lunar).put("time", p.time).put("place", p.place)
        val (code, j) = call("POST", "/api/manse", body)
        if (code == 200) Chart.from(j) else null
    }.getOrNull()

    suspend fun wallet(device: String): Int = call("GET", "/api/wallet?device=$device").second.optInt("balance", 0)

    suspend fun charge(device: String, pack: Int): Int =
        call("POST", "/api/charge", JSONObject().put("device", device).put("pack", pack)).second.optInt("balance", 0)

    private fun Profile.json() = JSONObject().put("name", name).put("gender", gender).put("birth", birth)
        .put("lunar", lunar).put("time", time).put("place", place)

    private fun report(j: JSONObject): Report {
        val r = j.getJSONObject("report")
        val arr = r.getJSONArray("sections")
        return Report(r.getString("title"), (0 until arr.length()).map { arr.getJSONObject(it).let { s -> s.getString("heading") to s.getString("body") } })
    }

    /** 두 사람의 궁합. 서버가 990P를 먼저 빼고 풀이를 만든다 */
    suspend fun match(device: String, a: Profile, b: Profile): MatchResult = runCatching {
        val (code, j) = call("POST", "/api/match", JSONObject().put("device", device).put("a", a.json()).put("b", b.json()), timeoutMs = 90_000)
        when (code) {
            200 -> MatchResult.Ok(Match(report(j), j.getInt("score"), j.getString("kind"), Chart.from(j.getJSONObject("a")), Chart.from(j.getJSONObject("b"))), j.optInt("balance"))
            402 -> MatchResult.NeedPoints(j.optInt("balance"))
            else -> MatchResult.Failed(j.optString("error", "궁합을 만들지 못했어요"))
        }
    }.getOrElse { MatchResult.Failed("서버에 연결되면 궁합을 볼 수 있어요") }

    suspend fun reading(device: String, p: Profile, worry: String): ReadingResult = runCatching {
        val profile = JSONObject().put("name", p.name).put("gender", p.gender).put("birth", p.birth)
            .put("lunar", p.lunar).put("time", p.time).put("place", p.place)
        val (code, j) = call("POST", "/api/reading", JSONObject().put("device", device).put("profile", profile).put("worry", worry), timeoutMs = 90_000)
        when {
            code == 200 -> {
                val r = j.getJSONObject("report")
                val arr = r.getJSONArray("sections")
                ReadingResult.Ok(
                    Report(r.getString("title"), (0 until arr.length()).map { arr.getJSONObject(it).let { s -> s.getString("heading") to s.getString("body") } }),
                    j.optInt("balance"),
                )
            }
            code == 402 -> ReadingResult.NeedPoints(j.optInt("balance"))
            else -> ReadingResult.Failed(j.optString("error", "풀이를 만들지 못했어요"), j.has("refunded"))
        }
    }.getOrElse { ReadingResult.Failed("서버에 연결하지 못했어요", false) }
}
