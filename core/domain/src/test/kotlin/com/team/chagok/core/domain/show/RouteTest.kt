package com.team.chagok.core.domain.show

import com.team.chagok.core.domain.signal.RouteReading
import com.team.chagok.core.domain.signal.remainingTime
import com.team.chagok.core.domain.signal.routeEvents
import com.team.chagok.core.model.Corner
import com.team.chagok.core.model.FormatPack
import com.team.chagok.core.model.Guest
import com.team.chagok.core.model.GuestId
import com.team.chagok.core.model.Seat
import com.team.chagok.core.model.SignalCategory
import com.team.chagok.core.model.SignalEvent
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** 내비 경로 → 목적지 제목 · 도착 예고 코너. */
class RouteTest {

    private val guest = Guest(GuestId("g1"), "지호", Seat.REAR_LEFT)
    private val arrival = Corner("arrival", "도착 예고", SignalCategory.ARRIVAL, "곧 도착! {guest}?")
    private val machine = ShowStateMachine(FormatPack("basic", "기본", listOf(arrival)))
    private val onAir = ShowState.OnAir(episode = 3, roster = listOf(guest), startedAtMillis = 0)

    private fun ShowState.on(event: SignalEvent) = machine.reduce(this, ShowInput.Signal(event))

    @Test
    fun `route readings become destination and one arrival notice`() = runTest {
        val events = flowOf(
            RouteReading.Guiding("할머니댁", 20.minutes),
            RouteReading.Guiding("할머니댁", 5.minutes),
            RouteReading.Guiding("할머니댁", 3.minutes),
            RouteReading.Guiding("할머니댁", 1.minutes),
            RouteReading.Ended,
        ).routeEvents().toList()

        assertEquals(
            listOf(
                SignalEvent.RouteChanged("할머니댁"),
                SignalEvent.RemainingChanged(20.minutes),
                SignalEvent.RemainingChanged(5.minutes),
                SignalEvent.RemainingChanged(3.minutes),
                SignalEvent.ArrivingSoon,
                SignalEvent.RemainingChanged(1.minutes),
                SignalEvent.RouteChanged(null),
            ),
            events,
        )
    }

    @Test
    fun `remaining time is sent only when it moves a minute or more`() = runTest {
        val events = flowOf(
            RouteReading.Guiding("할머니댁", 30.minutes),
            RouteReading.Guiding("할머니댁", 30.minutes - 20.seconds),
            RouteReading.Guiding("할머니댁", 29.minutes),
        ).routeEvents().toList()

        assertEquals(
            listOf(SignalEvent.RemainingChanged(30.minutes), SignalEvent.RemainingChanged(29.minutes)),
            events.filterIsInstance<SignalEvent.RemainingChanged>(),
        )
    }

    @Test
    fun `arrival is its own signal and keeps the destination for the ending`() = runTest {
        val events = flowOf(
            RouteReading.Guiding("할머니댁", 10.minutes),
            RouteReading.Arrived,
            RouteReading.Ended,
        ).routeEvents().toList()

        assertEquals(SignalEvent.Arrived, events.last())
        assertTrue(SignalEvent.RouteChanged(null) !in events)
    }

    @Test
    fun `arrival without a notice opens the arrival corner then wraps up`() {
        val arrived = onAir.copy(destination = "할머니댁").on(SignalEvent.Arrived).state as ShowState.OnAir
        assertEquals("arrival", arrived.live?.corner?.id)
        assertTrue(arrived.arrived)

        val done = machine.reduce(arrived, ShowInput.CornerDone).state as ShowState.OnAir
        val quiet = machine.reduce(done, ShowInput.QuietDue)
        assertNull((quiet.state as ShowState.OnAir).live)
        assertTrue(quiet.effects.isEmpty())
    }

    @Test
    fun `after the arrival corner no new corner opens`() {
        val noticed = onAir.on(SignalEvent.ArrivingSoon).state
        val done = machine.reduce(noticed, ShowInput.CornerDone).state as ShowState.OnAir

        val tunnel = done.on(SignalEvent.LightsOn).state as ShowState.OnAir
        assertNull(tunnel.live)
        assertNull((machine.reduce(done, ShowInput.QuietDue).state as ShowState.OnAir).live)
    }

