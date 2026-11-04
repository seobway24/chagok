package com.team.chagok.platform.voice

import android.media.AudioAttributes
import android.media.MediaDataSource
import android.media.MediaPlayer
import android.util.Log
import com.team.chagok.core.domain.port.VoicePlayer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** 복호화한 바이트를 메모리에서 바로 재생한다. 소리를 평문 파일로 쓰지 않는다. */
class MediaVoicePlayer : VoicePlayer {

    @Volatile
    private var player: MediaPlayer? = null

    override suspend fun play(bytes: ByteArray): Boolean = suspendCancellableCoroutine { cont ->
        val media = MediaPlayer()
        val finish = { ok: Boolean ->
            release(media)
            if (cont.isActive) cont.resume(ok)
        }
        cont.invokeOnCancellation { release(media) }
        player = media
        runCatching {
            media.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
            )
            media.setDataSource(MemorySource(bytes))
            media.setOnPreparedListener { it.start() }
            media.setOnCompletionListener { finish(true) }
            media.setOnErrorListener { _, what, _ ->
                Log.w(TAG, "재생 오류 $what")
                finish(false)
                true
            }
            media.prepareAsync()
        }.onFailure {
            Log.w(TAG, "재생 실패: ${it.javaClass.simpleName}")
            finish(false)
        }
    }

    override fun stop() {
        player?.let(::release)
    }

    private fun release(media: MediaPlayer) {
        if (player === media) player = null
        media.release()
    }

    private class MemorySource(private val bytes: ByteArray) : MediaDataSource() {
        override fun getSize() = bytes.size.toLong()

        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (position >= bytes.size) return END
            val count = minOf(size.toLong(), bytes.size - position).toInt()
            System.arraycopy(bytes, position.toInt(), buffer, offset, count)
            return count
        }

        override fun close() = Unit
    }

    private companion object {
        const val TAG = "ChagokPlay"
        const val END = -1
    }
}
