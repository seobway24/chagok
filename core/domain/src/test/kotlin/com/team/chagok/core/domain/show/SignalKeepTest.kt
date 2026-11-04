package com.team.chagok.core.domain.show

import com.team.chagok.core.model.Corner
import com.team.chagok.core.model.FormatPack
import com.team.chagok.core.model.Guest
import com.team.chagok.core.model.GuestId
import com.team.chagok.core.model.Seat
import com.team.chagok.core.model.SignalCategory
import com.team.chagok.core.model.SignalEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 코너를 못 여는 순간에 온 차량 신호를 잃지 않는다. */
class SignalKeepTest {

    private val driver = Guest(GuestId("g0"), "캡틴", Seat.DRIVER)
    private val guest = Guest(GuestId("g1"), "지호", Seat.REAR_LEFT)
    private val roster = listOf(driver, guest)

    private val weather = Corner("weather", "기상 특보", SignalCategory.RAIN, "비?")
    private val tunnel = Corner("tunnel", "터널 토크", SignalCategory.TUNNEL, "터널?")
    private val milestone = Corner("milestone", "주행 특집", SignalCategory.MILESTONE, "거리?")
    private val entrance = Corner("door", "게스트 입장", SignalCategory.DOOR, "입장?")
    private val machine = ShowStateMachine(FormatPack("basic", "기본", listOf(weather, tunnel, milestone, entrance)))

    private val onAir = ShowState.OnAir(episode = 1, roster = roster, startedAtMillis = 0)
    private val start = ShowInput.Start(1, roster, atMillis = 0)

    private fun ShowState.on(event: SignalEvent) = machine.reduce(this, ShowInput.Signal(event))
    private fun ShowState.liveId() = (this as? ShowState.OnAir)?.live?.corner?.id
    private fun Transition.after(input: ShowInput) = machine.reduce(state, input)

    @Test
    fun `episode starts with the latest driving and brake values`() {
        val idle = ShowState.Idle(driving = false)
            .on(SignalEvent.DrivingChanged(driving = false)).state
            .on(SignalEvent.BrakePedalChanged(pressed = true)).state

        val opening = machine.reduce(idle, start).state as ShowState.Opening

        assertFalse(opening.driving)
        assertTrue(opening.braking)
    }

    @Test
    fun `idle follows driving changes after an episode ends`() {
        val ending = onAir.copy(driving = false).on(SignalEvent.GearParked).state
        val idle = machine.reduce(ending, ShowInput.EndingDone(atMillis = 1)).state as ShowState.Idle

        assertFalse(idle.driving)
        assertTrue((idle.on(SignalEvent.DrivingChanged(driving = true)).state as ShowState.Idle).driving)
    }

    @Test
    fun `driving change during the ending reaches idle`() {
        val ending = onAir.copy(driving = false).on(SignalEvent.GearParked).state
            .on(SignalEvent.DrivingChanged(driving = true)).state

        val idle = machine.reduce(ending, ShowInput.EndingDone(atMillis = 1)).state as ShowState.Idle

        assertTrue(idle.driving)
    }

    @Test
    fun `episode started with the turn signal on stays paused`() {
        val idle = ShowState.Idle(driving = false).on(SignalEvent.TurnSignalChanged(on = true)).state

        val started = machine.reduce(idle, start)

        assertTrue(started.state is ShowState.SafetyPaused)
        assertTrue(started.effects.none { it is ShowEffect.PlayOpening })
    }

    @Test
    fun `turn signal held through the ending stays on in idle`() {
        val paused = onAir.on(SignalEvent.TurnSignalChanged(on = true)).state
        val ending = paused.on(SignalEvent.GearParked).state

        val idle = machine.reduce(ending, ShowInput.EndingDone(atMillis = 1)).state as ShowState.Idle

        assertTrue(idle.turnSignal)
    }

    @Test
    fun `rain already falling opens its corner after the opening`() {
        val idle = ShowState.Idle(driving = false).on(SignalEvent.RainStarted).state
        val started = machine.reduce(idle, start)

        val done = started.after(ShowInput.OpeningDone)

        assertEquals("weather", done.state.liveId())
    }

    @Test
    fun `rain that stopped before the start opens nothing`() {
        val idle = ShowState.Idle(driving = false)
            .on(SignalEvent.RainStarted).state
            .on(SignalEvent.RainStopped).state

        val done = machine.reduce(idle, start).after(ShowInput.OpeningDone)

        assertEquals(null, done.state.liveId())
        assertTrue(done.effects.any { it is ShowEffect.ScheduleQuiet })
    }

    @Test
    fun `one time signals before the start are not kept`() {
        val idle = ShowState.Idle(driving = false)
            .on(SignalEvent.MilestoneKm(100)).state
            .on(SignalEvent.DoorClosed(Seat.REAR_LEFT)).state

        val done = machine.reduce(idle, start).after(ShowInput.OpeningDone)

        assertEquals(null, done.state.liveId())
    }

    @Test
    fun `signal during the opening opens its corner right after`() {
        val opening = ShowState.Opening(1, roster, 0).on(SignalEvent.LightsOn).state

        val done = machine.reduce(opening, ShowInput.OpeningDone)

        assertEquals("tunnel", done.state.liveId())
    }

    @Test
    fun `signals during a corner wait and open in order`() {
        val live = onAir.on(SignalEvent.RainStarted).state
            .on(SignalEvent.MilestoneKm(100)).state
            .on(SignalEvent.LightsOn).state
        assertEquals("weather", live.liveId())

        val second = machine.reduce(live, ShowInput.CornerDone)
        assertEquals("milestone", second.state.liveId())
        assertTrue(second.effects.any { it is ShowEffect.RunCorner })

        val third = second.after(ShowInput.CornerDone)
        assertEquals("tunnel", third.state.liveId())
    }

