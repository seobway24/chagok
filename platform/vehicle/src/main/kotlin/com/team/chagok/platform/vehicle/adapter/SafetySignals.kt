package com.team.chagok.platform.vehicle.adapter

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import com.team.chagok.core.model.SignalEvent
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/*
 * 진행 흐름을 좌우하는 신호. 디바운스 없이 즉시 내보낸다.
 */

/** 방향지시등 → SafetyPause. 좌·우 구분은 필요 없다. 권한 CAR_LIGHTS. */
internal fun Vehicle.turnSignalEvents(): Flow<SignalEvent> = callbackFlow {
    val listener = object : TurnSignalStateListener {
        override fun onTurnSignalStateUpdated(value: TurnSignalValue?) {
            value ?: return
            trySend(SignalEvent.TurnSignalChanged(on = value != TurnSignalValue.OFF))
        }

        override fun onFailed(e: Exception) = logFailure("TurnSignal", e)
    }
    getTurnSignal().registerTurnSignalState(listener)
    awaitClose { getTurnSignal().unregisterTurnSignalState(listener) }
}.distinctUntilChanged()

/** 브레이크 페달 → 밟는 동안 새 코너 보류. 권한 CAR_DRIVE. */
internal fun Vehicle.brakePedalEvents(): Flow<SignalEvent> = callbackFlow {
    val listener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(pressed: Boolean?) {
            pressed ?: return
            trySend(SignalEvent.BrakePedalChanged(pressed))
        }

        override fun onFailed(e: Exception) = logFailure("Brake", e)
    }
    getBrake().registerBrakePedalStatus(listener)
    awaitClose { getBrake().unregisterBrakePedalStatus(listener) }
}.distinctUntilChanged()

/** 기어 P → 엔딩. SDK 에 주차 브레이크 리스너가 없어 기어로 판단한다. 권한 CAR_DRIVE. */
internal fun Vehicle.gearEvents(): Flow<SignalEvent> = callbackFlow {
    val listener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            if (gear != null) trySend(if (gear == VehicleGear.GEAR_PARK) SignalEvent.GearParked else SignalEvent.GearReleased)
        }

        override fun onFailed(e: Exception) = logFailure("Gear", e)
    }
    getGear().registerGearSelection(listener)
    awaitClose { getGear().unregisterGearSelection(listener) }
}
