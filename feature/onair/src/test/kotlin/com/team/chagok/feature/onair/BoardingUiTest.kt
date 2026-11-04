package com.team.chagok.feature.onair

import com.team.chagok.core.domain.show.Cabin
import com.team.chagok.core.domain.show.SeatSignal
import com.team.chagok.core.model.Seat
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

class BoardingUiTest {
    private val people = listOf(PersonCard("a", "아빠", Seat.DRIVER), PersonCard("b", "민준", Seat.REAR_RIGHT))
    private val belted = SeatSignal(occupied = true, buckled = true)
    private val loose = SeatSignal(occupied = true, buckled = false)

    private fun cabin(vararg seats: Pair<Seat, SeatSignal>) = Cabin(sensed = true, seats = seats.toMap())
    private fun ui(c: Cabin, d: BoardDraft = BoardDraft(), driving: Boolean = false) = boardingOf(c, people, d, driving, 14)

    @Test
    fun `no sensors means picking by hand`() {
        assertEquals(BoardMode.MANUAL, ui(Cabin(sensed = false)).mode)
        assertEquals(1, ui(Cabin(sensed = false), BoardDraft(unpicked = setOf("a"))).riders)
    }

    @Test
    fun `empty car waits`() = assertEquals(BoardMode.WATCH, ui(cabin()).mode)

    @Test
    fun `unbuckled rider gets a grace period then a warning by name`() {
        val c = cabin(Seat.DRIVER to belted, Seat.REAR_RIGHT to loose)
        assertEquals(BoardMode.WATCH, ui(c).mode)
        val warned = ui(c, BoardDraft(warned = true))
        assertEquals(BoardMode.BELT, warned.mode)
        assertEquals(listOf("민준"), warned.unbuckled)
    }

    @Test
    fun `driving off unbuckled warns at once`() {
        assertEquals(BoardMode.BELT, ui(cabin(Seat.REAR_RIGHT to loose), driving = true).mode)
    }

    @Test
    fun `all buckled is ready`() {
        assertEquals(BoardMode.READY, ui(cabin(Seat.DRIVER to belted, Seat.REAR_RIGHT to belted)).mode)
    }

    @Test
    fun `an open door holds the countdown while someone is still getting in`() {
        val c = cabin(Seat.DRIVER to belted, Seat.REAR_RIGHT to belted).copy(openDoors = setOf(Seat.REAR_LEFT))
        val b = ui(c)
        assertEquals(BoardMode.WATCH, b.mode)
        assertTrue(b.doorOpen)
        assertEquals(BoardMode.READY, ui(c.copy(openDoors = emptySet())).mode)
    }

    @Test
    fun `a rider who sits down during the countdown stops it until buckled`() {
        val ready = cabin(Seat.DRIVER to belted, Seat.REAR_RIGHT to belted)
        assertEquals(BoardMode.READY, ui(ready).mode)
        val sitting = Cabin(sensed = true, seats = ready.seats + (Seat.REAR_LEFT to loose))
        assertEquals(BoardMode.WATCH, ui(sitting).mode)
    }

    @Test
    fun `the countdown key changes whenever the cabin changes so the countdown starts over`() {
        val two = ui(cabin(Seat.DRIVER to belted, Seat.REAR_RIGHT to belted))
        val three = ui(cabin(Seat.DRIVER to belted, Seat.REAR_RIGHT to belted, Seat.FRONT_PASSENGER to belted), BoardDraft(guests = mapOf(Seat.FRONT_PASSENGER to "손님")))
        assertEquals(BoardMode.READY, three.mode)
        assertNotEquals(two.readyKey, three.readyKey)
        assertEquals(two.readyKey, ui(cabin(Seat.DRIVER to belted, Seat.REAR_RIGHT to belted)).readyKey)
    }

    @Test
    fun `a buckled stranger is asked for a name`() {
        val b = ui(cabin(Seat.DRIVER to belted, Seat.REAR_LEFT to belted))
        assertEquals(BoardMode.GUEST, b.mode)
        assertEquals(Seat.REAR_LEFT, b.askSeat)
    }

    @Test
    fun `a guest kept as guest no longer blocks`() {
        val d = BoardDraft(guests = mapOf(Seat.REAR_LEFT to "손님"))
        assertEquals(BoardMode.READY, ui(cabin(Seat.DRIVER to belted, Seat.REAR_LEFT to belted), d).mode)
    }

    @Test
    fun `an unbuckled stranger is warned by seat`() {
        val b = ui(cabin(Seat.REAR_LEFT to loose), BoardDraft(warned = true))
        assertEquals(listOf("뒷자리 왼쪽"), b.unbuckled)
    }

    @Test
    fun `a card holder not in the car stays an empty seat`() {
        val b = ui(cabin(Seat.DRIVER to belted))
        assertNull(b.seats.first { it.seat == Seat.REAR_RIGHT }.name)
        assertEquals(BoardSeatState.EMPTY, b.seats.first { it.seat == Seat.REAR_RIGHT }.state)
    }

    @Test
    fun `name is cut out of a spoken sentence`() {
        assertEquals("지우", nameFromSpeech("저는 지우예요."))
        assertEquals("하린", nameFromSpeech("제 이름은 하린입니다"))
        assertEquals("지우", nameFromSpeech("지우요"))
        assertNull(nameFromSpeech("음 그러니까 저는 오늘 친구 집에 가는 길인데요 네"))
    }
}
