package com.team.chagok.platform.vehicle.adapter

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.listener.SeatBeltBuckledStateListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import com.team.chagok.core.model.Seat
import com.team.chagok.core.model.SignalEvent
import kotlin.coroutines.resume
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.suspendCancellableCoroutine

/*
 * 자리별 착석·벨트. 지원 자리는 차종마다 달라 먼저 물어본다. 권한 CAR_SEATS.
 * 벨트 값이 있는 자리만 쓴다. 착석 값이 없는 자리는 벨트를 맨 사람만 탄 것으로 본다.
 */

internal fun Vehicle.seatEvents(): Flow<SignalEvent> = flow {
    val belts = supportedAreas { ok, no -> getSeat().checkSeatBeltBuckledCapabilities(ok, no) }
    val occupancy = supportedAreas { ok, no -> getSeat().checkSeatOccupancyCapabilities(ok, no) }
    val seats = SEAT_VALUES.filter { (value, _) -> belts.any { value in it.values } }
    emit(SignalEvent.SeatSensors(available = seats.isNotEmpty()))
    emitAll(
        seats.map { (value, seat) ->
            val area = SeatArea(value)
            val sits = if (occupancy.any { value in it.values }) occupancyFlow(area) else flowOf(false)
            combine(sits, buckledFlow(area)) { occupied, buckled -> SignalEvent.SeatChanged(seat, occupied, buckled) }
        }.merge(),
    )
}

private suspend fun supportedAreas(ask: ((List<SeatArea>) -> Unit, (Exception) -> Unit) -> Unit): List<SeatArea> =
    suspendCancellableCoroutine { next ->
        ask(
            { if (next.isActive) next.resume(it) },
            {
                logFailure("SeatCapability", it)
                if (next.isActive) next.resume(emptyList())
            },
        )
    }

private fun Vehicle.occupancyFlow(area: SeatArea): Flow<Boolean> = callbackFlow {
    val listener = object : SeatOccupancyListener {
        // SDK 2.3.2 는 원값 1 을 true 로 준다. 표준 값은 1 = 빈자리, 2 = 앉음이고 에뮬레이터도 그렇게 움직여서 뒤집어 읽는다.
        // 실차 값은 아직 확인하지 못했다
        override fun onSeatOccupancyUpdated(vacant: Boolean?) {
            vacant?.let { trySend(!it) }
        }

        override fun onFailed(e: Exception) = logFailure("SeatOccupancy", e)
    }
    getSeat().registerSeatOccupancy(area, listener)
    awaitClose { getSeat().unregisterSeatOccupancy(area, listener) }
}.onStart { emit(false) }.distinctUntilChanged()

private fun Vehicle.buckledFlow(area: SeatArea): Flow<Boolean> = callbackFlow {
    val listener = object : SeatBeltBuckledStateListener {
        override fun onSeatBeltBuckledStateUpdated(buckled: Boolean?) {
            buckled?.let { trySend(it) }
        }

        override fun onFailed(e: Exception) = logFailure("SeatBelt", e)
    }
    getSeat().registerSeatBeltBuckledState(area, listener)
    awaitClose { getSeat().unregisterSeatBeltBuckledState(area, listener) }
}.onStart { emit(false) }.distinctUntilChanged()

private val SEAT_VALUES = listOf(
    SeatValue.ROW_1_LEFT to Seat.DRIVER,
    SeatValue.ROW_1_RIGHT to Seat.FRONT_PASSENGER,
    SeatValue.ROW_2_LEFT to Seat.REAR_LEFT,
    SeatValue.ROW_2_CENTER to Seat.REAR_CENTER,
    SeatValue.ROW_2_RIGHT to Seat.REAR_RIGHT,
)
