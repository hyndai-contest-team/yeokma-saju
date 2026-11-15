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

    suspend fun wallet(device: String): Int = call("GET", "/api/wallet?device=$device").second.optInt("balance", 0)

    suspend fun charge(device: String, pack: Int): Int =
        call("POST", "/api/charge", JSONObject().put("device", device).put("pack", pack)).second.optInt("balance", 0)

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
