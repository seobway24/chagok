package com.team.chagok.core.privacy

/**
 * 엔진 출력을 읽기 전에 거른다. 통과 못 하면 null → 템플릿으로 넘어간다.
 *
 * 금칙어 목록은 형식 팩처럼 데이터로 주입한다(코드에 박지 않는다).
 */
class SafetyFilter(
    private val bannedWords: Set<String>,
    private val kidBannedWords: Set<String> = emptySet(),
    /** 진행자가 먼저 꺼내지 않을 낱말(브랜드 금지어). 출연자가 직접 한 말을 되받는 건 허용한다. */
    private val hostAvoidWords: Set<String> = emptySet(),
) {
    fun accept(line: String, maxChars: Int, kidSafe: Boolean, heard: String = ""): String? {
        val text = line.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: return null
        val banned = if (kidSafe) bannedWords + kidBannedWords else bannedWords
        if (banned.any { text.contains(it, ignoreCase = true) }) return null
        if (hostAvoidWords.any { it in text && it !in heard }) return null
        // 엔진이 대사 대신 지시문을 되읊은 경우("…한 문장 (60자 이내).") — 그대로 읽으면 방송 사고
        if (PROMPT_ECHO.containsMatchIn(text)) return null
        return text.take(maxChars)
    }

    private companion object {
        // 가린 호칭(게스트N)은 정상이다 — 이 필터는 마스킹된 글에 돈다
        // 진행자는 "멘트"·"받아쳐" 를 입에 올릴 일이 없다 → 나오면 지시문을 설명하고 있는 것
        val PROMPT_ECHO = Regex("""\d+\s*자\s*이내|한 문장|응답|멘트|받아쳐|받아치""")
    }
}
