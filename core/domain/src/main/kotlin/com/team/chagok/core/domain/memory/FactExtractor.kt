package com.team.chagok.core.domain.memory

import com.team.chagok.core.model.Answer
import com.team.chagok.core.model.FamilyFact

/**
 * 한 회차 대답에서 가족 정보를 뽑는다. 구현: [RuleFactExtractor](AI 없이), AI 카트리지(중계·Pleos LLM).
 *
 * 어느 구현이든 결과는 [FactCheck] 를 통과해야 기억에 들어간다.
 */
fun interface FactExtractor {
    suspend fun extract(answers: List<Answer>, episode: Int): List<FamilyFact>
}

/**
 * 앞의 것이 실패하거나 사실 확인을 통과한 정보가 하나도 없으면 뒤의 것. 출시 빌드는 AI 가 없어 규칙만 돈다.
 */
class ChainFactExtractor(private val extractors: List<FactExtractor>) : FactExtractor {
    override suspend fun extract(answers: List<Answer>, episode: Int): List<FamilyFact> {
        for (extractor in extractors) {
            val facts = runCatching { extractor.extract(answers, episode) }.getOrDefault(emptyList())
                .filter { FactCheck.grounded(it, answers) }
            if (facts.isNotEmpty()) return facts
        }
        return emptyList()
    }
}

/**
 * AI 없이 대답의 첫 마디를 그대로 정보로 삼는다. "짜장면! 짬뽕은 매워" → "짜장면".
 * 자연스럽지는 않지만 지어내는 일이 없다.
 */
class RuleFactExtractor(
    private val skipCorners: Set<String> = setOf("door"),
) : FactExtractor {

    override suspend fun extract(answers: List<Answer>, episode: Int): List<FamilyFact> =
        answers.filter { it.cornerId !in skipCorners }.mapNotNull { answer ->
            keywordOf(answer.text)?.let { FamilyFact(answer.guestId, answer.cornerId, it, episode) }
        }

    companion object {
        const val MAX_KEYWORD = 16

        // "음, 짜장면" 의 "음," — 뒤에 띄어쓰기·쉼표가 있어야 군말로 본다("아빠" 의 "아" 는 남긴다)
        private val LEADING_FILLER = Regex("""^(?:음+|어+|아+|그+|흠+|엄+)[\s.,…]+""")
        private val BREAK = Regex("""[.!?~,…]""")
        private val NON_ANSWERS = setOf("몰라", "몰라요", "글쎄", "글쎄요", "아니", "없어", "없어요", "비밀")

        fun keywordOf(text: String): String? {
            val first = text.trim().replace(LEADING_FILLER, "")
                .split(BREAK).map(String::trim).firstOrNull { it.isNotEmpty() } ?: return null
            if (first.length < 2 || first in NON_ANSWERS) return null
            if (first.length <= MAX_KEYWORD) return first
            return first.take(MAX_KEYWORD).substringBeforeLast(' ').takeIf { it.length >= 2 }
        }
    }
}

/** 지어낸 정보 막기: 정보의 말이 그 사람이 그 코너에서 실제로 한 대답 안에 있어야 한다(띄어쓰기·문장부호 무시). */
object FactCheck {
    private val NOISE = Regex("""[\s.,!?~…'"]""")

    fun grounded(fact: FamilyFact, answers: List<Answer>): Boolean {
        val keyword = fact.keyword.normalized()
        if (keyword.length < 2) return false
        return answers.any { it.guestId == fact.guestId && it.cornerId == fact.cornerId && keyword in it.text.normalized() }
    }

    private fun String.normalized() = replace(NOISE, "")
}
