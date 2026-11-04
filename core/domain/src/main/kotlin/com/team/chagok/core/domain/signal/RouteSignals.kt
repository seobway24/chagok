package com.team.chagok.core.domain.signal

import com.team.chagok.core.model.SignalEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** 내비가 알려 준 경로 값. 플랫폼 어댑터가 SDK 콜백을 이걸로 바꾼다. 좌표는 담지 않는다. */
sealed interface RouteReading {
    /** 경로 안내 중. [remaining] = 목적지까지 남은 시간(모르면 null). */
    data class Guiding(val destination: String, val remaining: Duration?) : RouteReading

    /** 목적지에 도착했다. */
    data object Arrived : RouteReading

    /** 취소·안내 종료. */
    data object Ended : RouteReading
}

/**
 * 경로 값 → 앱 신호.
 * - 목적지가 바뀌면 [SignalEvent.RouteChanged] (같은 이름이 반복되면 다시 안 보낸다)
 * - 남은 시간이 1분 넘게 바뀌면 [SignalEvent.RemainingChanged] (30초 폴링마다 보내지 않는다)
 * - 남은 시간이 [soon] 이하로 처음 내려가면 [SignalEvent.ArrivingSoon] 한 번. 목적지가 바뀌면 다시 셀 수 있다
 * - 도착하면 [SignalEvent.Arrived]. 목적지 이름은 그대로 둔다(엔딩까지 제목에 남는다)
 * - 취소·종료면 RouteChanged(null)
 */
fun Flow<RouteReading>.routeEvents(soon: Duration = DEFAULT_ARRIVING_SOON): Flow<SignalEvent> = flow {
    var destination: String? = null
    var announced = false
    var lastLeft: Duration? = null
    collect { reading ->
        when (reading) {
            is RouteReading.Guiding -> {
                if (reading.destination != destination) {
                    destination = reading.destination
                    announced = false
                    lastLeft = null
                    emit(SignalEvent.RouteChanged(reading.destination))
                }
                val left = reading.remaining
                val last = lastLeft
                if (left != null && left > Duration.ZERO && (last == null || (left - last).absoluteValue >= REMAINING_STEP)) {
                    lastLeft = left
                    emit(SignalEvent.RemainingChanged(left))
                }
                if (!announced && left != null && left > Duration.ZERO && left <= soon) {
                    announced = true
                    emit(SignalEvent.ArrivingSoon)
                }
            }
            RouteReading.Arrived -> if (destination != null) {
                destination = null
                announced = false
                lastLeft = null
                emit(SignalEvent.Arrived)
            }
            RouteReading.Ended -> if (destination != null) {
                destination = null
                announced = false
                lastLeft = null
                emit(SignalEvent.RouteChanged(null))
            }
        }
    }
}

private val REMAINING_STEP: Duration = 1.minutes

/**
 * 내비가 준 남은 시간 원값 → [Duration]. 단위는 초(에뮬레이터 실측 11/04: 23km · 3960 = 66분).
 * 펌웨어가 단위를 바꿔도 엉뚱한 때 예고하지 않도록 남은 거리로 속도를 따져 고친다:
 * 초로 읽어 시속 900km 를 넘으면 분, 시속 0.36km 아래면 밀리초로 본다. 0 이하·하루 넘는 값은 모름(null).
 */
fun remainingTime(raw: Int?, remainMeters: Int? = null): Duration? {
    if (raw == null || raw <= 0) return null
    var seconds = raw.toDouble()
    if (remainMeters != null && remainMeters > 0) {
        val speed = remainMeters / seconds // m/s
        if (speed > MAX_SPEED_MPS) seconds *= 60
        else if (speed < MIN_SPEED_MPS) seconds /= 1000
    }
    if (seconds > DAY_SECONDS || seconds < 1) return null
    return seconds.toLong().seconds
}

private const val MAX_SPEED_MPS = 250.0 // 900 km/h
private const val MIN_SPEED_MPS = 0.1 // 0.36 km/h
private const val DAY_SECONDS = 86_400.0

/** 도착 예고 시점. 짧으면 코너가 끝나기 전에 도착하고, 길면 예고가 너무 이르다. */
val DEFAULT_ARRIVING_SOON: Duration = 3.minutes
