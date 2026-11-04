package com.team.chagok.core.domain.show

import com.team.chagok.core.model.FormatPack
import com.team.chagok.core.model.Guest
import com.team.chagok.core.model.GuestId
import com.team.chagok.core.model.HostSkin
import com.team.chagok.core.model.Seat
import com.team.chagok.core.model.SignalEvent
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class BeltHoldTest {
    private val dad = Guest(GuestId("a"), "아빠", Seat.DRIVER, appearances = 0, isChild = false)
    private val kid = Guest(GuestId("b"), "민준", Seat.REAR_RIGHT, appearances = 0, isChild = true)
    private val machine = ShowStateMachine(FormatPack("p", "팩", emptyList()))
    private val onAir = ShowState.OnAir(1, listOf(dad, kid), startedAtMillis = 0)

    private fun belts(state: ShowState, vararg seats: Seat) = machine.reduce(state, ShowInput.BeltsChanged(seats.toList()))

    @Test
    fun `unbuckling on air stops the show and names the person`() {
        val t = belts(onAir, Seat.REAR_RIGHT)
        assertTrue(t.state is ShowState.SafetyPaused)
        assertEquals(listOf("민준"), t.effects.filterIsInstance<ShowEffect.SayBelt>().single().names)
    }

    @Test
    fun `an unknown person is named by seat`() {
        val t = belts(onAir, Seat.REAR_LEFT)
        assertEquals(listOf("뒷자리 왼쪽"), t.effects.filterIsInstance<ShowEffect.SayBelt>().single().names)
    }

    @Test
    fun `the show resumes only after everyone is buckled`() {
        val held = belts(onAir, Seat.REAR_RIGHT, Seat.DRIVER)
        val one = belts(held.state, Seat.REAR_RIGHT)
        assertFalse(one.effects.any { it is ShowEffect.ScheduleResume })
        assertTrue(machine.reduce(one.state, ShowInput.ResumeDue).state is ShowState.SafetyPaused)

        val done = belts(one.state)
        assertTrue(done.effects.any { it is ShowEffect.ScheduleResume })
        assertTrue(machine.reduce(done.state, ShowInput.ResumeDue).state is ShowState.OnAir)
    }

    @Test
    fun `a turn signal keeps holding after the belts are fixed`() {
        val held = belts(onAir, Seat.REAR_RIGHT)
        val blink = machine.reduce(held.state, ShowInput.Signal(SignalEvent.TurnSignalChanged(on = true)))
        val fixed = belts(blink.state)
        assertFalse(fixed.effects.any { it is ShowEffect.ScheduleResume })
        val off = machine.reduce(fixed.state, ShowInput.Signal(SignalEvent.TurnSignalChanged(on = false)))
        assertTrue(off.effects.any { it is ShowEffect.ScheduleResume })
    }

    @Test
    fun `turn signal release does not resume while a belt is off`() {
        val blink = machine.reduce(onAir, ShowInput.Signal(SignalEvent.TurnSignalChanged(on = true)))
        val held = belts(blink.state, Seat.REAR_RIGHT)
        val off = machine.reduce(held.state, ShowInput.Signal(SignalEvent.TurnSignalChanged(on = false)))
        assertFalse(off.effects.any { it is ShowEffect.ScheduleResume })
    }

    @Test
    fun `parking during a belt hold still ends the episode`() {
        val held = belts(onAir, Seat.REAR_RIGHT)
        assertTrue(machine.reduce(held.state, ShowInput.Signal(SignalEvent.GearParked)).state is ShowState.Ending)
    }

    @Test
    fun `unbuckling at the ending changes nothing`() {
        val ending = ShowState.Ending(onAir)
        assertEquals(ending, belts(ending, Seat.DRIVER).state)
    }

    @Test
    fun `cabin counts a buckled seat as present even without occupancy`() {
        val cabin = Cabin().on(SignalEvent.SeatChanged(Seat.REAR_LEFT, occupied = false, buckled = true))
        assertEquals(listOf(Seat.REAR_LEFT), cabin.present)
        assertTrue(cabin.unbuckled.isEmpty())
    }

    @Test
    fun `cabin keeps track of open doors while people get in`() {
        val open = Cabin().on(SignalEvent.DoorOpened(Seat.REAR_LEFT)).on(SignalEvent.DoorOpened(Seat.DRIVER))
        assertEquals(setOf(Seat.REAR_LEFT, Seat.DRIVER), open.openDoors)
        assertEquals(setOf(Seat.DRIVER), open.on(SignalEvent.DoorClosed(Seat.REAR_LEFT)).openDoors)
    }

    @Test
    fun `a seat value after missing sensors counts as sensed`() {
        val cabin = Cabin().on(SignalEvent.SeatSensors(available = false))
            .on(SignalEvent.SeatChanged(Seat.DRIVER, occupied = true, buckled = true))
        assertEquals(true, cabin.sensed)
    }
}
