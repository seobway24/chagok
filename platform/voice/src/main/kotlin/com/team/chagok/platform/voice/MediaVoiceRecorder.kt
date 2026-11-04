package com.team.chagok.platform.voice

import android.content.Context
import android.media.AudioManager
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.util.Log
import com.team.chagok.core.domain.port.RecordedVoice
import com.team.chagok.core.domain.port.VoiceRecorder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * 8초 녹음. AAC-LC 16kHz 모노 32kbps. 시작과 끝에 짧은 소리를 내 녹음 중임을 숨기지 않는다.
 *
 * 임시 파일은 앱 전용 저장소에 있고 어떤 경로로 끝나든 지운다. 전 실행의 찌꺼기는 생성할 때 지운다.
 */
class MediaVoiceRecorder(context: Context) : VoiceRecorder {

    private val tmp = File(context.applicationContext.noBackupFilesDir, "rec.tmp")
    private val tone by lazy { ToneGenerator(AudioManager.STREAM_MUSIC, TONE_VOLUME) }

    @Volatile
    private var active: Session? = null

    init {
        tmp.delete()
    }

    override suspend fun record(max: Duration): RecordedVoice? {
        beep()
        delay(TONE_GAP)
        val session = Session(start(max) ?: return null)
        active = session
        val startedAt = System.currentTimeMillis()
        return try {
            val finished = withTimeoutOrNull(max + STOP_MARGIN) { session.done.await() } != false
            if (!finished) return null
            session.release()
            val length = (System.currentTimeMillis() - startedAt).coerceAtMost(max.inWholeMilliseconds)
            beep()
            withContext(Dispatchers.IO) { tmp.readBytes() }.takeIf { it.isNotEmpty() }?.let { RecordedVoice(it, length) }
        } finally {
            session.release()
            active = null
            tmp.delete()
        }
    }

    override fun cancel() {
        active?.done?.complete(false)
    }

    private fun start(max: Duration): MediaRecorder? {
        @Suppress("DEPRECATION") // minSdk 28. Context 생성자는 31 부터
        val recorder = MediaRecorder()
        return runCatching {
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(SAMPLE_RATE)
                setAudioChannels(1)
                setAudioEncodingBitRate(BIT_RATE)
                setMaxDuration(max.inWholeMilliseconds.toInt())
                setOutputFile(tmp.path)
                prepare()
                start()
            }
        }.getOrElse {
            Log.w(TAG, "녹음 시작 실패: ${it.javaClass.simpleName}")
            recorder.release()
            tmp.delete()
            null
        }
    }

    private fun beep() {
        tone.startTone(ToneGenerator.TONE_PROP_BEEP, TONE_MS)
    }

    private class Session(private val recorder: MediaRecorder) {
        /** true = 끝까지 녹음, false = 취소. */
        val done = CompletableDeferred<Boolean>()
        private var released = false

        init {
            recorder.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) done.complete(true)
            }
        }

        /** 시간 한도에 닿으면 시스템이 이미 멈춘 상태라 stop 의 오류는 소리 저장과 무관하다. */
        fun release() {
            if (released) return
            released = true
            runCatching { recorder.stop() }
            recorder.release()
        }
    }

    private companion object {
        const val TAG = "ChagokRecord"
        const val SAMPLE_RATE = 16_000
        const val BIT_RATE = 32_000
        const val TONE_VOLUME = 60
        const val TONE_MS = 150
        val STOP_MARGIN = 1500.milliseconds
        val TONE_GAP = 250.milliseconds
    }
}