    @Test
    fun `ending names the destination only after arriving`() {
        val arrived = onAir.copy(destination = "할머니댁").on(SignalEvent.Arrived).state as ShowState.OnAir
        val ended = machine.reduce(machine.reduce(arrived, ShowInput.CornerDone).state, ShowInput.Signal(SignalEvent.GearParked))
        val idle = machine.reduce(ended.state, ShowInput.EndingDone(1)).state as ShowState.Idle
        assertNull(idle.destination) // 도착한 곳을 다음 회 오프닝에 다시 말하지 않는다
    }

    @Test
    fun `filler gap stretches with the trip and never drops below the default`() {
        val fillers = (1..3).map { Corner("f$it", "채움$it", SignalCategory.QUIET, "{guest}?") }
        val m = ShowStateMachine(FormatPack("basic", "기본", fillers + arrival))
        fun gapFor(left: kotlin.time.Duration?): kotlin.time.Duration {
            val effects = m.reduce(onAir.copy(remaining = left, live = null), ShowInput.QuietDue).let { t ->
                m.reduce(t.state, ShowInput.CornerDone).effects
            }
            return effects.filterIsInstance<ShowEffect.ScheduleQuiet>().single().after
        }
        assertEquals(ShowStateMachine.DEFAULT_QUIET_AFTER, gapFor(null))
        assertEquals(ShowStateMachine.DEFAULT_QUIET_AFTER, gapFor(5.minutes)) // 짧은 길 → 촘촘히
        assertEquals(3.minutes, gapFor(12.minutes)) // (12-3)분 ÷ (남은 채움 2 + 1)
        assertEquals(ShowStateMachine.MAX_QUIET_GAP, gapFor(120.minutes)) // 긴 길도 4분 넘게 비우지 않는다
    }

    @Test
    fun `a new destination can announce arrival again`() = runTest {
        val events = flowOf(
            RouteReading.Guiding("마트", 2.minutes),
            RouteReading.Guiding("학교", 10.minutes),
            RouteReading.Guiding("학교", 2.minutes),
        ).routeEvents().toList()

        assertEquals(2, events.count { it == SignalEvent.ArrivingSoon })
    }

    @Test
    fun `unknown remaining time never announces`() = runTest {
        val events = flowOf(RouteReading.Guiding("마트", null)).routeEvents().toList()
        assertEquals(listOf(SignalEvent.RouteChanged("마트")), events)
    }

    @Test
    fun `destination set before the show reaches the opening line`() {
        val idle = ShowState.Idle(driving = false).on(SignalEvent.RouteChanged("할머니댁")).state
        val started = machine.reduce(idle, ShowInput.Start(3, listOf(guest), atMillis = 0))

        assertEquals("할머니댁", (started.state as ShowState.Opening).destination)
        assertTrue(started.effects.contains(ShowEffect.PlayOpening(3, listOf(guest), "할머니댁")))
    }

    @Test
    fun `arriving soon opens the arrival corner`() {
        val opened = onAir.on(SignalEvent.ArrivingSoon).state as ShowState.OnAir
        assertEquals("arrival", opened.live?.corner?.id)
    }

    @Test
    fun `arriving soon before the show is dropped`() {
        val idle = ShowState.Idle(driving = false).on(SignalEvent.ArrivingSoon).state as ShowState.Idle
        assertTrue(idle.pending.isEmpty())
    }

    @Test
    fun `route end withdraws a waiting arrival corner`() {
        val braking = onAir.copy(braking = true).on(SignalEvent.ArrivingSoon).state as ShowState.OnAir
        assertEquals(SignalCategory.ARRIVAL, braking.pending.single().category)

        val ended = braking.copy(destination = "할머니댁").on(SignalEvent.RouteChanged(null)).state as ShowState.OnAir
        assertTrue(ended.pending.isEmpty())
        assertNull(ended.destination)
    }

    @Test
    fun `remaining time is read as seconds`() {
        assertEquals(66.minutes, remainingTime(3960, 23_000)) // 에뮬레이터 실측 값
        assertEquals(66.minutes, remainingTime(3960))
    }

    @Test
    fun `remaining time sent in minutes or milliseconds is corrected by distance`() {
        assertEquals(66.minutes, remainingTime(66, 23_000)) // 초로 읽으면 시속 1250km
        assertEquals(66.minutes, remainingTime(3_960_000, 23_000)) // 초로 읽으면 시속 0.02km
    }

    @Test
    fun `missing or absurd remaining time is unknown`() {
        assertNull(remainingTime(null, 23_000))
        assertNull(remainingTime(0, 23_000))
        assertNull(remainingTime(-5))
        assertNull(remainingTime(200_000)) // 하루 넘음
    }
}
