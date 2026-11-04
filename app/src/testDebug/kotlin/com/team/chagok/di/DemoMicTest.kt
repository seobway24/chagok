package com.team.chagok.di

import com.team.chagok.content.BuiltinShow
import com.team.chagok.core.privacy.SafetyFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 리허설 대본이 기본 편성의 모든 질문에 하나씩 맞는 대답을 갖고, 문구 규칙을 지킨다. */
class DemoMicTest {

    private val all: List<String> = DemoMic.byCorner.values.flatten() + DemoMic.callbacks.values + DemoMic.FAREWELL

    @Test
    fun `every question of every corner has its own answer`() {
        BuiltinShow.basicPack.corners.forEach { corner ->
            val pool = DemoMic.byCorner[corner.id]
            assertEquals("${corner.id} 질문 수와 대답 수", 1 + corner.variants.size, pool?.size)
        }
    }

    @Test
    fun `every callback topic has a follow-up answer`() {
        assertEquals(BuiltinShow.callbackTemplates.keys, DemoMic.callbacks.keys)
    }

    @Test
    fun `answers keep the copy rules and pass the kid-safe filter`() {
        val filter = SafetyFilter(BuiltinShow.bannedWords, BuiltinShow.kidBannedWords)
        val forbidden = listOf("이야기", "라디오", "게임", "퀴즈")
        all.forEach { line ->
            assertTrue("금지어: $line", forbidden.none { it in line })
            assertTrue("짧게: $line", line.length <= 30)
            assertEquals(line, filter.accept(line, maxChars = 200, kidSafe = true))
        }
    }
}
