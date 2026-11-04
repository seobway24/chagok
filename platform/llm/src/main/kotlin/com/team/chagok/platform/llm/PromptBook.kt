package com.team.chagok.platform.llm

import com.team.chagok.core.quip.QuipRequest
import com.team.chagok.core.quip.QuipText

/**
 * 받아치기 프롬프트. 실존 방송·인물·유행어 흉내는 요청하지 않는다.
 *
 * 이름은 GuardedQuip 이 이미 `게스트N` 으로 바꿔서 들어온다.
 */
internal object PromptBook {

    private val skinTone = mapOf(
        "basic" to "다정하고 밝은 진행자",
        "hiphop" to "라임을 살짝 넣는 힙합 MC",
        "roast" to "장난스럽게 놀리지만 상처 주지 않는 토크쇼 진행자",
    )

    fun reaction(request: QuipRequest): String = buildString {
        appendLine("너는 함께 탄 사람들이 만드는 차 안 팟캐스트의 진행자다. 말투: ${skinTone[request.skinId] ?: skinTone.getValue("basic")}.")
        appendLine("지금 코너: ${request.cornerId}.")
        if (request.question.isNotBlank()) appendLine("진행자가 던진 질문: \"${request.question}\"")
        if (request.utterance.isBlank()) {
            appendLine("${request.speaker}이(가) 대답하지 않았다. 부담 주지 말고 다음을 기약하는 말로 넘겨라.")
        } else {
            appendLine("${request.speaker}의 대답: \"${request.utterance}\"")
            appendLine("대답을 살려 재치 있게 받아쳐라. 대답에 없는 사실이나 다른 사람 일은 지어내지 마라.")
        }
        if (request.kidSafe) appendLine("아이가 함께 듣는다. 순한 말만 쓴다.")
        append("한국어 한 문장, ${request.maxChars}자 이내. 따옴표·이모지·설명 없이 문장만.")
    }

    /** 따옴표·줄바꿈을 걷어내고, 넘치면 문장이 끝나는 자리에서 자른다. */
    fun clean(text: String, maxChars: Int): String = QuipText.fit(text, maxChars)
}
