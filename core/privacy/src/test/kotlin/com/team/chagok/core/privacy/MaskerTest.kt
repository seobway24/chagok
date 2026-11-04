package com.team.chagok.core.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MaskerTest {

    private val session = Masker().open(listOf("지호", "지호아빠", "캡틴"))

    @Test
    fun `nicknames become guest aliases and come back`() {
        val masked = session.mask("지호아빠랑 지호가 캡틴한테")

        assertFalse(masked.contains("지호"))
        assertEquals("지호아빠랑 지호가 캡틴한테", session.unmask(masked))
    }

    @Test
    fun `phone numbers and emails are removed`() {
        val masked = session.mask("010-1234-5678 a.b@mail.com 연락해")

        assertEquals("# 연락해", masked)
    }

    @Test
    fun `particles follow the real nickname, not the alias`() {
        val family = Masker().open(listOf("아빠", "누나", "캡틴"))
        val alias = { name: String -> family.mask(name) }

        // 엔진은 "게스트N" 발음에 맞춰 조사를 붙인다 → 호칭 받침으로 다시 맞춘다
        assertEquals("아빠가 셀카를", family.unmask("${alias("아빠")}이 셀카를"))
        assertEquals("누나는 짜장면", family.unmask("${alias("누나")}은 짜장면"))
        assertEquals("캡틴이 운전", family.unmask("${alias("캡틴")}가 운전"))
        assertEquals("캡틴을 응원", family.unmask("${alias("캡틴")}를 응원"))
        // 조사가 아닌 말이 이어지면 그대로
        assertEquals("누나의 꿈", family.unmask("${alias("누나")}의 꿈"))
        // 엔진이 붙인 "님" 은 떼고 조사도 맞춘다
        assertEquals("누나, 짜장면!", family.unmask("${alias("누나")} 님, 짜장면!"))
        assertEquals("아빠가 최고", family.unmask("${alias("아빠")}님이 최고"))
    }

    @Test
    fun `short numbers stay`() {
        assertEquals("100점", session.mask("100점"))
    }
}
