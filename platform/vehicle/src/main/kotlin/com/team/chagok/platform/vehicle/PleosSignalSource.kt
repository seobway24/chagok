package com.team.chagok.platform.vehicle

import ai.pleos.playground.vehicle.Vehicle
import android.content.Context
import android.util.Log
import com.team.chagok.core.domain.port.SignalSource
import com.team.chagok.core.model.SignalEvent
import com.team.chagok.platform.vehicle.adapter.TAG
import com.team.chagok.platform.vehicle.adapter.brakePedalEvents
import com.team.chagok.platform.vehicle.adapter.doorEvents
import com.team.chagok.platform.vehicle.adapter.gearEvents
import com.team.chagok.platform.vehicle.adapter.lightEvents
import com.team.chagok.platform.vehicle.adapter.milestoneEvents
import com.team.chagok.platform.vehicle.adapter.seatEvents
import com.team.chagok.platform.vehicle.adapter.turnSignalEvents
import com.team.chagok.platform.vehicle.adapter.wiperEvents
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach

/**
 * Pleos Vehicle SDK + CarUxRestrictions → [SignalEvent].
 *
 * 수집하는 동안만 SDK 에 붙어 있고, 수집이 끝나면 모든 리스너를 풀고 release 한다.
 * 조회만 하며 차량을 제어하지 않는다.
 */
class PleosSignalSource(
    context: Context,
    private val config: SignalConfig = SignalConfig(),
) : SignalSource {

    private val appContext = context.applicationContext

    override val events: Flow<SignalEvent> = flow {
        val vehicle = Vehicle(appContext)
        vehicle.initialize()
        try {
            emitAll(
                merge(
                    DrivingGate(appContext).events(),
                    vehicle.turnSignalEvents(),
                    vehicle.brakePedalEvents(),
                    vehicle.gearEvents(),
                    vehicle.wiperEvents(config.rainAfter),
                    vehicle.lightEvents(config.tunnelAfter),
                    vehicle.doorEvents(config.doorSettle),
                    vehicle.milestoneEvents(config.milestoneStepKm),
                    vehicle.seatEvents(),
                ).onEach { Log.d(TAG, "event $it") },
            )
        } finally {
            vehicle.release()
        }
    }
}
