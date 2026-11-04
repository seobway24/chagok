package com.team.chagok.core.domain.show

import com.team.chagok.core.model.Seat
import com.team.chagok.core.model.SignalEvent

/** 자리 하나의 신호. 벨트를 맸으면 착석 값이 없어도 앉은 것으로 친다. */
data class SeatSignal(val occupied: Boolean = false, val buckled: Boolean = false) {
    val present: Boolean get() = occupied || buckled
    val unbuckled: Boolean get() = occupied && !buckled
}

/**
 * 차 안 자리 상태. [sensed] null = 아직 모름, false = 이 차는 자리 값을 주지 않음(사람을 직접 고른다).
 * 벨트를 매면 출석이다.
 */
data class Cabin(
    val sensed: Boolean? = null,
    val seats: Map<Seat, SeatSignal> = emptyMap(),
) {
    val present: List<Seat> get() = Seat.entries.filter { seats[it]?.present == true }
    val unbuckled: List<Seat> get() = Seat.entries.filter { seats[it]?.unbuckled == true }

    fun on(event: SignalEvent): Cabin = when (event) {
        is SignalEvent.SeatSensors -> copy(sensed = event.available)
        is SignalEvent.SeatChanged -> copy(sensed = true, seats = seats + (event.seat to SeatSignal(event.occupied, event.buckled)))
        else -> this
    }
}
