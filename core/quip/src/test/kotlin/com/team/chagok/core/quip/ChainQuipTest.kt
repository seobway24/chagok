package com.team.chagok.core.quip

import com.team.chagok.core.model.SignalCategory
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class ChainQuipTest {

    private val request = QuipRequest(
        cornerId = "weather",
        skinId = "basic",
        speaker = "지호",
        utterance = "100점",
        signal = SignalCategory.RAIN,
        kidSafe = true,
    )

    private val template = FixedQuip("template", "템플릿 대사")

    @Test
    fun `first engine answers within budget`() = runTest {
        val chain = ChainQuip(primary = listOf(FixedQuip("llm", "LLM 대사")), fallback = template)

        assertEquals("llm", (chain.quip(request) as QuipResult.Line).engineId)
    }

    @Test
    fun `slow engine falls back to template`() = runTest {
        val slow = FixedQuip("llm", "늦은 대사", delayMs = 5_000)
        val chain = ChainQuip(primary = listOf(slow), fallback = template)

        assertEquals("template", (chain.quip(request) as QuipResult.Line).engineId)
    }

    @Test
    fun `throwing engine does not break the chain`() = runTest {
        val broken = object : QuipEngine {
            override val id = "broken"
            override suspend fun quip(request: QuipRequest): QuipResult = error("boom")
        }
        val chain = ChainQuip(primary = listOf(broken), fallback = template)

        assertEquals("template", (chain.quip(request) as QuipResult.Line).engineId)
    }

    @Test
    fun `unavailable engine is skipped`() = runTest {
        val offline = object : QuipEngine {
            override val id = "remote"
            override suspend fun quip(request: QuipRequest) =
                QuipResult.Unavailable(QuipResult.Reason.OFFLINE)
        }
        val chain = ChainQuip(primary = listOf(offline, FixedQuip("llm", "대사")), fallback = template)

        assertEquals("llm", (chain.quip(request) as QuipResult.Line).engineId)
    }

    /** 차에 없는 온디바이스 LLM 처럼: 부르면 영영 답이 없다. [answers] 가 true 면 바로 답한다. */
    private class SilentQuip(override val id: String) : QuipEngine {
        var calls = 0
        var answers = false
        override suspend fun quip(request: QuipRequest): QuipResult {
            calls += 1
            if (answers) return QuipResult.Line("온디바이스 대사", id, 0)
            delay(60_000)
            error("unreachable")
        }
    }

    @Test
    fun `engine that keeps timing out is benched so the next engine gets the budget`() = runTest {
        // 예산 계산도 가상 시간으로 해야 기다린 만큼 예산이 줄어든다
        val onDevice = SilentQuip("pleos-llm")
        val chain = ChainQuip(listOf(onDevice, FixedQuip("remote", "원격 대사")), template, timeSource = testScheduler.timeSource)

        // 두 번은 기다리다 시간이 다 돼 템플릿
        repeat(2) { assertEquals("template", (chain.quip(request) as QuipResult.Line).engineId) }
        // 세 번째부터 건너뛰고 다음 엔진이 바로 답한다
        assertEquals("remote", (chain.quip(request) as QuipResult.Line).engineId)
        assertEquals(2, onDevice.calls)
    }

    @Test
    fun `benched engine is tried again after cool down and recovers`() = runTest {
        val onDevice = SilentQuip("pleos-llm")
        val chain = ChainQuip(listOf(onDevice), template, timeSource = testScheduler.timeSource, coolDown = 2.minutes)
        repeat(2) { chain.quip(request) }

        delay(1.minutes)
        chain.quip(request)
        assertEquals(2, onDevice.calls)

        delay(2.minutes)
        onDevice.answers = true
        assertEquals("pleos-llm", (chain.quip(request) as QuipResult.Line).engineId)
        assertEquals(3, onDevice.calls)
    }

    @Test
    fun `engine that steps aside instantly is never benched`() = runTest {
        var calls = 0
        val offline = object : QuipEngine {
            override val id = "remote"
            override suspend fun quip(request: QuipRequest): QuipResult {
                calls += 1
                return QuipResult.Unavailable(QuipResult.Reason.OFFLINE)
            }
        }
        val chain = ChainQuip(listOf(offline), template, timeSource = testScheduler.timeSource)

        repeat(5) { chain.quip(request) }
        assertEquals(5, calls)
    }

    private class FixedQuip(
        override val id: String,
        private val text: String,
        private val delayMs: Long = 0,
    ) : QuipEngine {
        override suspend fun quip(request: QuipRequest): QuipResult {
            delay(delayMs)
            return QuipResult.Line(text, id, delayMs)
        }
    }
}
