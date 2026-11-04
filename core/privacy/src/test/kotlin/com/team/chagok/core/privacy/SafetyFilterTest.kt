package com.team.chagok.core.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SafetyFilterTest {

    private val filter = SafetyFilter(bannedWords = setOf("바보"), kidBannedWords = setOf("담배"))

    @Test
    fun `normal masked line passes`() {
        assertEquals("게스트2, 다음엔 꼭 들려주세요!", filter.accept("게스트2, 다음엔 꼭 들려주세요!", 60, kidSafe = true))
    }

    @Test
    fun `engine echoing the instructions is rejected`() {
        // 10/30 에뮬레이터: 지시문이 그대로 대사로 나왔다
        assertNull(filter.accept("게스트1 캡틴, 다음에 편하게 들려주세요 하는 식으로 한 문장 (60자 이내).", 60, kidSafe = true))
        assertNull(filter.accept("평화를 살려 받아쳐 주는 멘트 한 줄이에요.", 60, kidSafe = true))
        assertNull(filter.accept("웃음 가득한 한 문장 응답이 필요해요.", 60, kidSafe = true))
    }

    @Test
    fun `host never brings up a brand banned word but may echo the guest`() {
        val brand = SafetyFilter(emptySet(), hostAvoidWords = setOf("이야기"))
        assertNull(brand.accept("재밌는 이야기네요!", 60, kidSafe = true, heard = "공룡이요"))
        assertEquals("공룡 이야기, 최고예요!", brand.accept("공룡 이야기, 최고예요!", 60, kidSafe = true, heard = "공룡 이야기요"))
    }

    @Test
    fun `kid words only apply when kids listen`() {
        assertNull(filter.accept("담배 냄새", 60, kidSafe = true))
        assertEquals("담배 냄새", filter.accept("담배 냄새", 60, kidSafe = false))
    }
}
