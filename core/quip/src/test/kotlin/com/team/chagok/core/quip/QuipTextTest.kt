package com.team.chagok.core.quip

import org.junit.Assert.assertEquals
import org.junit.Test

class QuipTextTest {

    @Test
    fun `short line passes with quotes stripped`() {
        assertEquals("짜장면 승!", QuipText.fit("\"짜장면 승!\"\n설명입니다", 60))
    }

    @Test
    fun `emoji are removed before reading aloud`() {
        assertEquals("강아지 보러 가는 길, 정말 좋은 순간이네!", QuipText.fit("강아지 보러 가는 길, 정말 좋은 순간이네! 🐶✨", 60))
    }

    @Test
    fun `long line is cut at a sentence end, not mid-word`() {
        val line = "아빠가 누나 폰으로 셀카를 오십 장이나 찍었다니 눈에 선하네요! 다음엔 아빠 폰도 같이 들고 가족사진 찍어요"
        assertEquals("아빠가 누나 폰으로 셀카를 오십 장이나 찍었다니 눈에 선하네요!", QuipText.fit(line, 50))
    }

    @Test
    fun `short first sentence beats a mid-clause cut`() {
        val line = "공룡 박사도 좋고 소방관도 멋져요! 그럼 막내는 공룡 깨우는 소방관이 되어서 불도 끄고 화석도 찾는 거예요"
        assertEquals("공룡 박사도 좋고 소방관도 멋져요!", QuipText.fit(line, 50))
    }

    @Test
    fun `falls back to comma when there is no sentence end`() {
        val line = "공룡 박사에 소방관이라니 막내 꿈이 씩씩하네, 그럼 공룡 소방관이 되어서 공룡 시대까지 출동하는 거야"
        assertEquals("공룡 박사에 소방관이라니 막내 꿈이 씩씩하네", QuipText.fit(line, 40))
    }
}
