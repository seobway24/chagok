package com.team.chagok.platform.navi

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.util.Log
import com.team.chagok.core.domain.port.SignalSource
import com.team.chagok.core.domain.signal.RouteReading
import com.team.chagok.core.domain.signal.remainingTime
import com.team.chagok.core.domain.signal.routeEvents
import com.team.chagok.core.model.SignalEvent
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Pleos NaviHelper → 경로 신호. 권한 pleos.car.permission.NAVI_ROUTE (normal, 이 모듈 매니페스트).
 *
 * 받는 것: 목적지 이름, 남은 시간·거리(단위 검산용). 좌표·주소·요금은 읽지 않는다.
 * 내비 서비스가 없거나 초기화가 실패하면 아무 신호도 내지 않는다 — 방송은 내비 없이도 돈다.
 *
 * 남은 시간 단위는 초(에뮬레이터 실측 11/04, [remainingTime] 이 거리로 검산).
 * 안내 중에는 [poll] 마다 목적지 정보를 다시 물어 남은 시간을 갱신한다.
 */
class PleosRouteSource(
    context: Context,
    private val poll: Duration = 30.seconds,
) : SignalSource {

    private val appContext = context.applicationContext

    override val events: Flow<SignalEvent> = readings().routeEvents()

    private fun readings(): Flow<RouteReading> = callbackFlow {
        val navi = runCatching { NaviHelper(appContext) }.getOrElse {
            Log.w(TAG, "NaviHelper unavailable", it)
            awaitClose { }
            return@callbackFlow
        }
        var guiding = false

        fun guide(name: String?, remain: Int?, meters: Int?) {
            val dest = name?.trim().orEmpty()
            if (dest.isEmpty()) return // 안내 전에는 이름 "" · 시간 0 이 온다(실측)
            // 경로 시작 전 FREE_DRIVE 에도 drivingInfo 가 이름만 채워 0·0 으로 온다(실측 11/04) — 안내로 보지 않는다
            if ((remain ?: 0) <= 0 && (meters ?: 0) <= 0) return
            guiding = true
            trySend(RouteReading.Guiding(dest, remainingTime(remain, meters)))
        }

        fun end(reading: RouteReading = RouteReading.Ended) {
            guiding = false
            trySend(reading)
        }

        val listener = object : NaviHelperEventListener {
            override fun onRouteStarted(info: RouteStartInfo) = guide(info.name, info.duration, info.distance)
            override fun onDestinationInfo(info: DestinationInfo) = guide(info.name, info.remainTime, info.remainDistance)
            override fun onDrivingInfo(info: DrivingInfo) {
                val d = info.destination
                guide(d.name, d.duration, d.distance)
            }
            override fun onDestinationArrived(info: DestinationArrivedInfo) = end(RouteReading.Arrived)
            // onRouteEnded 는 안내 종료가 아니다: 경로 탐색 직후 routeStarted 와 함께 온다(실측 11/04). 끝은 도착·취소·상태로 본다
            override fun onRouteCancelled() = end()
            override fun onNavigationStatus(status: NavigationStatus) {
                if (status == NavigationStatus.ROUTE_DRIVE) safely { navi.getDestinationInfo() } else if (guiding) end()
            }
            override fun onError(code: NaviErrorCode) {
                Log.w(TAG, "Navi error $code")
            }
        }

        safely {
            navi.addListener(listener)
            navi.initialize(
                { safely { navi.getDestinationInfo() } },
                { Log.w(TAG, "NaviHelper init failed", it) },
            )
        }
        val poller = launch {
            while (true) {
                delay(poll)
                if (guiding) safely { navi.getDestinationInfo() }
            }
        }
        awaitClose {
            poller.cancel()
            safely {
                navi.removeListener(listener)
                navi.release()
            }
        }
    }

    private inline fun safely(block: () -> Unit) {
        runCatching(block).onFailure { Log.w(TAG, "Navi call failed", it) }
    }

    private companion object {
        const val TAG = "ChagokNavi"
    }
}
