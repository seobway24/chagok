package com.team.chagok.core.domain.show

import com.team.chagok.core.domain.port.AudioZone
import com.team.chagok.core.domain.port.Heard
import com.team.chagok.core.domain.port.HostVoice
import com.team.chagok.core.domain.port.MicInput
import com.team.chagok.core.model.Corner
import com.team.chagok.core.model.Guest
import com.team.chagok.core.model.GuestId
import com.team.chagok.core.model.HostSkin
import com.team.chagok.core.model.Seat
import com.team.chagok.core.model.SignalCategory
import com.team.chagok.core.quip.QuipEngine
import com.team.chagok.core.quip.QuipRequest
import com.team.chagok.core.quip.QuipResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration

/** 같은 코너라도 회차가 바뀌면 다른 질문을 하고, 대답에는 실제로 한 질문이 남는다. */
class ShowDirectorCornerTest {

    private class SpokenLines : HostVoice {
        val lines = mutableListOf<String>()

        override suspend fun speak(text: String, speaker: String, zone: AudioZone): Boolean {
            lines += text
            return true
        }

        override fun stop() = Unit
    }

    private val mic = object : MicInput {
        override suspend fun listen(window: Duration): Heard = Heard.Text("바다요")
    }

    private val quip = object : QuipEngine {
        override val id = "silent"
        override suspend fun quip(request: QuipRequest): QuipResult = QuipResult.Unavailable(QuipResult.Reason.OFFLINE)
    }

    private val guest = Guest(GuestId("g1"), "엄마", Seat.FRONT_PASSENGER)
    private val corner = Corner(
        "balance", "둘 중 하나", SignalCategory.QUIET, "A! {guest}",
        variants = listOf("B! {guest}", "C! {guest}"),
    )

    @Test
    fun `next episode asks the next question`() = runTest {
        val voice = SpokenLines()
        val director = ShowDirector(voice, mic, quip, HostSkin("basic", "다정한 진행자", "haeun"))

        val first = director.corner(LiveCorner(corner, guest), listOf(guest), episode = 13)
        val second = director.corner(LiveCorner(corner, guest), listOf(guest), episode = 14)

        assertEquals(listOf("B! 엄마", "C! 엄마"), voice.lines)
        assertEquals("B! 엄마", first?.question)
        assertEquals("C! 엄마", second?.question)
    }

    @Test
    fun `the answer is handed over before the host starts the reaction`() = runTest {
        val order = mutableListOf<String>()
        val slowQuip = object : QuipEngine {
            override val id = "slow"
            override suspend fun quip(request: QuipRequest): QuipResult {
                order += "quip"
                return QuipResult.Line("좋아요", id, 0)
            }
        }
        val director = ShowDirector(SpokenLines(), mic, slowQuip, HostSkin("basic", "다정한 진행자", "haeun"))

        director.corner(LiveCorner(corner, guest), listOf(guest), episode = 13, heard = { order += "heard:${it.text}" })

        // 반응을 말하는 중에 깜빡이로 끊겨도 대답은 이미 상태에 들어가 있어야 재개 때 같은 코너를 다시 열지 않는다
        assertEquals(listOf("heard:바다요", "quip"), order)
    }
}
