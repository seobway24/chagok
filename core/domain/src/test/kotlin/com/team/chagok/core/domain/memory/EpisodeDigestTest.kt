package com.team.chagok.core.domain.memory

import com.team.chagok.core.model.Answer
import com.team.chagok.core.model.EpisodeDigest
import com.team.chagok.core.model.EpisodeHighlight
import com.team.chagok.core.model.EpisodeRecord
import com.team.chagok.core.model.EpisodeSummary
import com.team.chagok.core.model.Guest
import com.team.chagok.core.model.GuestId
import com.team.chagok.core.model.PortraitLine
import com.team.chagok.core.model.ProfileBook
import com.team.chagok.core.model.Seat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeDigestTest {

    private val driver = Guest(GuestId("a"), "도윤", Seat.DRIVER)
    private val friend = Guest(GuestId("b"), "서연", Seat.FRONT_PASSENGER)
    private val kid = Guest(GuestId("c"), "지호", Seat.REAR_LEFT, isChild = true)
    private val adults = listOf(driver, friend)

    private fun answer(g: Guest, corner: String, text: String) = Answer(g.id, corner, "질문", text)
    private val record = EpisodeRecord(
        number = 3,
        guestIds = adults.map { it.id },
        cornerIds = listOf("weather", "dream"),
        startedAtMillis = 0,
        endedAtMillis = 1,
        answers = listOf(
            answer(friend, "balance", "저는 짬뽕이 제일 좋아요"),
            answer(driver, "captain", "평온"),
            answer(friend, "dream", "제주도에서 작은 책방을 열고 싶어요"),
        ),
    )

    private fun parse(output: String, roster: List<Guest> = adults, accept: (String) -> Boolean = { true }) =
        DigestPrompt.parse(output, record, roster, { it }, accept)

    @Test
    fun `rules name who came and count corners`() {
        val digest = DigestRules.write(record, adults)
        assertEquals("제3회, 도윤, 서연 함께 코너 2개를 했어요", digest.summary.oneLine)
        assertEquals("서연 님이 2번으로 가장 많이 말했어요", digest.summary.insights[1])
    }

    @Test
    fun `highlights keep the original words, one per person, longest first`() {
        val picks = DigestRules.highlights(record.answers)
        assertEquals(listOf("제주도에서 작은 책방을 열고 싶어요"), picks.map { it.text })
    }

    @Test
    fun `no comparison between people when a child rides`() {
        assertEquals(1, DigestRules.insights(record, adults + kid).size)
    }

    @Test
    fun `ai card is stored only when its words are in that answer`() {
        val digest = parse(
            """
            요약|짬뽕 얘기로 시작했어요
            카드|1|좋아하는 것|짬뽕
            카드|3|꿈|우주비행사
            카드|2|꿈|평온
            카드|9|추억|제주도
            카드|3|별명|책방
            """.trimIndent(),
        )
        assertNotNull(digest)
        assertEquals(
            listOf(PortraitLine(friend.id, "좋아하는 것", "짬뽕", 3), PortraitLine(driver.id, "꿈", "평온", 3)),
            digest!!.portraits,
        )
    }

    @Test
    fun `highlight is the original answer, not ai wording`() {
        val digest = parse("요약|좋은 회였어요\n명장면|3\n명장면|99")!!
        assertEquals(listOf("제주도에서 작은 책방을 열고 싶어요"), digest.summary.highlights.map { it.text })
    }

    @Test
    fun `no summary means the rules stay`() {
        assertNull(parse("카드|1|좋아하는 것|짬뽕"))
        assertNull(parse("요약|나쁜 문장") { false })
        assertNull(parse("요약|" + "가".repeat(61)))
    }

    @Test
    fun `comparison insight is dropped when a child rides`() {
        val digest = parse("요약|좋은 회였어요\n인사이트|서연 님이 가장 재밌었어요", adults + kid)!!
        assertFalse(digest.summary.insights.any { "가장" in it })
    }

    @Test
    fun `same label is replaced, other people keep theirs`() {
        val book = ProfileBook(portraits = listOf(PortraitLine(friend.id, "꿈", "책방", 1), PortraitLine(driver.id, "꿈", "평온", 1)))
        val next = book.withDigest(EpisodeDigest(EpisodeSummary(2, "요약"), listOf(PortraitLine(friend.id, "꿈", "서점", 2))))
        assertEquals(setOf("평온", "서점"), next.portraits.map { it.value }.toSet())
    }

    @Test
    fun `same episode is replaced, only recent ones stay`() {
        var book = ProfileBook()
        (1..25).forEach { book = book.withDigest(EpisodeDigest(EpisodeSummary(it, "규칙"))) }
        book = book.withDigest(EpisodeDigest(EpisodeSummary(25, "AI")))
        assertEquals(ProfileBook.MAX_EPISODES, book.episodes.size)
        assertEquals("AI", book.episodes.last().oneLine)
        assertEquals(6, book.episodes.first().number)
    }

    @Test
    fun `removing a person removes their card lines and highlights`() {
        val book = ProfileBook(
            portraits = listOf(PortraitLine(friend.id, "꿈", "책방", 1)),
            episodes = listOf(EpisodeSummary(1, "요약", listOf(EpisodeHighlight(friend.id, "dream", "책방")))),
        ).withoutPerson(friend.id)
        assertTrue(book.portraits.isEmpty())
        assertTrue(book.episodes.single().highlights.isEmpty())
    }

    @Test
    fun `digest survives save and load, old files still open`() {
        val book = ProfileBook(
            lastEpisode = 2,
            portraits = listOf(PortraitLine(friend.id, "꿈", "책방", 2)),
            episodes = listOf(EpisodeSummary(2, "요약", listOf(EpisodeHighlight(friend.id, "dream", "책방 열기")), listOf("대답 3개"))),
        )
        assertEquals(book, ProfileBookCodec.decode(ProfileBookCodec.encode(book)))
        assertEquals(5, ProfileBookCodec.decode("chagok-memory v1\nE\t5\n").lastEpisode)
    }

    @Test
    fun `card shows tidy lines first and never repeats a word`() {
        val book = ProfileBook(
            facts = listOf(
                com.team.chagok.core.model.FamilyFact(friend.id, "dream", "책방", 1),
                com.team.chagok.core.model.FamilyFact(friend.id, "balance", "짬뽕", 1),
            ),
            portraits = listOf(PortraitLine(friend.id, "꿈", "책방", 1)),
        )
        val card = ProfileCards.of(book, listOf(friend), mapOf("balance" to "밸런스 게임")).single()
        assertEquals(listOf("꿈 · 책방", "밸런스 게임 · 짬뽕"), card.lines)
    }
}
