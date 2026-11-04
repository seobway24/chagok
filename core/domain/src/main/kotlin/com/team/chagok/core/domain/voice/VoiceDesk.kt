package com.team.chagok.core.domain.voice

import com.team.chagok.core.domain.port.VoicePlayer
import com.team.chagok.core.domain.port.VoiceRecorder
import com.team.chagok.core.domain.port.VoiceVault
import com.team.chagok.core.domain.show.ShowInput
import com.team.chagok.core.domain.show.ShowState
import com.team.chagok.core.model.Guest
import com.team.chagok.core.model.SignalCategory
import com.team.chagok.core.model.VoiceClip
import kotlin.time.Duration.Companion.seconds

/** 녹음·보관함·재생을 묶는다. 쓰는 순서: 공간 확인 → 녹음 → 암호화 저장. 저장이 끝나야 결과를 낸다. */
class VoiceDesk(
    private val vault: VoiceVault,
    private val recorder: VoiceRecorder,
    private val player: VoicePlayer,
    private val consented: suspend () -> Boolean,
    private val newId: () -> String,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    suspend fun canKeep(): Boolean = consented()

    /** 동의가 없으면 재생도 제안하지 않는다. */
    suspend fun offer(now: SignalCategory?): VoiceClip? =
        if (consented()) vault.shelf().takeUnless { it.unreadable }?.let { VoiceShelf.pick(it, now) } else null

    suspend fun keep(onAir: ShowState.OnAir, guest: Guest, signal: SignalCategory?): ShowInput.KeepResult {
        val shelf = vault.shelf()
        if (shelf.unreadable || !VoiceShelf.hasRoom(shelf.clips.size, vault.freeBytes())) return ShowInput.KeepResult(null, full = true)
        val voice = recorder.record(MAX_LENGTH) ?: return ShowInput.KeepResult(null)
        val clip = VoiceClip(newId(), onAir.episode, guest.id, guest.nickname, clock(), voice.durationMillis, signal)
        return ShowInput.KeepResult(clip.takeIf { vault.add(it, voice.bytes) })
    }

    fun cancelRecording() = recorder.cancel()

    suspend fun play(clip: VoiceClip) {
        val audio = vault.read(clip.id) ?: return
        if (player.play(audio)) vault.markPlayed(clip.id)
    }

    fun stopPlaying() = player.stop()

    suspend fun delete(id: String) = vault.delete(id)

    private companion object {
        val MAX_LENGTH = 8.seconds
    }
}
