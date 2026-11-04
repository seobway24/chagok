package com.team.chagok.content

import com.team.chagok.core.model.Corner
import com.team.chagok.core.model.SignalCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 두 번째 탈 때 같은 질문을 듣지 않는지 — 회차 번호로 질문이 돈다. */
class QuestionRotationTest {

    private val corners = BuiltinShow.basicPack.corners

    @Test
    fun `every corner has at least six questions`() {
        corners.forEach { corner ->
            assertTrue("${corner.id} 질문 수", corner.variants.size + 1 >= 6)
        }
    }

    @Test
    fun `back to back episodes never repeat a question`() {
        corners.forEach { corner ->
            (1..30).forEach { ep ->
                assertNotEquals("${corner.id} 제${ep}회", corner.callOutFor(ep), corner.callOutFor(ep + 1))
            }
        }
    }

    @Test
    fun `a corner uses every question before repeating`() {
        corners.forEach { corner ->
            val size = corner.variants.size + 1
            val asked = (13 until 13 + size).map { corner.callOutFor(it) }.toSet()
            assertEquals("${corner.id}", size, asked.size)
        }
    }

    @Test
    fun `questions inside a corner are all different and name the guest`() {
        corners.forEach { corner ->
            val all = listOf(corner.callOut) + corner.variants
            assertEquals("${corner.id} 중복", all.size, all.toSet().size)
            all.forEach { assertTrue("${corner.id}: $it", "{guest}" in it) }
        }
    }

    @Test
    fun `no banned brand word and no game wording in host lines`() {
        val lines = corners.flatMap { listOf(it.title, it.callOut) + it.variants } + BuiltinShow.callbackTemplates.values
        lines.forEach { line ->
            assertFalse(line, "이야기" in line)
            assertFalse(line, "게임" in line)
            assertFalse(line, "퀴즈" in line)
        }
    }

    @Test
    fun `corner without variants keeps its single question`() {
        val corner = Corner("x", "x", SignalCategory.QUIET, "하나뿐! {guest}")
        assertEquals("하나뿐! {guest}", corner.callOutFor(0))
        assertEquals("하나뿐! {guest}", corner.callOutFor(7))
        assertEquals("하나뿐! {guest}", corner.callOutFor(-3))
    }

    @Test
    fun `negative or zero episode still picks a question`() {
        val corner = corners.first()
        assertTrue(corner.callOutFor(0).isNotBlank())
        assertTrue(corner.callOutFor(-1).isNotBlank())
    }
}
