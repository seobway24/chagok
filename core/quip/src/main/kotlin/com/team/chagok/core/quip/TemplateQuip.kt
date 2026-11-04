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

    /** 이미 쓴 틀. 묶음을 다 쓰기 전에는 같은 문장을 다시 꺼내지 않는다 — 두 번째부터 기계처럼 들린다. */
    private val used = mutableSetOf<String>()

    override suspend fun quip(request: QuipRequest): QuipResult {
        val reaction = if (request.utterance.isBlank()) Reaction.SILENT else Reaction.ANSWERED
        val line = pickFresh(book.linesFor(request.cornerId, request.skinId, reaction))
            .replace(GUEST, request.speaker)
            .replace(ANSWER, request.utterance)
            .take(request.maxChars)
        return QuipResult.Line(line, id, latencyMs = 0)
    }

    private fun pickFresh(lines: List<String>): String {
        val fresh = lines.filterNot { it in used }.ifEmpty {
            used.removeAll(lines.toSet())
            lines
        }
        return fresh.random(random).also { used += it }
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

    fun linesFor(cornerId: String, skinId: String, reaction: Reaction): List<String> =
        lines[Key(cornerId, skinId, reaction)]
            ?: lines[Key(cornerId, DEFAULT_SKIN, reaction)]
            ?: common.getValue(reaction)

    companion object {
        const val DEFAULT_SKIN = "basic"
    }
}
