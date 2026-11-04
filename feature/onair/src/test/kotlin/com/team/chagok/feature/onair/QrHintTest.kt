package com.team.chagok.feature.onair

import org.junit.Assert.assertTrue
import org.junit.Test

class QrHintTest {

    @Test
    fun `qr hint says how to use it and keeps the copy rules`() {
        assertTrue("카메라로 찍는 법", "카메라" in QR_HINT)
        assertTrue("밖으로 안 나간다", "보내지 않아요" in QR_HINT)
        listOf("이야기", "라디오", "게임", "퀴즈").forEach { assertTrue(it, it !in QR_HINT) }
        assertTrue("한 줄", '\n' !in QR_HINT)
    }
}
