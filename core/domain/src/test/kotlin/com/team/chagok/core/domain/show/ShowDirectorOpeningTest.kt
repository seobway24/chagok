package com.team.chagok.core.domain.show

import com.team.chagok.core.domain.port.AudioZone
import com.team.chagok.core.domain.port.Heard
import com.team.chagok.core.domain.port.HostVoice
import com.team.chagok.core.domain.port.MicInput
import com.team.chagok.core.model.Guest
import com.team.chagok.core.model.GuestId
import com.team.chagok.core.model.HostSkin
import com.team.chagok.core.model.Seat
import com.team.chagok.core.quip.QuipEngine
import com.team.chagok.core.quip.QuipRequest
import com.team.chagok.core.quip.QuipResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration

/** 첫 인사는 오늘 몇 명이 함께하는지부터 말한다. */
class ShowDirectorOpeningTest {

    private class SpokenLines : HostVoice {
        val lines = mutableListOf<String>()

        override suspend fun speak(text: String, speaker: String, zone: AudioZone): Boolean {
            lines += text
            return true
        }

        override fun stop() = Unit
    }

    private val mic = object : MicInput {
        override suspend fun listen(window: Duration): Heard = error("opening does not listen")
    }

    private val quip = object : QuipEngine {
        override val id = "unused"
        override suspend fun quip(request: QuipRequest): QuipResult = error("opening does not quip")
    }

    private val skin = HostSkin("basic", "다정한 진행자", "haeun")

    @Test
    fun `opening says how many guests are on air today`() = runTest {
        val voice = SpokenLines()
        val roster = listOf(
            Guest(GuestId("g0"), "아빠", Seat.DRIVER),
            Guest(GuestId("g1"), "엄마", Seat.FRONT_PASSENGER),
            Guest(GuestId("g2"), "누나", Seat.REAR_LEFT),
            Guest(GuestId("g3"), "막내", Seat.REAR_RIGHT),
        )

        ShowDirector(voice, mic, quip, skin).opening(episode = 13, roster = roster)

        assertEquals(
            listOf("차곡 팟캐스트 제13회! 오늘은 4명이 함께하고 있네요. 아빠, 엄마, 누나, 막내!"),
            voice.lines,
        )
    }
}
