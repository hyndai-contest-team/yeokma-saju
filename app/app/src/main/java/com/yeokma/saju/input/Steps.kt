package com.yeokma.saju.input

import androidx.compose.ui.text.input.KeyboardType
import com.yeokma.saju.data.Profile

enum class StepId { Name, Gender, Birth, Time, Place, Email, EmailAddress, Worry }

data class Step(
    val id: StepId,
    val question: String,
    val placeholder: String,
    val keyboard: KeyboardType,
    val optional: Boolean = false,
)

val PROFILE_STEPS = listOf(
    Step(StepId.Name, "성함이 어떻게 되세요?", "이름", KeyboardType.Text),
    Step(StepId.Gender, "성별을 알려 주세요", "여자 또는 남자", KeyboardType.Text),
    Step(StepId.Birth, "언제 태어나셨어요?", "예: 1994.03.07", KeyboardType.Number),
    Step(StepId.Time, "몇 시쯤 태어나셨어요?", "예: 13:30 (모르면 모름)", KeyboardType.Number),
    Step(StepId.Place, "어디서 태어나셨어요?", "시, 군, 구", KeyboardType.Text),
    Step(StepId.Email, "풀이를 메일로도 받아 보시겠어요?", "name@example.com (선택)", KeyboardType.Email, optional = true),
)

/** 메일을 받겠다고 하면 이어서 묻는 주소 */
val EMAIL_ADDRESS_STEP = Step(StepId.EmailAddress, "메일 주소를 알려 주세요", "name@example.com", KeyboardType.Email, optional = true)

val WORRY_STEP = Step(StepId.Worry, "지금 고민거리가 뭐예요?", "지금 고민을 적어 주세요", KeyboardType.Text)

val WORRY_EXAMPLES = listOf(
    "면접 가는 길인데 떨려서 첫마디가 안 떠올라요",
    "상견례 가는 중인데 실수할까 봐 걱정돼요",
    "중요한 계약 미팅인데 잘 풀릴지 모르겠어요",
    "오랜만에 아빠 만나러 가는데 무슨 말부터 할지 모르겠어요",
)

/** 말하거나 친 답을 저장할 값으로 바꾼다. 알아듣지 못하면 null. */
object Parse {
    private val FEMALE = listOf("여자", "여성", "여인", "여", "녀", "걸", "우먼", "레이디", "아가씨", "woman", "female", "girl", "lady", "f")
    private val MALE = listOf("남자", "남성", "사나이", "남", "맨", "보이", "man", "male", "boy", "m")

    /** "여자예요", "여성이에요", "걸", "woman" → 여자 / "남자이에요", "남성", "맨", "male" → 남자. 둘 다 나오면 먼저 말한 쪽 */
    fun gender(raw: String): String? {
        val t = raw.lowercase().replace(Regex("\\s+"), " ").trim()
        fun first(keys: List<String>, s: String) = keys.mapNotNull { k ->
            val i = if (k.length == 1 && k[0] in 'a'..'z') Regex("\\b$k\\b").find(s)?.range?.first else s.indexOf(k).takeIf { it >= 0 }
            i
        }.minOrNull()
        val f = first(FEMALE, t)
        // woman·female 안의 man·male은 남자로 세지 않는다
        val m = first(MALE, t.replace("woman", "     ").replace("female", "      "))
        return when {
            f == null && m == null -> null
            m == null -> "여자"
            f == null -> "남자"
            f <= m -> "여자"
            else -> "남자"
        }
    }

    /** 메일을 받을지: 받겠다 true, 안 받겠다 false, 모르겠으면 null */
    fun emailWant(t: String): Boolean? = when {
        Regex("않|아니|안\\s*받|싫|괜찮|됐|건너|없어|필요\\s*없|no").containsMatchIn(t.lowercase()) -> false
        Regex("받을|받아|받고|보내|좋아|그래|네|예|응|yes|ok").containsMatchIn(t.lowercase()) -> true
        else -> null
    }

