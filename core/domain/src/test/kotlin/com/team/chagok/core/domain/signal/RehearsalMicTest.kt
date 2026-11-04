package com.team.chagok.core.domain.signal

import com.team.chagok.core.domain.memory.CallbackPlanner
import com.team.chagok.core.domain.port.Heard
import com.team.chagok.core.domain.show.LiveCorner
import com.team.chagok.core.domain.show.ShowState
import com.team.chagok.core.model.Corner
import com.team.chagok.core.model.Guest
import com.team.chagok.core.model.GuestId
import com.team.chagok.core.model.Seat
import com.team.chagok.core.model.SignalCategory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class RehearsalMicTest {

    private val mom = Guest(GuestId("g-mom"), "엄마", Seat.FRONT_PASSENGER)
    private val weather = Corner(
        "weather", "기상 특보", SignalCategory.RAIN, "몇 점?",
        variants = listOf("노래는?", "먹고 싶은 건?"),
    )
    private val script = RehearsalScript(
        byCorner = mapOf("weather" to listOf("칠 점이요", "빗방울 동요요", "김치전이요")),
        callbacks = mapOf("milestone" to "또 가고 싶어요"),
        farewell = "다들 고마워요",
    )

    private fun onAir(corner: Corner, episode: Int) =
        ShowState.OnAir(episode = episode, roster = listOf(mom), live = LiveCorner(corner, mom), startedAtMillis = 0)

    @Test
    fun `the answer matches the question asked in that episode`() = runTest {
        // 회차 13 → 질문 13 % 3 = 1번(노래), 14 → 2번(먹을 것)
        var state: ShowState = onAir(weather, 13)
        val mic = RehearsalMic(script) { state }

        assertEquals(Heard.Text("빗방울 동요요"), mic.listen(1.seconds))
        state = onAir(weather, 14)
        assertEquals(Heard.Text("김치전이요"), mic.listen(1.seconds))
        // 같은 회차·코너면 몇 번을 들어도 같다(다시 부르기 포함)
        assertEquals(Heard.Text("김치전이요"), mic.listen(1.seconds))
    }

    @Test
    fun `callback answers follow the topic of the remembered fact`() {
        val callback = Corner(CallbackPlanner.PREFIX + "g-mom|milestone|강릉 바다", "지난 회 이어서", SignalCategory.QUIET, "?")
        assertEquals("또 가고 싶어요", script.answerFor(onAir(callback, 13)))
    }

    @Test
    fun `ending gets the farewell and unknown moments stay silent`() = runTest {
        val ending = ShowState.Ending(ShowState.OnAir(episode = 13, roster = listOf(mom), startedAtMillis = 0))
        assertEquals("다들 고마워요", script.answerFor(ending))

        val unknown = Corner("tunnel", "터널 토크", SignalCategory.TUNNEL, "?")
        assertEquals(Heard.Silence, RehearsalMic(script) { onAir(unknown, 13) }.listen(1.seconds))
        assertEquals(Heard.Silence, RehearsalMic(script) { ShowState.Idle() }.listen(1.seconds))
    }
}
