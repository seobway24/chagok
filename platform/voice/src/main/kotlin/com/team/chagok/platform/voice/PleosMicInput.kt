package com.team.chagok.platform.voice

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import android.util.Log
import com.team.chagok.core.domain.port.Heard
import com.team.chagok.core.domain.port.MicInput
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * Pleos STT → [MicInput].
 *
 * **ON_DEVICE 고정** — HYBRID/SERVER 는 원음을 업로드한다(1시간 약 115MB, architecture §5.2).
 * 화자 인식은 하지 않는다. 호명한 사람의 답으로 간주한다.
 */
class PleosMicInput(context: Context) : MicInput {

    private val engine = SpeechToText(context.applicationContext, Mode.ON_DEVICE)
    private val ready = CompletableDeferred<Boolean>()

    @Volatile
    private var current: Channel<Sound>? = null

    init {
        engine.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                engine.addListener(results)
                engine.setLocale(Locale.KOREA)
                ready.complete(true)
            }

            override fun onFailure(type: ErrorType) {
                Log.e(TAG, "STT 초기화 실패 $type")
                ready.complete(false)
            }
        })
    }

    override suspend fun listen(window: Duration): Heard {
        if (!ready.await()) return Heard.Failed
        val sounds = Channel<Sound>(Channel.UNLIMITED).also { current = it }
        engine.request()
        // 시간 초과뿐 아니라 장면이 취소돼도(깜빡이·기어 P) 마이크를 끈다 — 다음 코너 듣기와 겹치지 않게
        return try {
            collect(sounds, window).also { Log.d(TAG, "listen → ${it.javaClass.simpleName}") }
        } finally {
            current = null
            engine.stop()
        }
    }

    /** STT 는 문장마다 final 을 낸다. 이어 붙이다가 [AFTER_FINAL] 동안 새 말이 없으면 끝으로 본다. */
    private suspend fun collect(sounds: Channel<Sound>, window: Duration): Heard {
        val said = mutableListOf<String>()
        val deadline = TimeSource.Monotonic.markNow() + window
        while (true) {
            val left = -deadline.elapsedNow()
            val wait = if (said.isEmpty()) left else minOf(left, AFTER_FINAL)
            when (val sound = withTimeoutOrNull(wait) { sounds.receive() }) {
                is Sound.Final -> if (sound.text.isNotBlank()) said += sound.text.trim()
                Sound.Failed -> return said.toHeard(Heard.Failed)
                Sound.Ended, null -> return said.toHeard(Heard.Silence)
            }
        }
    }

    private fun List<String>.toHeard(empty: Heard) = if (isEmpty()) empty else Heard.Text(joinToString(" "))

    fun release() {
        engine.removeListener(results)
        engine.release()
    }

    private val results = object : ResultListener {
        override fun onUpdated(text: String, isFinal: Boolean) {
            if (isFinal) current?.trySend(Sound.Final(text))
        }

        override fun onError(type: ErrorType) {
            Log.w(TAG, "STT 오류 $type")
            current?.trySend(Sound.Failed)
        }

        override fun onEndedRecognition() { current?.trySend(Sound.Ended) }
        override fun onUpdatedEpdData(start: Long, end: Long) = Unit
        override fun onStartedRecognition() = Unit
        override fun onReady() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
    }

    private sealed interface Sound {
        data class Final(val text: String) : Sound
        data object Ended : Sound
        data object Failed : Sound
    }

    private companion object {
        const val TAG = "ChagokMic"
        val AFTER_FINAL = 1500.milliseconds
    }
}