    @Test
    fun `same signal twice waits only once`() {
        val live = onAir.on(SignalEvent.RainStarted).state
            .on(SignalEvent.LightsOn).state
            .on(SignalEvent.LightsOn).state

        val second = machine.reduce(live, ShowInput.CornerDone)
        val third = second.after(ShowInput.CornerDone)

        assertEquals("tunnel", second.state.liveId())
        assertEquals(null, third.state.liveId())
        assertTrue(third.effects.any { it is ShowEffect.ScheduleQuiet })
    }

    @Test
    fun `corner already played does not wait`() {
        val done = machine.reduce(onAir.on(SignalEvent.RainStarted).state, ShowInput.CornerDone).state
        val live = done.on(SignalEvent.LightsOn).state.on(SignalEvent.RainStarted).state

        val next = machine.reduce(live, ShowInput.CornerDone)

        assertEquals(null, next.state.liveId())
    }

    @Test
    fun `tunnel left while waiting is dropped`() {
        val live = onAir.on(SignalEvent.RainStarted).state
            .on(SignalEvent.LightsOn).state
            .on(SignalEvent.LightsOff).state

        val next = machine.reduce(live, ShowInput.CornerDone)

        assertEquals(null, next.state.liveId())
    }

    @Test
    fun `signal while braking opens after the calm time once the brake is released`() {
        val braking = onAir.on(SignalEvent.BrakePedalChanged(pressed = true)).state
            .on(SignalEvent.RainStarted).state
        assertEquals(null, braking.liveId())

        val released = braking.on(SignalEvent.BrakePedalChanged(pressed = false))
        // 떼자마자 말을 걸지 않는다
        assertEquals(null, released.state.liveId())
        assertTrue(ShowEffect.ScheduleResume(ShowStateMachine.DEFAULT_CALM_AFTER_BRAKE) in released.effects)

        assertEquals("weather", released.after(ShowInput.ResumeDue).state.liveId())
    }

    @Test
    fun `corner end while braking opens the waiting signal after the calm time`() {
        val live = onAir.on(SignalEvent.RainStarted).state
            .on(SignalEvent.LightsOn).state
            .on(SignalEvent.BrakePedalChanged(pressed = true)).state

        val done = machine.reduce(live, ShowInput.CornerDone)
        assertEquals(null, done.state.liveId())

        val released = done.state.on(SignalEvent.BrakePedalChanged(pressed = false))
        assertEquals(null, released.state.liveId())
        assertEquals("tunnel", released.after(ShowInput.ResumeDue).state.liveId())
    }

    @Test
    fun `new signal during the calm time waits too`() {
        val calming = onAir.on(SignalEvent.BrakePedalChanged(pressed = true)).state
            .on(SignalEvent.BrakePedalChanged(pressed = false)).state

        val rain = calming.on(SignalEvent.RainStarted)
        assertEquals(null, rain.state.liveId())
        assertEquals("weather", rain.after(ShowInput.ResumeDue).state.liveId())
    }

    @Test
    fun `braking again during the calm time restarts it`() {
        val again = onAir.on(SignalEvent.BrakePedalChanged(pressed = true)).state
            .on(SignalEvent.RainStarted).state
            .on(SignalEvent.BrakePedalChanged(pressed = false)).state
            .on(SignalEvent.BrakePedalChanged(pressed = true))
        assertTrue(ShowEffect.CancelResume in again.effects)
        // 다시 밟은 채로 예전 타이머가 와도 열지 않는다
        assertEquals(null, again.after(ShowInput.ResumeDue).state.liveId())
    }

    @Test
    fun `signal during a safety pause opens after resuming`() {
        val paused = onAir.on(SignalEvent.TurnSignalChanged(on = true)).state
            .on(SignalEvent.RainStarted).state
            .on(SignalEvent.TurnSignalChanged(on = false)).state

        val resumed = machine.reduce(paused, ShowInput.ResumeDue)

        assertEquals("weather", resumed.state.liveId())
    }

    @Test
    fun `corner cut by the turn signal runs again after resuming`() {
        val paused = onAir.on(SignalEvent.LightsOn).state
            .on(SignalEvent.TurnSignalChanged(on = true)).state
            .on(SignalEvent.TurnSignalChanged(on = false)).state

        val resumed = machine.reduce(paused, ShowInput.ResumeDue)

        assertEquals("tunnel", resumed.state.liveId())
    }

    @Test
    fun `door corner cut by the turn signal goes back to the same seat`() {
        val paused = onAir.on(SignalEvent.DoorClosed(Seat.DRIVER)).state
            .on(SignalEvent.TurnSignalChanged(on = true)).state
            .on(SignalEvent.TurnSignalChanged(on = false)).state

        val resumed = machine.reduce(paused, ShowInput.ResumeDue).state as ShowState.OnAir

        assertEquals(driver, resumed.live!!.guest)
    }

    @Test
    fun `waiting signals end with the episode`() {
        val ending = onAir.on(SignalEvent.RainStarted).state
            .on(SignalEvent.LightsOn).state
            .on(SignalEvent.GearParked).state

        val idle = machine.reduce(ending, ShowInput.EndingDone(atMillis = 1)).state as ShowState.Idle

        assertTrue(idle.pending.isEmpty())
    }
}
