package com.yeokma.saju.input

import androidx.compose.ui.text.input.KeyboardType
import com.yeokma.saju.data.Profile

enum class StepId { Name, Gender, Birth, Time, Place, Email, Worry }

data class Step(
    val id: StepId,
    val question: String,
    val placeholder: String,
    val keyboard: KeyboardType,
    val optional: Boolean = false,
)

val PROFILE_STEPS = listOf(
    Step(StepId.Name, "성함이 어떻게 되세요?", "이름", KeyboardType.Text),
    Step(StepId.Gender, "성별을 알려 주세요", "여성 또는 남성", KeyboardType.Text),
    Step(StepId.Birth, "언제 태어나셨어요?", "예: 1994.03.07", KeyboardType.Number),
    Step(StepId.Time, "몇 시쯤 태어나셨어요?", "예: 13:30 (모르면 모름)", KeyboardType.Number),
    Step(StepId.Place, "어디서 태어나셨어요?", "시, 군, 구", KeyboardType.Text),
    Step(StepId.Email, "풀이를 메일로도 받아 보시겠어요?", "name@example.com (선택)", KeyboardType.Email, optional = true),
)

val WORRY_STEP = Step(StepId.Worry, "지금 고민거리가 뭐예요?", "지금 고민을 적어 주세요", KeyboardType.Text)

val WORRY_EXAMPLES = listOf(
    "면접 가는 길인데 떨려서 첫마디가 안 떠올라요",
    "상견례 가는 중인데 실수할까 봐 걱정돼요",
    "중요한 계약 미팅인데 잘 풀릴지 모르겠어요",
    "오랜만에 아빠 만나러 가는데 무슨 말부터 할지 모르겠어요",
)

/** 말하거나 친 답을 저장할 값으로 바꾼다. 알아듣지 못하면 null. */
object Parse {
    fun gender(t: String): String? = when {
        t.contains("여") -> "여성"
        t.contains("남") -> "남성"
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
    fun time(t: String): String? {
        if (Regex("모름|몰라|모르").containsMatchIn(t)) return "모름"
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
            StepId.Place -> t.takeIf { it.isNotEmpty() }?.let { p.copy(place = it) to it }
            StepId.Email -> email(t)?.let { p.copy(email = it) to it.ifEmpty { "메일은 받지 않을게요" } }
            StepId.Worry -> null
        }
    }
}
