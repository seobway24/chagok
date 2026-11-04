package com.team.chagok.platform.vehicle

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import com.team.chagok.core.model.SignalEvent
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 주행/정차의 단일 출처.
 *
 * Pleos 원칙: 앱은 속도·기어로 주행 여부를 판정하지 않는다.
 * 플랫폼이 알려 주는 CarUxRestrictions 만 따른다.
 */
internal class DrivingGate(private val context: Context) {

    fun events(): Flow<SignalEvent> = callbackFlow {
        val car = Car.createCar(context)
        val manager = car.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager

        manager.registerListener { restrictions ->
            trySend(SignalEvent.DrivingChanged(restrictions.isRequiresDistractionOptimization))
        }
        manager.currentCarUxRestrictions?.let {
            trySend(SignalEvent.DrivingChanged(it.isRequiresDistractionOptimization))
        }

        awaitClose {
            manager.unregisterListener()
            car.disconnect()
        }
    }.distinctUntilChanged()
}
