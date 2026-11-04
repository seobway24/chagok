package com.team.chagok.core.quip

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * 앞에서부터 차례로 시도하고, 처음 성공한 줄을 쓴다.
 *
 * - [primary] 는 남은 예산 안에서만 기다린다. 넘으면 다음으로 넘어간다.
 * - [fallback] 은 반드시 즉시 답하는 엔진(템플릿)이어야 하며 시간 제한 없이 부른다.
 *   그래서 체인 전체는 실패하지 않는다.
 * - 같은 엔진이 [maxStrikes] 번 연달아 실패하면 [coolDown] 동안 건너뛴다.
 *   차에 없는 엔진(예: 에뮬레이터의 온디바이스 LLM)이 매 코너마다 예산을 다 먹지 않게 한다.
 *   쉬는 시간이 지나면 한 번 다시 시도하고, 성공하면 바로 정상으로 돌아온다.
 *   상태가 있으므로 한 번에 한 장면만 부른다는 전제(ShowEngine 의 장면은 하나씩 돈다).
 */
class ChainQuip(
    private val primary: List<QuipEngine>,
    private val fallback: QuipEngine,
    private val budget: Duration = DEFAULT_BUDGET,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val maxStrikes: Int = DEFAULT_MAX_STRIKES,
    private val coolDown: Duration = DEFAULT_COOL_DOWN,
) : QuipEngine {

    override val id: String = "chain"

    private class Health {
        var strikes = 0
        var benchedUntil: TimeMark? = null
    }

    private val health = primary.associateWith { Health() }

    override suspend fun quip(request: QuipRequest): QuipResult {
        val started = timeSource.markNow()
        for (engine in primary) {
            val state = health.getValue(engine)
            if (state.benchedUntil?.hasNotPassedNow() == true) continue
            val remaining = budget - started.elapsedNow()
            if (remaining <= Duration.ZERO) break
            val result = engine.tryWithin(remaining, request)
            if (result is QuipResult.Line) {
                state.strikes = 0
                state.benchedUntil = null
                return result
            }
            // 느리거나 고장 난 경우만 센다. 설정이 없어 바로 양보(OFFLINE)하거나 안전 필터에 걸린 건 비용이 없다
            if (result is QuipResult.Unavailable && result.reason in COSTLY) state.strike()
        }
        return fallback.quip(request)
    }

    private fun Health.strike() {
        strikes += 1
        if (strikes >= maxStrikes) {
            benchedUntil = timeSource.markNow() + coolDown
            // 쉬고 돌아와서 또 실패하면 곧바로 다시 쉰다
            strikes = maxStrikes - 1
        }
    }

    /**
     * 엔진 호출을 호출자와 끊어진 Job 으로 돌린다. withTimeout 만 쓰면 취소를 못 받는 엔진
     * (HttpURLConnection 으로 막힌 외부 LLM 등)이 끝날 때까지 기다려서 예산이 지켜지지 않는다.
     * 예산이 지나면 결과를 버리고 취소만 걸어 둔 채 바로 다음으로 간다 — 버린 호출은 제 시간 제한까지 뒤에서 돈다.
     */
    private suspend fun QuipEngine.tryWithin(limit: Duration, request: QuipRequest): QuipResult {
        val context = currentCoroutineContext()
        val call = CoroutineScope(context + Job()).async { quip(request) }
        return try {
            withTimeoutOrNull(limit) { call.await() }
                ?: QuipResult.Unavailable(QuipResult.Reason.TIMEOUT).also { call.cancel() }
        } catch (e: CancellationException) {
            call.cancel()
            // 장면이 끊긴 것(호출자 취소)이면 그대로 올린다. 엔진 안에서 난 취소는 실패로 친다
            context.ensureActive()
            QuipResult.Unavailable(QuipResult.Reason.ERROR)
        } catch (e: Exception) {
            // 카트리지 계약 위반(예외)도 체인을 멈추지 않는다
            QuipResult.Unavailable(QuipResult.Reason.ERROR)
        }
    }

    companion object {
        /** 받아치기 전체 시간 예산 (architecture §4.2). */
        val DEFAULT_BUDGET: Duration = 1_500.milliseconds
        const val DEFAULT_MAX_STRIKES = 2
        val DEFAULT_COOL_DOWN: Duration = 2.minutes
        private val COSTLY = setOf(QuipResult.Reason.TIMEOUT, QuipResult.Reason.ERROR)
    }
}
