package com.team.chagok.platform.navi

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.util.Log

/**
 * 디버그 전용 실측. 내비 SDK 가 주는 남은 시간·거리 원값과 단위를 로그(태그 ChagokNaviProbe)로 남긴다.
 * 경로를 하나 요청하므로 [enabled] 일 때만 돈다: `adb shell setprop debug.chagok.navi_probe 1`.
 */
object PleosNaviProbe {

    private const val TAG = "ChagokNaviProbe"

    fun enabled(): Boolean = runCatching {
        val sp = Class.forName("android.os.SystemProperties")
        sp.getMethod("get", String::class.java, String::class.java).invoke(null, "debug.chagok.navi_probe", "0") == "1"
    }.getOrDefault(false)

    fun run(context: Context) {
        val navi = runCatching { NaviHelper(context.applicationContext) }.getOrElse {
            Log.w(TAG, "NaviHelper unavailable", it)
            return
        }
        navi.addListener(object : NaviHelperEventListener {
            override fun onRouteStarted(info: RouteStartInfo) =
                log("routeStarted name=${info.name} duration=${info.duration} distance=${info.distance}")
            override fun onDestinationInfo(info: DestinationInfo) =
                log("destinationInfo name=${info.name} remainTime=${info.remainTime} remainDistance=${info.remainDistance}")
            override fun onDrivingInfo(info: DrivingInfo) =
                log("drivingInfo name=${info.destination.name} duration=${info.destination.duration} distance=${info.destination.distance}")
            override fun onDestinationArrived(info: DestinationArrivedInfo) =
                log("arrived duration=${info.duration} distance=${info.distance}")
            override fun onRouteEnded() = log("routeEnded")
            override fun onRouteCancelled() = log("routeCancelled")
            override fun onRoutingCompleted() = log("routingCompleted")
            override fun onNavigationStatus(status: NavigationStatus) {
                log("status=$status")
                runCatching { navi.getDestinationInfo() }
            }
            override fun onError(code: NaviErrorCode) = log("error=$code")
        })
        navi.initialize(
            {
                log("initialized")
                runCatching {
                    // 서울역 → 시연용 목적지. 좌표는 로그에 남기지 않는다
                    navi.requestRoute(
                        RouteInfo(37.5547, 126.9707, null, null, "서울역", "", "", "", RouteOption.RECOMMENDED, "", false),
                    )
                    log("route requested")
                }.onFailure { Log.w(TAG, "requestRoute failed", it) }
            },
            { Log.w(TAG, "init failed", it) },
        )
    }

    private fun log(message: String) {
        Log.i(TAG, message)
    }
}
