package com.team.chagok.core.quip

/** 엔진이 돌려준 글을 소리 내 읽을 한 줄로 다듬는다. */
object QuipText {

    /**
     * 첫 줄만 남기고 따옴표를 걷어 [maxChars] 안에 맞춘다.
     * 넘치면 글자 수로 자르지 않고 그 안의 마지막 문장 끝(. ! ? ~) → 쉼표 → 띄어쓰기에서 끊는다
     * — "다음엔 아빠 폰도 같이" 처럼 말이 중간에 끊기면 TTS 로 들었을 때 고장 난 것처럼 들린다.
     */
    fun fit(text: String, maxChars: Int): String {
        // 이모지는 TTS 가 이름을 읽거나 건너뛰어 어색하다 → 지운다 (지시해도 엔진이 자꾸 붙인다)
        val line = text.lineSequence().map { EMOJI.replace(it, "").trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
            .trim('"', '\'', '“', '”')
        if (line.length <= maxChars) return line
        val head = line.take(maxChars)
        val minKeep = maxChars / 2
        // 짧아도 문장이 끝나는 자리가 제일 자연스럽다 → 쉼표·띄어쓰기는 그게 없을 때만
        val cut = head.indexOfLast { it in ".!?~" }.takeIf { it >= MIN_SENTENCE }?.let { it + 1 }
            ?: head.lastIndexOf(',').takeIf { it >= minKeep }
            ?: head.lastIndexOf(' ').takeIf { it >= minKeep }
            ?: maxChars
        return head.take(cut).trimEnd(' ', ',')
    }

    /**
     * 진행자가 되받을 대답 한 마디. 첫 마디(문장부호·쉼표 앞)만 남기고 끝 문장부호는 뗀다 — "떡볶이요!" 를
     * "{answer}!" 에 넣으면 "!!" 가 된다. 두 낱말·10자를 넘으면 null: 문장째 되읽으면
     * "벨트 잘 매 줘서 고마워요, 그 대답 기다렸어요." 처럼 남의 말을 진행자 말에 붙인 꼴이 된다.
     */
    fun echoOf(answer: String): String? {
        val head = answer.trim().split(CLAUSE_END, limit = 2).first().trim().trim('"', '\'', '“', '”')
        val words = head.split(' ').count { it.isNotBlank() }
        return head.takeIf { it.isNotEmpty() && it.length <= MAX_ECHO_CHARS && words <= MAX_ECHO_WORDS }
    }

    private const val MIN_SENTENCE = 10
    private const val MAX_ECHO_CHARS = 10
    private const val MAX_ECHO_WORDS = 2
    private val CLAUSE_END = Regex("[.!?~,]")

    /** 그림 문자·기호 이모지·변형 선택자·ZWJ (코드 포인트 범위). 한글·문장부호는 건드리지 않는다. */
    private val EMOJI = Regex("[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{FE0F}\\x{200D}]")
}
