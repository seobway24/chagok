package com.team.chagok.platform.vehicle.adapter

import android.util.Log
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest
import kotlin.time.Duration

internal const val TAG = "ChagokSignal"

/** SDK 콜백 실패는 신호 하나를 잃을 뿐 방송을 멈추지 않는다. 기록만 남긴다. */
internal fun logFailure(signal: String, e: Exception) {
    Log.w(TAG, "$signal 신호 실패", e)
}

/** true 가 [duration] 동안 이어지면 [started] 를 한 번 내보낸다. false 가 오면 처음부터 다시 세고 [stopped] 를 내보낸다. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T> Flow<Boolean>.whenSustained(duration: Duration, started: T, stopped: T): Flow<T> =
    distinctUntilChanged().transformLatest { on ->
        if (on) {
            delay(duration)
            emit(started)
        } else {
            emit(stopped)
        }
    }
