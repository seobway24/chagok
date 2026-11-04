package com.team.chagok.core.quip

import kotlin.random.Random

/**
 * 항상 켜져 있는 마지막 폴백. 네트워크 없이 0ms 로 답한다.
 *
 * 줄 고르기는 [random] 으로 주입해 테스트에서 결정적으로 만든다.
 */
class TemplateQuip(
    private val book: TemplateBook,
    private val random: Random = Random.Default,
) : QuipEngine {

    override val id: String = ID

    /**
     * 틀마다 마지막으로 쓴 차례. 안 쓴 틀 → 가장 오래전에 쓴 틀 순으로 꺼낸다 — 두 번째부터 기계처럼 들린다.
     * 묶음을 다 써도 통째로 비우지 않는다: 비우고 아무거나 고르면 방금 한 말이 바로 다시 나왔다(제14회 "하이라이트 확정!" 두 번).
     */
    private val lastUsed = mutableMapOf<String, Long>()
    private var turn = 0L

    override suspend fun quip(request: QuipRequest): QuipResult {
        val reaction = if (request.utterance.isBlank()) Reaction.SILENT else Reaction.ANSWERED
        // 짧은 대답만 되받는다. 문장 대답은 {answer} 가 없는 줄로 받아친다
        val echo = QuipText.echoOf(request.utterance)
        val line = pickFresh(book.linesFor(request.cornerId, request.skinId, reaction, echo = echo != null))
            .replace(GUEST, request.speaker)
            .replace(ANSWER, echo ?: request.utterance)
            .take(request.maxChars)
        return QuipResult.Line(line, id, latencyMs = 0)
    }

    private fun pickFresh(lines: List<String>): String {
        val oldest = lines.minOf { lastUsed[it] ?: -1L }
        val fresh = lines.filter { (lastUsed[it] ?: -1L) == oldest }
        return fresh.random(random).also { lastUsed[it] = turn++ }
    }

    companion object {
        const val ID = "template"
        const val GUEST = "{guest}"
        const val ANSWER = "{answer}"
    }
}

enum class Reaction { ANSWERED, SILENT }

/**
 * 코너 × 스킨 × 반응 별 대사 묶음.
 *
 * 찾는 순서: (코너, 스킨) → (코너, 기본 스킨) → 공용. 공용은 비어 있으면 안 된다.
 */
class TemplateBook(
    private val lines: Map<Key, List<String>>,
    private val common: Map<Reaction, List<String>>,
) {
    data class Key(val cornerId: String, val skinId: String, val reaction: Reaction)

    init {
        Reaction.entries.forEach { require(!common[it].isNullOrEmpty()) { "공용 대사 없음: $it" } }
    }

    /**
     * @param echo false 면 대답을 되받지 않는 줄({answer} 없음)만. 그 묶음에 없으면 공용에서,
     *  공용에도 없으면 원래 묶음 그대로 준다(비어 있으면 안 되므로).
     */
    fun linesFor(cornerId: String, skinId: String, reaction: Reaction, echo: Boolean = true): List<String> {
        val pool = lines[Key(cornerId, skinId, reaction)]
            ?: lines[Key(cornerId, DEFAULT_SKIN, reaction)]
            ?: common.getValue(reaction)
        if (echo) return pool
        return pool.withoutAnswer().ifEmpty { common.getValue(reaction).withoutAnswer() }.ifEmpty { pool }
    }

    private fun List<String>.withoutAnswer() = filterNot { TemplateQuip.ANSWER in it }

    companion object {
        const val DEFAULT_SKIN = "basic"
    }
}
