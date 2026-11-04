package com.team.chagok.core.domain

import com.team.chagok.core.domain.signal.tunnelPasses
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/** 터널 코너는 터널을 나온 뒤 한 번. 밤에는 열리지 않는다. */
class TunnelPassTest {

    /** (전조등 값, 그 값으로 바뀐 뒤 흐른 시간) 순서대로 흘려 보낸다. */
    private fun passes(vararg steps: Pair<Boolean, Duration>): Int = run {
        val clock = TestTimeSource()
        var count = 0
        runTest {
            count = flow {
                steps.forEach { (on, hold) ->
                    emit(on)
                    clock += hold
                }
            }.tunnelPasses(time = clock).toList().size
        }
        count
    }

    @Test
    fun `short light on then off in daytime is one tunnel, counted on exit`() {
        assertEquals(1, passes(false to 1.minutes, true to 40.seconds, false to 1.minutes))
    }

    @Test
    fun `lights on for a long time is night, not a tunnel`() {
        assertEquals(0, passes(false to 1.minutes, true to 40.minutes, false to 1.minutes))
    }

    @Test
    fun `lights already on at start is night`() {
        assertEquals(0, passes(true to 30.seconds, false to 1.minutes))
    }

    @Test
    fun `a brief flash is ignored`() {
        assertEquals(0, passes(false to 1.minutes, true to 2.seconds, false to 1.minutes))
    }

    @Test
    fun `two tunnels in a row count twice`() {
        assertEquals(2, passes(false to 1.minutes, true to 20.seconds, false to 30.seconds, true to 1.minutes, false to 10.seconds))
    }
}
