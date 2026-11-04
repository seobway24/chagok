package com.team.chagok.core.domain.show

import com.team.chagok.core.model.Guest
import com.team.chagok.core.model.GuestId
import com.team.chagok.core.model.Seat
import com.team.chagok.core.model.SignalEvent

/** 자리 하나의 신호. 벨트를 맸으면 착석 값이 없어도 앉은 것으로 친다. */
data class SeatSignal(val occupied: Boolean = false, val buckled: Boolean = false) {
    val present: Boolean get() = occupied || buckled
    val unbuckled: Boolean get() = occupied && !buckled
}

/**
 * 차 안 자리 상태. [sensed] null = 아직 모름, false = 이 차는 자리 값을 주지 않음(사람을 직접 고른다).
 * 벨트를 매면 출석이다. [openDoors] 는 지금 열린 문 — 누가 아직 타는 중이라는 뜻이라 탑승 확인이 기다린다.
 */
data class Cabin(
    val sensed: Boolean? = null,
    val seats: Map<Seat, SeatSignal> = emptyMap(),
    val openDoors: Set<Seat> = emptySet(),
) {
    val present: List<Seat> get() = Seat.entries.filter { seats[it]?.present == true }
    val unbuckled: List<Seat> get() = Seat.entries.filter { seats[it]?.unbuckled == true }

    fun on(event: SignalEvent): Cabin = when (event) {
        is SignalEvent.SeatSensors -> copy(sensed = event.available)
        is SignalEvent.SeatChanged -> copy(sensed = true, seats = seats + (event.seat to SeatSignal(event.occupied, event.buckled)))
        is SignalEvent.DoorOpened -> copy(openDoors = openDoors + event.seat)
        is SignalEvent.DoorClosed -> copy(openDoors = openDoors - event.seat)
        else -> this
    }
}

/** 카드가 없는 자리 사람을 부르는 이름. 탑승 확인과 늦게 탄 사람이 같이 쓴다. */
const val PLAIN_GUEST_NAME = "손님"

/** "{guest} 님" 에 이름 없는 손님이 들어가면 "손님 님" 이 되므로 "손님" 으로 줄인다. */
internal fun String.fillGuest(nickname: String): String {
    val named = if (nickname == PLAIN_GUEST_NAME) replace("{guest} 님", PLAIN_GUEST_NAME) else this
    return named.replace("{guest}", nickname)
}

/** 카드 없는 자리의 출연자. id 는 자리로 정해 같은 자리 손님은 한 사람으로 친다. */
fun plainGuest(seat: Seat, nickname: String = PLAIN_GUEST_NAME) =
    Guest(GuestId("guest-${seat.name}"), nickname, seat, appearances = 0, isChild = false)
