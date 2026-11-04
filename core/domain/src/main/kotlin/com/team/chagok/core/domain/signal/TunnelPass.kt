package com.team.chagok.core.domain.signal

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * 전조등 켜짐/꺼짐 → "터널을 지났다".
 *
 * - 낮에 전조등이 잠깐([minOn]~[maxOn]) 켜졌다가 **꺼지는 순간** 한 번 내보낸다 — 터널을 나온 뒤라 운전 부담이 덜하다.
 * - [maxOn] 보다 오래 켜져 있었으면 밤이다. 내보내지 않는다 — 밤마다 터널 코너가 열리지 않게.
 * - 앱을 켰을 때 이미 켜져 있었으면(밤에 출발) 그 켜짐은 세지 않는다.
 */
fun Flow<Boolean>.tunnelPasses(
    minOn: Duration = DEFAULT_MIN_ON,
    maxOn: Duration = DEFAULT_MAX_ON,
    time: TimeSource = TimeSource.Monotonic,
): Flow<Unit> = flow {
    var onSince: TimeMark? = null
    var first = true
    distinctUntilChanged().collect { on ->
        val atStart = first
        first = false
        if (on) {
            onSince = if (atStart) null else time.markNow()
            return@collect
        }
        val held = onSince?.elapsedNow()
        onSince = null
        if (held != null && held >= minOn && held <= maxOn) emit(Unit)
    }
}

/** 터널로 볼 최소 켜짐. 한 번 깜빡 켠 건 무시. */
val DEFAULT_MIN_ON: Duration = 5.seconds

/** 이보다 길면 터널이 아니라 밤·흐린 날이다. 국내 최장 도로 터널도 10분 안에 지난다. */
val DEFAULT_MAX_ON: Duration = 10.minutes
