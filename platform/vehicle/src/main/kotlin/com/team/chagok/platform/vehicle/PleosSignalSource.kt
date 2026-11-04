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
import kotlinx.coroutines.flow.catch
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

    /**
     * 차량 서비스가 없거나 신호 하나가 실패해도 앱은 죽지 않는다. 실패한 신호만 빠지고 나머지(내비 포함)는 그대로 돈다.
     * 주행 판단(CarUxRestrictions)이 실패하면 대기 상태의 기본값(주행 중)이 유지돼 안전한 쪽으로 남는다.
     */
    override val events: Flow<SignalEvent> = flow {
        val vehicle = runCatching { Vehicle(appContext).also { it.initialize() } }
            .onFailure { Log.w(TAG, "Vehicle SDK unavailable", it) }
            .getOrNull()
        try {
            val carSignals = vehicle?.let {
                listOf(
                    it.turnSignalEvents(),
                    it.brakePedalEvents(),
                    it.gearEvents(),
                    it.wiperEvents(config.rainAfter),
                    it.lightEvents(config.tunnelAfter),
                    it.doorEvents(config.doorSettle),
                    it.milestoneEvents(config.milestoneStepKm),
                    it.seatEvents(),
                )
            }.orEmpty()
            val sources = listOf(DrivingGate(appContext).events()) + carSignals
            emitAll(merge(*sources.map { it.dropOnFailure() }.toTypedArray()).onEach { Log.d(TAG, "event $it") })
        } finally {
            vehicle?.let { runCatching { it.release() } }
        }
    }

    private fun Flow<SignalEvent>.dropOnFailure(): Flow<SignalEvent> =
        catch { Log.w(TAG, "Vehicle signal stopped", it) }
}
