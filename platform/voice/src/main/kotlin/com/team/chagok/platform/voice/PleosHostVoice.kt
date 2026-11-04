package com.team.chagok.platform.voice

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.util.Log
import com.team.chagok.core.domain.port.AudioZone
import com.team.chagok.core.domain.port.HostVoice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale

/**
 * Pleos TTS → [HostVoice]. ON_DEVICE 고정이라 대사가 차 밖으로 나가지 않는다.
 *
 * SDK 주의 (에뮬레이터 검증 10/29, research/05_tts_lab_results.md):
 * - initialize() 성공 뒤에 addEventListener() — 순서가 바뀌면 SDK 내부 lateinit 크래시
 * - 화자 설정이 앱 단위로 남으므로 말할 때마다 다시 지정한다
 */
class PleosHostVoice(context: Context) : HostVoice {

    private val engine = TextToSpeech(context.applicationContext, Mode.ON_DEVICE)
    private val ready = CompletableDeferred<Boolean>()
    private val speaking = Mutex()

    /** 지금 읽고 있는 한 줄. onDone/onError/onStop 이 완료시킨다. */
    @Volatile
    private var current: CompletableDeferred<Boolean>? = null

    init {
        engine.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                engine.addEventListener(events)
                engine.setLocale(Locale.KOREA)
                ready.complete(true)
            }

            override fun onFailure(type: ErrorType) {
                Log.e(TAG, "TTS 초기화 실패 $type")
                ready.complete(false)
            }
        })
    }

    override suspend fun speak(text: String, speaker: String, zone: AudioZone): Boolean {
        if (!ready.await()) return false
        return speaking.withLock {
            val done = CompletableDeferred<Boolean>().also { current = it }
            runCatching {
                engine.setSpeakerType(speaker)
                engine.setAudioZoneId(zone.toZoneId())
                engine.speak(text, true)
            }.onFailure {
                Log.e(TAG, "speak 실패", it)
                done.complete(false)
            }
            done.await().also { Log.d(TAG, "speak($speaker) ${if (it) "끝" else "끊김"} (${text.length}자)") }
        }
    }

    override fun stop() {
        engine.stop()
        current?.complete(false)
    }

    fun release() = engine.release()

    private val events = object : EventListener {
        override fun onStart() = Unit
        override fun onReady() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onDone() { current?.complete(true) }

        // stop() 이 이미 그 줄을 끝냈다. onStop 은 늦게 와서, 바로 뒤에 시작한 다음 줄(엔딩 등)을
        // 끊은 것으로 잘못 끝내 버린다(10/30 에뮬레이터: 코너 중 기어 P → 엔딩 멘트 "끊김")
        override fun onStop() = Unit
        override fun onError(type: ErrorType) {
            Log.w(TAG, "TTS 오류 $type")
            current?.complete(false)
        }
    }

    /** 0 = 전체, 1·2 = 뒷좌석 장치 (에뮬레이터 검증 · 실차 확인 필요). */
    private fun AudioZone.toZoneId() = when (this) {
        AudioZone.ALL -> 0
        AudioZone.REAR -> 1
    }

    private companion object {
        const val TAG = "ChagokVoice"
    }
}
