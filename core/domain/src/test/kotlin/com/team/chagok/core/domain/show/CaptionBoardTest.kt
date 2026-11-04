package com.team.chagok.core.domain.show

import com.team.chagok.core.domain.port.AudioZone
import com.team.chagok.core.domain.port.Heard
import com.team.chagok.core.domain.port.HostVoice
import com.team.chagok.core.domain.port.MicInput
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Duration

/** 화면 자막은 실제로 말한 줄만. 깜빡이로 끊긴 반응이 나중에 정차 화면에 글자로 남으면 안 된다(시연 녹화 11/03). */
class CaptionBoardTest {

    private val board = CaptionBoard()

    @Test
    fun `a line spoken to the end stays on screen`() = runTest {
        val voice = board.voice(object : HostVoice {
            override suspend fun speak(text: String, speaker: String, zone: AudioZone) = true
            override fun stop() = Unit
        })
        voice.speak("떡볶이요! 다들 들었죠?", "haeun")
        assertEquals(Caption("떡볶이요! 다들 들었죠?", fromHost = true), board.current.value)
    }

    @Test
    fun `a line cut by stop is taken off the screen`() = runTest {
        val gate = CompletableDeferred<Boolean>()
        val voice = board.voice(object : HostVoice {
            override suspend fun speak(text: String, speaker: String, zone: AudioZone) = gate.await()
            override fun stop() { gate.complete(false) }
        })
        val line = async { voice.speak("그 대답 기다렸어요, 막내!", "haeun") }
        runCurrent()
        voice.stop()
        line.await()
        assertNull(board.current.value)
    }

    @Test
    fun `a line whose scene was cancelled is taken off the screen`() = runTest {
        val voice = board.voice(object : HostVoice {
            override suspend fun speak(text: String, speaker: String, zone: AudioZone) = CompletableDeferred<Boolean>().await()
            override fun stop() = Unit
        })
        val line = async { voice.speak("그 대답 기다렸어요, 막내!", "haeun") }
        runCurrent()
        line.cancel()
        runCurrent()
        assertNull(board.current.value)
    }

    @Test
    fun `cutting an old line does not wipe a newer caption`() = runTest {
        val gate = CompletableDeferred<Boolean>()
        val voice = board.voice(object : HostVoice {
            override suspend fun speak(text: String, speaker: String, zone: AudioZone) = gate.await()
            override fun stop() { gate.complete(false) }
        })
        val heard = board.mic(object : MicInput {
            override suspend fun listen(window: Duration) = Heard.Text("백 점이요")
        })
        val line = async { voice.speak("곧 도착해요!", "haeun") }
        runCurrent()
        heard.listen(Duration.ZERO)
        voice.stop()
        line.await()
        assertEquals(Caption("백 점이요", fromHost = false), board.current.value)
    }
}
