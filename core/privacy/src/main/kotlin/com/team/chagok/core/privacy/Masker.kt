package com.team.chagok.core.privacy

/**
 * 외부로 나가는 텍스트에서 개인정보를 지운다.
 *
 * - 명부 호칭 → `게스트N` (되돌릴 수 있게 [MaskSession] 이 대응표를 쥔다)
 * - 4자리 이상 숫자열(전화번호·주소 번지) → `#`
 * - 이메일 → 제거
 */
class Masker {

    fun open(nicknames: List<String>): MaskSession {
        // 긴 호칭부터 바꿔야 "지호"가 "지호아빠"의 일부를 먼저 먹지 않는다
        val aliases = nicknames.distinct()
            .filter { it.isNotBlank() }
            .sortedByDescending { it.length }
            .mapIndexed { index, name -> name to "$ALIAS_PREFIX${index + 1}" }
        return MaskSession(aliases)
    }

    companion object {
        const val ALIAS_PREFIX = "게스트"
    }
}

/** 한 번의 요청 동안만 사는 대응표. 저장하지 않는다. */
class MaskSession internal constructor(
    private val aliases: List<Pair<String, String>>,
) {
    fun mask(text: String): String {
        var masked = EMAIL.replace(text, "")
        masked = LONG_DIGITS.replace(masked, "#")
        aliases.forEach { (name, alias) -> masked = masked.replace(name, alias) }
        return WHITESPACE.replace(masked, " ").trim()
    }

    /**
     * 엔진이 돌려준 대사의 `게스트N` 을 다시 호칭으로 바꿔 차 안에서 읽는다.
     * 조사도 호칭 받침에 맞춘다 — 엔진은 "게스트1이" 처럼 숫자 발음에 맞춰 붙이므로 "아빠이" 가 되기 쉽다.
     * 엔진이 붙인 "님" 은 뗀다 — "누나 님" 은 어색하다. 프롬프트로 막으면 그 지시를 대사로 되읊는다(10/30).
     */
    fun unmask(text: String): String {
        var restored = text
        // 게스트10 이 게스트1 보다 먼저 바뀌어야 한다
        aliases.sortedByDescending { it.second.length }.forEach { (name, alias) ->
            val base = Regex.escape(alias) + HONORIFIC
            // 1) 조사가 붙은 자리는 조사까지 고쳐서 2) 나머지는 그대로 바꾼다
            restored = Regex(base + JOSA + "(?![가-힣])")
                .replace(restored) { m -> name + josaFor(name, m.groupValues[1]) }
                .let { Regex(base).replace(it, Regex.escapeReplacement(name)) }
        }
        return restored
    }

    private companion object {
        val EMAIL = Regex("""[\w.+-]+@[\w-]+\.[\w.]+""")
        val LONG_DIGITS = Regex("""\d[\d\s-]{2,}\d""")
        val WHITESPACE = Regex("""\s+""")

        /** 받침에 따라 바뀌는 조사. 긴 것부터 맞춘다. 뒤에 한글이 더 이어지면(예: "이네요") 건드리지 않는다. */
        const val HONORIFIC = """(?:\s?님)?"""

        const val JOSA ="(이랑|랑|으로|로|이에요|예요|이야|야|이|가|은|는|을|를|과|와|아)"

        private val PAIRS = listOf(
            "이랑" to "랑", "으로" to "로", "이에요" to "예요", "이야" to "야",
            "이" to "가", "은" to "는", "을" to "를", "과" to "와", "아" to "야",
        )

        fun josaFor(name: String, josa: String): String {
            val last = name.lastOrNull() ?: return josa
            if (last !in '가'..'힣') return josa
            val coda = (last - '가') % 28
            val pair = PAIRS.firstOrNull { josa == it.first || josa == it.second } ?: return josa
            return when {
                coda == 0 -> pair.second
                // ㄹ 받침 + 으로 → 로 (예: 하늘로)
                pair.first == "으로" && coda == 8 -> pair.second
                else -> pair.first
            }
        }
    }
}
