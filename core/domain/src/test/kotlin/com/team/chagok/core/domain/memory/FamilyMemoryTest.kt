package com.team.chagok.core.domain.memory

import com.team.chagok.core.domain.show.GuestPicker
import com.team.chagok.core.domain.show.ShowInput
import com.team.chagok.core.domain.show.ShowState
import com.team.chagok.core.domain.show.ShowStateMachine
import com.team.chagok.core.model.Answer
import com.team.chagok.core.model.Corner
import com.team.chagok.core.model.EpisodeRecord
import com.team.chagok.core.model.FamilyFact
import com.team.chagok.core.model.FormatPack
import com.team.chagok.core.model.Guest
import com.team.chagok.core.model.GuestId
import com.team.chagok.core.model.ProfileBook
import com.team.chagok.core.model.Seat
import com.team.chagok.core.model.SignalCategory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilyMemoryTest {

    private val dad = Guest(GuestId("g-dad"), "아빠", Seat.DRIVER)
    private val little = Guest(GuestId("g-little"), "막내", Seat.REAR_RIGHT, isChild = true)
    private val roster = listOf(dad, little)

    private val templates = mapOf(
        "dream" to "지난 회 {guest}의 꿈은 {keyword}! 더 들려줄래요?",
        "captain" to "지난 회 캡틴 {guest}의 운전 기분은 {keyword}!",
    )
    private val planner = CallbackPlanner(templates)

    private fun answer(guest: Guest, corner: String, text: String) = Answer(guest.id, corner, "질문", text)

    @Test
    fun `rule extractor keeps the first phrase and drops filler`() {
        assertEquals("짜장면", RuleFactExtractor.keywordOf("음, 짜장면! 짬뽕은 매워"))
        assertEquals("아빠", RuleFactExtractor.keywordOf("아빠! 차만 타면 자"))
        assertEquals("공룡 박사 아니면 소방관", RuleFactExtractor.keywordOf("공룡 박사 아니면 소방관!"))
        assertNull(RuleFactExtractor.keywordOf("몰라"))
    }

    @Test
    fun `made-up facts never reach the profile`() = runBlocking {
        val answers = listOf(answer(little, "dream", "공룡 박사 아니면 소방관!"))
        // AI 가 대답에 없는 "우주비행사" 를 지어냈다 → 버리고 규칙으로 넘어간다
        val liar = FactExtractor { _, ep -> listOf(FamilyFact(little.id, "dream", "우주비행사", ep)) }
        val memory = FamilyMemoryBook(ChainFactExtractor(listOf(liar, RuleFactExtractor())), planner)

        memory.remember(EpisodeRecord(12, listOf(little.id), listOf("dream"), 0, 1, answers), roster)

        assertEquals(listOf("공룡 박사 아니면 소방관"), memory.book.value.facts.map { it.keyword })
    }

    @Test
    fun `next episode asks back once, then never again`() = runBlocking {
        val memory = FamilyMemoryBook(RuleFactExtractor(), planner)
        memory.remember(
            EpisodeRecord(12, listOf(little.id), listOf("dream"), 0, 1, listOf(answer(little, "dream", "공룡 박사!"))),
            roster,
        )

        val callbacks = memory.callbacksFor(roster, 13)
        val corner = callbacks.single()
        assertEquals(little.id, corner.forGuest)
        assertTrue(corner.callOut.contains("공룡 박사"))

        // 13회에 실제로 이어 물었으면 14회에는 다시 안 꺼낸다
        memory.remember(EpisodeRecord(13, listOf(little.id), listOf(corner.id), 0, 1), roster)
        assertTrue(memory.callbacksFor(roster, 14).isEmpty())
    }

    @Test
    fun `old facts expire`() {
        val book = ProfileBook(listOf(FamilyFact(little.id, "dream", "공룡 박사", episode = 10)))
        assertEquals(1, planner.plan(book, roster, 12).size)
        assertTrue(planner.plan(book, roster, 13).isEmpty())
    }

    @Test
    fun `driver callback waits for a stop and does not block passengers`() {
        val book = ProfileBook(
            listOf(
                FamilyFact(dad.id, "captain", "평화", 12),
                FamilyFact(little.id, "dream", "공룡 박사", 12),
            ),
        )
        val callbacks = planner.plan(book, roster, 13)
        assertEquals(listOf(little.id, dad.id), callbacks.map { it.forGuest })
        assertTrue(callbacks.last().needsDriver)

        val filler = Corner("best", "오늘의 순간", SignalCategory.QUIET, "{guest}?")
        val machine = ShowStateMachine(FormatPack("p", "p", listOf(filler)), GuestPicker())
        val driving = ShowState.OnAir(13, roster, driving = true, startedAtMillis = 0, callbacks = callbacks)

        val first = machine.reduce(driving, ShowInput.QuietDue).state as ShowState.OnAir
        assertEquals(callbacks.first().id, first.live!!.corner.id)

        // 막내 이어 묻기 끝, 아직 달리는 중 → 아빠 이어 묻기는 건너뛰고 일반 채움 코너
        val done = machine.reduce(first, ShowInput.CornerDone).state
        val next = machine.reduce(done, ShowInput.QuietDue).state as ShowState.OnAir
        assertEquals("best", next.live!!.corner.id)
    }

    @Test
    fun `memory survives an app restart and can be forgotten`() = runBlocking {
        var saved = ""
        val first = FamilyMemoryBook(RuleFactExtractor(), planner, onChange = { saved = ProfileBookCodec.encode(it) })
        first.remember(
            EpisodeRecord(12, listOf(little.id), listOf("dream"), 0, 1, listOf(answer(little, "dream", "공룡\t박사!"))),
            roster,
        )

        // 앱을 다시 켰다
        val second = FamilyMemoryBook(RuleFactExtractor(), planner, load = { ProfileBookCodec.decode(saved) })
        assertEquals(12, second.lastEpisode())
        assertEquals(listOf("공룡 박사"), second.book.value.facts.map { it.keyword })
        assertEquals(1, second.callbacksFor(roster, 13).size)

        second.forget()
        assertTrue(second.book.value.facts.isEmpty())
        assertEquals("정보만 지우고 회차 번호는 남긴다", 12, second.lastEpisode())
        assertTrue(ProfileBookCodec.decode(saved).facts.isNotEmpty()) // 저장은 onChange 가 있을 때만
        assertEquals(ProfileBook(), ProfileBookCodec.decode("깨진 파일"))
    }

    @Test
    fun `people cards are added, edited, removed and survive a restart`() = runBlocking {
        var saved = ""
        val book = FamilyMemoryBook(RuleFactExtractor(), planner, onChange = { saved = ProfileBookCodec.encode(it) })
        book.savePerson(dad)
        book.savePerson(little)
        val friend = Guest(GuestId("g-friend"), " 지우 ", Seat.REAR_CENTER)
        book.savePerson(friend)
        book.savePerson(Guest(GuestId("g-blank"), "  ", Seat.REAR_LEFT))
        assertEquals("빈 이름은 무시, 앞뒤 공백은 지운다", listOf("아빠", "막내", "지우"), book.people().map { it.nickname })

        book.savePerson(little.copy(nickname = "공룡 박사", seat = Seat.REAR_LEFT))
        assertEquals("같은 id 는 그 자리에서 바뀐다", Guest(little.id, "공룡 박사", Seat.REAR_LEFT, isChild = true), book.people()[1])

        book.remember(
            EpisodeRecord(3, listOf(little.id), listOf("dream"), 0, 1, listOf(answer(little, "dream", "공룡 박사"))),
            roster,
        )
        book.countAppearances(listOf(little.id))
        val restored = FamilyMemoryBook(RuleFactExtractor(), planner, load = { ProfileBookCodec.decode(saved) })
        assertEquals(book.people(), restored.people())
        assertEquals(1, restored.people()[1].appearances)

        restored.forget()
        assertEquals("기억 지우기는 사람 카드를 남긴다", 3, restored.people().size)

        book.removePerson(little.id)
        assertEquals(listOf("아빠", "지우"), book.people().map { it.nickname })
        assertTrue("지운 사람의 기억도 같이 사라진다", book.book.value.facts.none { it.guestId == little.id })
    }

    @Test
    fun `ai output is read line by line and names are restored`() {
        val answers = listOf(answer(little, "dream", "공룡 박사!"), answer(dad, "captain", "평화!"))
        val output = "설명: 다음과 같아요\n1|공룡 박사\n2|\"게스트1 평화\"\n9|없는 번호"
        val facts = FactPrompt.parse(output, answers, 12) { it.replace("게스트1", "아빠") }

        assertEquals(listOf("공룡 박사", "아빠 평화"), facts.map { it.keyword })
        // "아빠 평화" 는 대답에 없는 말 → 사실 확인에서 걸린다
        assertEquals(listOf(true, false), facts.map { FactCheck.grounded(it, answers) })
    }

    @Test
    fun `qr text stays small enough to scan`() {
        val many = (1..40).map { FamilyFact(little.id, "dream", "아주 긴 꿈 이야기 $it 번째", 12) }
        val cards = ProfileCards.of(ProfileBook(many), roster, mapOf("dream" to "꿈 인터뷰"))
        val text = ProfileCards.qrText(cards, 12)

        assertTrue(text.startsWith(ProfileCards.HEADER))
        assertTrue(text.toByteArray(Charsets.UTF_8).size <= ProfileCards.QR_MAX_BYTES)
        // 최신 정보가 남는다
        assertTrue(text.contains("40 번째"))
        assertFalse(text.contains("- 꿈 인터뷰 · 아주 긴 꿈 이야기 1 번째\n"))
    }
}
