package com.team.chagok.platform.vehicle.adapter

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import com.team.chagok.core.domain.signal.tunnelPasses
import com.team.chagok.core.model.Seat
import com.team.chagok.core.model.SignalEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.transformLatest
import kotlin.time.Duration

/*
 * 코너를 여는 신호. 순간 변화가 아니라 "상황"이 되었을 때만 내보낸다(디바운스).
 */

/** 앞 유리 와이퍼가 [after] 동안 계속 돌면 RainStarted. 권한 CAR_WINDOWS. */
internal fun Vehicle.wiperEvents(after: Duration): Flow<SignalEvent> = callbackFlow {
    val area = WindowArea(WindowValue.FRONT_WINDSHIELD)
    val listener = object : WiperStateListener {
        override fun onWiperStateUpdated(state: WiperState?) {
            state ?: return
            trySend(state == WiperState.ON)
        }

        override fun onFailed(e: Exception) = logFailure("Wiper", e)
    }
    getWiper().registerWiperState(area, listener)
    awaitClose { getWiper().unregisterWiperState(area, listener) }
}.whenSustained(after, SignalEvent.RainStarted, SignalEvent.RainStopped)

/**
 * 전조등(로우빔)이 잠깐([minOn] 이상) 켜졌다가 꺼지면 = 터널을 지났다 → LightsOn(터널 코너) 한 번. 권한 CAR_LIGHTS.
 * 터널 안이 아니라 나온 뒤에 열고, 오래 켜져 있는 밤에는 열지 않는다([tunnelPasses]).
 * 꺼지는 순간 보내므로 기다리던 터널 코너를 거둘 LightsOff 는 더 이상 보내지 않는다.
 */
internal fun Vehicle.lightEvents(minOn: Duration): Flow<SignalEvent> = callbackFlow {
    val listener = object : LightStateListener {
        override fun onLightStateUpdated(state: LightState?) {
            state ?: return
            trySend(state == LightState.ON)
        }

        override fun onFailed(e: Exception) = logFailure("Light", e)
    }
    getLight().registerLowBeamLightState(listener)
    awaitClose { getLight().unregisterLowBeamLightState(listener) }
}.tunnelPasses(minOn = minOn).map { SignalEvent.LightsOn }

/** 좌석별 문. 열리면 즉시 DoorOpened, 닫히고 [settle] 뒤 DoorClosed. 권한 CAR_DOORS. */
internal fun Vehicle.doorEvents(settle: Duration): Flow<SignalEvent> =
    DOOR_SEATS.map { (door, seat) -> doorOpenFlow(door).toDoorEvents(seat, settle) }.merge()

private fun Vehicle.doorOpenFlow(door: DoorValue): Flow<Boolean> = callbackFlow {
    val area = DoorArea(door)
    val listener = object : DoorPositionListener {
        override fun onPositionUpdated(position: Int?) {
            position ?: return
            trySend(position > 0)
        }

        override fun onFailed(e: Exception) = logFailure("Door", e)
    }
    getDoor().registerDoorPosition(area, listener)
    awaitClose { getDoor().unregisterDoorPosition(area, listener) }
}.distinctUntilChanged()

@OptIn(ExperimentalCoroutinesApi::class)
private fun Flow<Boolean>.toDoorEvents(seat: Seat, settle: Duration): Flow<SignalEvent> =
    transformLatest { open ->
        if (open) {
            emit(SignalEvent.DoorOpened(seat))
        } else {
            delay(settle)
            emit(SignalEvent.DoorClosed(seat))
        }
    }

private val DOOR_SEATS = listOf(
    DoorValue.ROW_1_LEFT to Seat.DRIVER,
    DoorValue.ROW_1_RIGHT to Seat.FRONT_PASSENGER,
    DoorValue.ROW_2_LEFT to Seat.REAR_LEFT,
    DoorValue.ROW_2_RIGHT to Seat.REAR_RIGHT,
)

/** 누적 주행거리가 [stepKm] 의 배수를 넘을 때마다. 원값 km 는 밖으로 내보내지 않는다. 권한 CAR_DRIVE. */
internal fun Vehicle.milestoneEvents(stepKm: Int): Flow<SignalEvent> = callbackFlow {
    val listener = object : DistanceDrivenListener {
        override fun onDistanceDrivenUpdated(km: Float?) {
            km ?: return
            trySend((km / stepKm).toInt())
        }

        override fun onFailed(e: Exception) = logFailure("Odometer", e)
    }
    getOdometer().registerDistanceDriven(listener)
    awaitClose { getOdometer().unregisterDistanceDriven(listener) }
}.distinctUntilChanged()
    .dropFirstReading()
    .mapNotNull { step -> SignalEvent.MilestoneKm(step * stepKm) }

/** 앱을 켰을 때의 첫 값은 "넘었다"가 아니라 현재 위치다. */
private fun <T> Flow<T>.dropFirstReading(): Flow<T> = flow {
    var first = true
    collect { if (first) first = false else emit(it) }
}
