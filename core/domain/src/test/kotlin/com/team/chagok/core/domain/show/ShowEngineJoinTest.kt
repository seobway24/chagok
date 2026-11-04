package com.team.chagok.core.domain.show

import com.team.chagok.core.domain.port.AudioZone
import com.team.chagok.core.domain.port.Heard
import com.team.chagok.core.domain.port.HostVoice
import com.team.chagok.core.domain.port.MicInput
import com.team.chagok.core.domain.port.SignalSource
import com.team.chagok.core.domain.port.StudioRepository
import com.team.chagok.core.model.Corner
import com.team.chagok.core.model.EpisodeRecord
import com.team.chagok.core.model.FormatPack
import com.team.chagok.core.model.Guest
import com.team.chagok.core.model.GuestId
import com.team.chagok.core.model.HostSkin
import com.team.chagok.core.model.Seat
import com.team.chagok.core.model.SignalCategory
import com.team.chagok.core.model.SignalEvent
import com.team.chagok.core.quip.QuipEngine
import com.team.chagok.core.quip.QuipRequest
import com.team.chagok.core.quip.QuipResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** 자리 신호가 엔진을 거쳐 명부까지 간다. 신원은 탑승 확인과 같은 규칙(그 자리 가족 카드, 없으면 손님)으로 정한다. */
class ShowEngineJoinTest {

    private val driver = Guest(GuestId("g0"), "캡틴", Seat.DRIVER)
    private val youngest = Guest(GuestId("g2"), "막내", Seat.REAR_LEFT)

    private val lines = java.util.Collections.synchronizedList(mutableListOf<String>())
    private val voice = object : HostVoice {
        override suspend fun speak(text: String, speaker: String, zone: AudioZone): Boolean {
            lines += text
            return true
        }
        override fun stop() = Unit
    }
    private val mic = object : MicInput {
        override suspend fun listen(window: Duration): Heard = Heard.Silence
    }
    private val quip = object : QuipEngine {
        override val id = "none"
        override suspend fun quip(request: QuipRequest): QuipResult = QuipResult.Unavailable(QuipResult.Reason.ERROR)
    }
    private val studio = object : StudioRepository {
        override suspend fun roster() = listOf(driver, youngest)
        override suspend fun lastEpisodeNumber() = 0
        override suspend fun saveEpisode(record: EpisodeRecord) = Unit
    }

    private val entrance = Corner("door", "게스트 입장", SignalCategory.DOOR, "{guest} 님 입장!")
    private val filler = Corner("balance", "밸런스 토크", SignalCategory.QUIET, "{guest}, 골라?")

    @Test
    fun `seat taken on air adds the family card for that seat, then a plain guest`() = runBlocking {
        val signals = MutableSharedFlow<SignalEvent>(extraBufferCapacity = 16)
        val engine = ShowEngine(
            machine = ShowStateMachine(FormatPack("p", "p", listOf(entrance, filler)), quietAfter = 60.seconds),
            director = ShowDirector(voice, mic, quip, HostSkin("basic", "다정한 진행자", "haeun"), listenWindow = Duration.ZERO),
            signals = object : SignalSource { override val events = signals },
            studio = studio,
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        engine.launchIn(scope)
        suspend fun until(what: String, check: () -> Boolean) {
            withTimeoutOrNull(5.seconds) { while (!check()) delay(10) } ?: fail("$what — ${engine.state.value}")
        }
        fun roster() = (engine.state.value as? ShowState.OnAir)?.roster.orEmpty()
        fun idle() = (engine.state.value as? ShowState.OnAir)?.let { it.live == null && it.greeting.isEmpty() } == true

        signals.subscriptionCount.first { it > 0 }
        signals.emit(SignalEvent.DrivingChanged(driving = false))
        until("parked") { (engine.state.value as? ShowState.Idle)?.driving == false }
        signals.emit(SignalEvent.SeatChanged(Seat.DRIVER, occupied = true, buckled = true))
        until("cabin") { engine.cabin.value.present == listOf(Seat.DRIVER) }
        engine.startEpisode(keep = { it.seat in engine.cabin.value.present })
        until("on air", ::idle)
        assertEquals(listOf(driver), roster())

        signals.emit(SignalEvent.SeatChanged(Seat.REAR_LEFT, occupied = true, buckled = true))
        until("youngest greeted") { lines.any { it == "막내 님 입장!" } }
        assertEquals(listOf(driver, youngest), roster())
        until("door done", ::idle)

        signals.emit(SignalEvent.SeatChanged(Seat.REAR_RIGHT, occupied = true, buckled = true))
        until("guest added") { roster().size == 3 }
        val plain = roster().last()
        assertEquals(Seat.REAR_RIGHT, plain.seat)
        assertEquals(PLAIN_GUEST_NAME, plain.nickname)
        // 게스트 입장은 이미 했다 → 환영 한 줄
        until("welcomed") { lines.any { it.startsWith(PLAIN_GUEST_NAME) && "어서 오세요" in it } }
        assertTrue(lines.count { it.endsWith("님 입장!") } == 1)
        scope.cancel()
    }

    @Test
    fun `door signals reach the cabin so boarding can wait for people getting in`() = runBlocking {
        val signals = MutableSharedFlow<SignalEvent>(extraBufferCapacity = 16)
        val engine = ShowEngine(
            machine = ShowStateMachine(FormatPack("p", "p", listOf(entrance, filler)), quietAfter = 60.seconds),
            director = ShowDirector(voice, mic, quip, HostSkin("basic", "다정한 진행자", "haeun"), listenWindow = Duration.ZERO),
            signals = object : SignalSource { override val events = signals },
            studio = studio,
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        engine.launchIn(scope)
        suspend fun until(what: String, check: () -> Boolean) {
            withTimeoutOrNull(5.seconds) { while (!check()) delay(10) } ?: fail("$what — ${engine.cabin.value}")
        }
        signals.subscriptionCount.first { it > 0 }
        signals.emit(SignalEvent.DoorOpened(Seat.REAR_LEFT))
        until("door open") { engine.cabin.value.openDoors == setOf(Seat.REAR_LEFT) }
        signals.emit(SignalEvent.DoorClosed(Seat.REAR_LEFT))
        until("door closed") { engine.cabin.value.openDoors.isEmpty() }
        scope.cancel()
    }
}
