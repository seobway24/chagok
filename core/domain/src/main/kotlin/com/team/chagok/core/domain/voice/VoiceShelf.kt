package com.team.chagok.core.domain.voice

import com.team.chagok.core.model.SignalCategory
import com.team.chagok.core.model.VoiceClip
import com.team.chagok.core.model.VoiceShelfState

/** 다음 회에 틀 목소리 고르기: 같은 상황 → 아직 안 튼 첫 회 → 가장 최근. 끈 사람은 뺀다. */
object VoiceShelf {

    fun pick(shelf: VoiceShelfState, now: SignalCategory?): VoiceClip? {
        val clips = shelf.clips.filterNot { it.guestId in shelf.muted }
        if (clips.isEmpty()) return null
        val sameMoment = now?.let { signal -> clips.filter { it.signal == signal }.minByOrNull { it.playCount } }
        val first = clips.minBy { it.episode }.takeIf { it.playCount == 0 }
        return sameMoment ?: first ?: clips.maxBy { it.recordedAtMillis }
    }

    /** 저장을 멈출지 판단한다. 자동 삭제는 하지 않으므로 넘치면 사람이 고를 때까지 새 녹음만 멈춘다. */
    fun hasRoom(count: Int, freeBytes: Long): Boolean = count < MAX_CLIPS && freeBytes >= MIN_FREE_BYTES

    const val MAX_CLIPS = 5_000
    const val MIN_FREE_BYTES = 50L * 1024 * 1024
}
