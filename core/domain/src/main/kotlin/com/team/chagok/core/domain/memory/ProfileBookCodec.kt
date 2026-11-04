package com.team.chagok.core.domain.memory

import com.team.chagok.core.model.EpisodeHighlight
import com.team.chagok.core.model.EpisodeSummary
import com.team.chagok.core.model.FamilyFact
import com.team.chagok.core.model.Guest
import com.team.chagok.core.model.GuestId
import com.team.chagok.core.model.PortraitLine
import com.team.chagok.core.model.ProfileBook
import com.team.chagok.core.model.Seat

/**
 * [ProfileBook] ↔ 글. 의존성 없는 줄 단위 형식이라 JVM 테스트로 바로 검증하고, 암호화는 저장하는 쪽이 한다.
 *
 * ```
 * chagok-memory v1
 * E	13
 * F	g-little	dream	12	공룡 박사
 * C	g-little|dream|공룡 박사
 * P	g-little	막내	REAR_RIGHT	1	12        (id · 호칭 · 좌석 · 아이 여부 · 출연 횟수)
 * R	g-little	꿈	12	공룡 박사           (사람 카드 줄)
 * S	12	오늘은 공룡 얘기로 시작했어요        (회차 한 줄 요약)
 * H	12	g-little	dream	공룡 박사가 될 거야 (명장면, S 뒤)
 * I	12	대답 5개가 나왔어요                 (인사이트, S 뒤)
 * ```
 * 모르는 줄은 건너뛴다 — 나중 버전이 줄 종류를 늘려도 앞 버전이 깨지지 않는다.
 */
object ProfileBookCodec {

    private const val HEADER = "chagok-memory v1"
    private val UNSAFE = Regex("""[\t\r\n]""")

    fun encode(book: ProfileBook): String = buildString {
        appendLine(HEADER)
        appendLine("E\t${book.lastEpisode}")
        book.facts.forEach { appendLine("F\t${it.guestId.value.safe()}\t${it.cornerId.safe()}\t${it.episode}\t${it.keyword.safe()}") }
        book.calledBack.forEach { appendLine("C\t${it.safe()}") }
        book.people.forEach {
            appendLine("P\t${it.id.value.safe()}\t${it.nickname.safe()}\t${it.seat.name}\t${if (it.isChild) 1 else 0}\t${it.appearances}")
        }
        book.portraits.forEach { appendLine("R\t${it.guestId.value.safe()}\t${it.label.safe()}\t${it.episode}\t${it.value.safe()}") }
        book.episodes.forEach { e ->
            appendLine("S\t${e.number}\t${e.oneLine.safe()}")
            e.highlights.forEach { appendLine("H\t${e.number}\t${it.guestId.value.safe()}\t${it.cornerId.safe()}\t${it.text.safe()}") }
            e.insights.forEach { appendLine("I\t${e.number}\t${it.safe()}") }
        }
    }

    /** 머리줄이 다르거나 비어 있으면 빈 기억. 저장 파일이 깨져도 방송은 시작돼야 한다. */
    fun decode(text: String): ProfileBook {
        val lines = text.lines()
        if (lines.firstOrNull() != HEADER) return ProfileBook()
        var lastEpisode = 0
        val facts = mutableListOf<FamilyFact>()
        val calledBack = mutableSetOf<String>()
        val people = mutableListOf<Guest>()
        val portraits = mutableListOf<PortraitLine>()
        val episodes = linkedMapOf<Int, EpisodeSummary>()
        fun onEpisode(number: String, update: (EpisodeSummary) -> EpisodeSummary) {
            val n = number.toIntOrNull() ?: return
            episodes[n]?.let { episodes[n] = update(it) }
        }
        lines.drop(1).forEach { line ->
            val cols = line.split('\t')
            when (cols[0]) {
                "E" -> lastEpisode = cols.getOrNull(1)?.toIntOrNull() ?: lastEpisode
                "F" -> if (cols.size == 5) {
                    cols[3].toIntOrNull()?.let { facts += FamilyFact(GuestId(cols[1]), cols[2], cols[4], it) }
                }
                "C" -> cols.getOrNull(1)?.let { calledBack += it }
                "P" -> person(cols)?.let { people += it }
                "R" -> if (cols.size == 5) {
                    cols[3].toIntOrNull()?.let { portraits += PortraitLine(GuestId(cols[1]), cols[2], cols[4], it) }
                }
                "S" -> if (cols.size == 3) {
                    cols[1].toIntOrNull()?.let { episodes[it] = EpisodeSummary(it, cols[2]) }
                }
                "H" -> if (cols.size == 5) {
                    onEpisode(cols[1]) { it.copy(highlights = it.highlights + EpisodeHighlight(GuestId(cols[2]), cols[3], cols[4])) }
                }
                "I" -> if (cols.size == 3) onEpisode(cols[1]) { it.copy(insights = it.insights + cols[2]) }
            }
        }
        return ProfileBook(facts, calledBack, lastEpisode, people, portraits, episodes.values.toList())
    }

    private fun person(cols: List<String>): Guest? {
        if (cols.size != 6 || cols[2].isBlank()) return null
        val seat = Seat.entries.firstOrNull { it.name == cols[3] } ?: return null
        return Guest(GuestId(cols[1]), cols[2], seat, appearances = cols[5].toIntOrNull() ?: 0, isChild = cols[4] == "1")
    }

    private fun String.safe() = replace(UNSAFE, " ")
}
