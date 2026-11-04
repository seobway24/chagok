package com.team.chagok.core.domain.memory

import com.team.chagok.core.model.FamilyFact
import com.team.chagok.core.model.Guest
import com.team.chagok.core.model.GuestId
import com.team.chagok.core.model.ProfileBook
import com.team.chagok.core.model.Seat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 지난 회 대답이 낱말이 아니라 문장이어도 이어 묻기 대사가 말이 되게 만든다. */
class CallbackPlannerTest {

    private val mom = Guest(GuestId("g-mom"), "엄마", Seat.FRONT_PASSENGER)
    private val nouns = mapOf("tunnel" to "지난 회 터널 토크, {guest}의 대답은 {keyword}! 그 뒤로 어떻게 됐어요?")
    private val quotes = mapOf("tunnel" to "지난 회 터널 토크, {guest}의 대답 기억나요? '{keyword}'. 그 뒤로 어떻게 됐어요?")
    private val planner = CallbackPlanner(nouns, quoteTemplates = quotes)

    private fun lineFor(keyword: String, p: CallbackPlanner = planner): String? =
        p.plan(ProfileBook(listOf(FamilyFact(mom.id, "tunnel", keyword, 12))), listOf(mom), 13)
            .singleOrNull()?.callOut?.replace("{guest}", mom.nickname)

    @Test
    fun `a noun answer fills the noun slot`() {
        assertEquals("지난 회 터널 토크, 엄마의 대답은 셀카 50장! 그 뒤로 어떻게 됐어요?", lineFor("셀카 50장"))
        assertEquals("지난 회 터널 토크, 엄마의 대답은 강릉 바다! 그 뒤로 어떻게 됐어요?", lineFor("강릉 바다"))
    }

    @Test
    fun `a clause answer is quoted instead of pasted into the noun slot`() {
        assertEquals(
            "지난 회 터널 토크, 엄마의 대답 기억나요? '아플 때 다들 죽 끓여 준 거요'. 그 뒤로 어떻게 됐어요?",
            lineFor("아플 때 다들 죽 끓여 준 거요"),
        )
        assertEquals(
            "지난 회 터널 토크, 엄마의 대답 기억나요? '느린 자장가 같아요'. 그 뒤로 어떻게 됐어요?",
            lineFor("느린 자장가 같아요"),
        )
        // 예전 규칙이 잘라 둔 조각("…끓여 준")도 이름 칸에 넣지 않는다
        assertTrue(lineFor("아플 때 다들 죽 끓여 준")!!.contains("'아플 때 다들 죽 끓여 준'"))
    }

    @Test
    fun `a clause answer without a quote template is skipped rather than said awkwardly`() {
        assertEquals(null, lineFor("아플 때 다들 죽 끓여 준 거요", CallbackPlanner(nouns)))
        assertEquals("지난 회 터널 토크, 엄마의 대답은 짜장면! 그 뒤로 어떻게 됐어요?", lineFor("짜장면", CallbackPlanner(nouns)))
    }

    @Test
    fun `noun or clause is told apart by the ending`() {
        listOf("짜장면", "공룡 박사", "강릉 바다", "셀카 50장", "평화", "공룡 박사 아니면 소방관").forEach {
            assertTrue(it, CallbackPlanner.readsAsNoun(it))
        }
        listOf("느린 자장가 같아요", "아플 때 다들 죽 끓여 준", "재밌었다", "다 좋아", "강아지 보러", "긴 대답의 앞부분…").forEach {
            assertFalse(it, CallbackPlanner.readsAsNoun(it))
        }
    }

    @Test
    fun `rule extractor keeps a long answer whole instead of cutting it mid clause`() {
        assertEquals("아플 때 다들 죽 끓여 준 거요", RuleFactExtractor.keywordOf("아플 때 다들 죽 끓여 준 거요"))
        val long = "우리 가족이 다 같이 바닷가에 가서 조개를 줍고 모래성을 쌓았던 날이요"
        val cut = RuleFactExtractor.keywordOf(long)!!
        assertTrue(cut, cut.endsWith("…"))
        assertTrue(cut, long.startsWith(cut.removeSuffix("…")))
        assertTrue(cut, cut.length <= RuleFactExtractor.MAX_KEYWORD + 1)
        assertFalse(cut, cut.removeSuffix("…").endsWith(" "))
    }
}
