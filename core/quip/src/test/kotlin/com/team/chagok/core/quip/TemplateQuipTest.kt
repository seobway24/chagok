package com.team.chagok.core.quip

import com.team.chagok.core.model.SignalCategory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

/** 같은 받아치기 문장이 연달아 나오면 기계처럼 들린다. 묶음을 다 쓰기 전엔 반복하지 않는다. */
class TemplateQuipTest {

    private val book = TemplateBook(
        lines = emptyMap(),
        common = mapOf(
            Reaction.ANSWERED to listOf("A {answer}", "B {answer}", "C {answer}"),
            Reaction.SILENT to listOf("쉿"),
        ),
    )

    private fun request(answer: String) =
        QuipRequest("dream", "basic", "막내", answer, SignalCategory.QUIET, kidSafe = true)

    @Test
    fun `no line repeats until every line in the set was used`() = runTest {
        val quip = TemplateQuip(book, Random(7))

        val firstRound = (1..3).map { (quip.quip(request("x")) as QuipResult.Line).text.first() }
        assertEquals(setOf('A', 'B', 'C'), firstRound.toSet())

        // 다 쓰면 다시 처음부터 돈다
        val secondRound = (1..3).map { (quip.quip(request("x")) as QuipResult.Line).text.first() }
        assertEquals(setOf('A', 'B', 'C'), secondRound.toSet())
    }

    @Test
    fun `starting a new round never picks a line said in the last round`() = runTest {
        (0..20).forEach { seed ->
            val quip = TemplateQuip(book, Random(seed))
            val said = (1..12).map { (quip.quip(request("x")) as QuipResult.Line).text.first() }
            said.windowed(3).forEach { assertEquals("seed $seed: $said", 3, it.toSet().size) }
        }
    }

    private val mixed = TemplateBook(
        lines = emptyMap(),
        common = mapOf(
            Reaction.ANSWERED to listOf("{answer}! 그거 좋다.", "그 대답 기다렸어요, {guest}!"),
            Reaction.SILENT to listOf("쉿"),
        ),
    )

    private suspend fun TemplateQuip.say(answer: String) = (quip(request(answer)) as QuipResult.Line).text

    @Test
    fun `a short answer is echoed once, without doubled punctuation`() = runTest {
        val quip = TemplateQuip(mixed, Random(3))
        val lines = (1..2).map { quip.say("떡볶이요!") }
        assertEquals(setOf("떡볶이요! 그거 좋다.", "그 대답 기다렸어요, 막내!"), lines.toSet())
    }

    @Test
    fun `a sentence answer is not read back — the host reacts without it`() = runTest {
        val quip = TemplateQuip(mixed, Random(3))
        listOf("벨트 잘 매 줘서 고마워요", "오늘은 팔 점이요. 빗소리가 좋아요", "다 같이 크게 웃은 순간이요").forEach { answer ->
            repeat(3) { assertEquals(answer, "그 대답 기다렸어요, 막내!", quip.say(answer)) }
        }
    }

    @Test
    fun `only the first short clause of a longer answer is echoed`() = runTest {
        val echoOnly = TemplateBook(emptyMap(), mapOf(Reaction.ANSWERED to listOf("{answer}! 그거 좋다."), Reaction.SILENT to listOf("쉿")))
        assertEquals("짜장면이요! 그거 좋다.", TemplateQuip(echoOnly).say("짜장면이요. 짬뽕은 매워요"))
    }

    @Test
    fun `single line set still answers every time`() = runTest {
        val quip = TemplateQuip(book, Random(1))
        repeat(3) { assertEquals("쉿", (quip.quip(request("")) as QuipResult.Line).text) }
    }
}
