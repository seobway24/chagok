package com.team.chagok.core.domain.show

import com.team.chagok.core.domain.port.AudioZone
import com.team.chagok.core.domain.port.Heard
import com.team.chagok.core.domain.port.HostVoice
import com.team.chagok.core.domain.port.MicInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Duration

/** 화면 자막 한 줄. 진행자가 읽는 말이거나 방금 들은 대답. */
data class Caption(val text: String, val fromHost: Boolean)

/**
 * 진행자 목소리·마이크를 감싸 지금 오가는 말을 화면에 보여 준다.
 * 마지막 한 줄만 들고 있고 저장하지 않는다.
 */
class CaptionBoard {
    private val _current = MutableStateFlow<Caption?>(null)
    val current: StateFlow<Caption?> = _current.asStateFlow()

    fun voice(inner: HostVoice): HostVoice = object : HostVoice {
        override suspend fun speak(text: String, speaker: String, zone: AudioZone): Boolean {
            _current.value = Caption(text, fromHost = true)
            return inner.speak(text, speaker, zone)
        }

        override fun stop() = inner.stop()
    }

    fun mic(inner: MicInput): MicInput = object : MicInput {
        override suspend fun listen(window: Duration): Heard =
            inner.listen(window).also { if (it is Heard.Text) _current.value = Caption(it.text, fromHost = false) }
    }

    fun clear() {
        _current.value = null
    }
}
