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
    fun `single line set still answers every time`() = runTest {
        val quip = TemplateQuip(book, Random(1))
        repeat(3) { assertEquals("쉿", (quip.quip(request("")) as QuipResult.Line).text) }
    }
}