    /** "1994년 3월 7일", "1994.3.7", "19940307" → 1994.03.07 */
    fun birth(t: String): String? {
        Regex("""(\d{4})\D+(\d{1,2})\D+(\d{1,2})""").find(t)?.let { m ->
            return fmt(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        }
        Regex("""^\s*(\d{4})(\d{2})(\d{2})\s*$""").find(t)?.let { m ->
            return fmt(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        }
        return null
    }

    fun lunar(t: String): Boolean? = when {
        t.contains("음력") -> true
        t.contains("양력") -> false
        else -> null
    }

    /** 말한 시각을 그대로 숫자로: "13시 30분" → 13:30, "오후 1시 반" → 13:30, "0730" → 07:30 */
    fun time(raw: String): String? {
        if (Regex("모름|몰라|모르").containsMatchIn(raw)) return "모름"
        val t = koreanNumbers(raw)
        val pm = t.contains("오후") || t.contains("저녁") || t.contains("밤")
        val am = t.contains("오전") || t.contains("새벽") || t.contains("아침")
        var h: Int? = null; var mi = 0
        Regex("""(\d{1,2})\s*[:시]\s*(\d{1,2})?""").find(t)?.let { m ->
            h = m.groupValues[1].toInt(); mi = m.groupValues[2].toIntOrNull() ?: 0
        }
        if (h == null) Regex("""^\s*(\d{2})(\d{2})\s*$""").find(t)?.let { m ->
            h = m.groupValues[1].toInt(); mi = m.groupValues[2].toInt()
        }
        var hh = h ?: return null
        if (t.contains("반") && mi == 0) mi = 30
        if (pm && hh < 12) hh += 12
        if (am && hh == 12) hh = 0
        if (hh !in 0..23 || mi !in 0..59) return null
        return "%02d:%02d".format(hh, mi)
    }

    private val SINO = mapOf('일' to 1, '이' to 2, '삼' to 3, '사' to 4, '오' to 5, '육' to 6, '칠' to 7, '팔' to 8, '구' to 9)
    private val NATIVE = listOf(
        "열한" to 11, "열두" to 12, "열세" to 13, "열네" to 14, "열다섯" to 15, "열여섯" to 16, "열일곱" to 17, "열여덟" to 18, "열아홉" to 19,
        "스물한" to 21, "스물두" to 22, "스물세" to 23, "스물네" to 24, "스무" to 20,
        "열" to 10, "한" to 1, "두" to 2, "세" to 3, "네" to 4, "다섯" to 5, "여섯" to 6, "일곱" to 7, "여덟" to 8, "아홉" to 9,
    )

    /** "십삼" → 13, "이십" → 20, "삼십오" → 35 */
    private fun sino(w: String): Int? {
        var total = 0; var cur = 0
        for (c in w) when (c) {
            '십' -> { total += (if (cur == 0) 1 else cur) * 10; cur = 0 }
            else -> cur = SINO[c] ?: return null
        }
        return total + cur
    }

    /** 음성 인식이 한글로 적은 시각을 숫자로: "십삼시 삼십분" → "13시 30분", "열한시" → "11시" */
    fun koreanNumbers(t: String): String {
        var s = Regex("""([일이삼사오육칠팔구십]+)\s*(시|분)""").replace(t) { m -> sino(m.groupValues[1])?.let { "$it${m.groupValues[2]}" } ?: m.value }
        for ((w, n) in NATIVE) s = Regex("""$w\s*시""").replace(s, "${n}시")
        return s
    }

    /** "광주에서 태어났어요" → "광주" */
    fun place(t: String): String? {
        val s = t.replace(Regex("""\s*(에서|서)?\s*(태어났[어습]?[요니다]*|태어났어|출생(했어요|했습니다|했어)?|이에요|예요|입니다|이요|요)\s*[.!]?$"""), "").trim()
        return s.ifEmpty { t.trim() }.takeIf { it.isNotEmpty() }
    }

    fun email(t: String): String? {
        if (Regex("건너|없어|안 받|괜찮").containsMatchIn(t)) return ""
        val e = t.lowercase().replace(" ", "").replace("골뱅이", "@").replace("닷", ".").replace("점", ".")
        return if (Regex("""^[^@]+@[^@]+\.[^@]+$""").matches(e)) e else null
    }

    private fun fmt(y: Int, m: Int, d: Int): String? =
        if (y in 1900..2100 && m in 1..12 && d in 1..31) "%04d.%02d.%02d".format(y, m, d) else null

    /** 단계별로 적용한다. 결과: 바뀐 프로필, 화면에 보여 줄 값. 실패하면 null */
    fun apply(step: StepId, raw: String, p: Profile): Pair<Profile, String>? {
        val t = raw.trim()
        return when (step) {
            StepId.Name -> t.takeIf { it.isNotEmpty() }?.let { p.copy(name = it) to it }
            StepId.Gender -> gender(t)?.let { p.copy(gender = it) to it }
            StepId.Birth -> birth(t)?.let { b ->
                val l = lunar(t) ?: p.lunar
                p.copy(birth = b, lunar = l) to (b + if (l) " (음력)" else " (양력)")
            }
            StepId.Time -> time(t)?.let { p.copy(time = it) to it }
            StepId.Place -> place(t)?.let { p.copy(place = it) to it }
            StepId.Email -> email(t)?.takeIf { it.isNotEmpty() }?.let { p.copy(email = it) to it } // 주소를 바로 말하면 그대로
                ?: emailWant(t)?.takeIf { !it }?.let { p.copy(email = "") to "메일은 받지 않을게요" }
            StepId.EmailAddress -> email(t)?.let { p.copy(email = it) to it.ifEmpty { "메일은 받지 않을게요" } }
            StepId.Worry -> null
        }
    }
}
