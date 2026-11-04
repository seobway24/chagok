package com.team.chagok.core.domain.port

import com.team.chagok.core.model.Corner
import com.team.chagok.core.model.EpisodeRecord
import com.team.chagok.core.model.Guest
import com.team.chagok.core.model.GuestId
import com.team.chagok.core.model.SignalEvent
import com.team.chagok.core.model.VoiceClip
import com.team.chagok.core.model.VoiceShelfState
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

/*
 * 도메인이 바깥 세계에 요구하는 것. 구현은 platform·data 모듈이 한다.
 * 도메인은 Pleos SDK·안드로이드를 모른다 → JVM 테스트에서 가짜로 바꿔 끼운다.
 */

/** 차량 신호. 구현: platform:vehicle (실차·에뮬레이터), DebugSignalSource (시연 버튼). */
interface SignalSource {
    val events: Flow<SignalEvent>
}

/** 진행자 목소리. 구현: platform:voice (Pleos TTS). */
interface HostVoice {
    /** 다 읽을 때까지 기다린다. 실패해도 예외 대신 false. */
    suspend fun speak(text: String, speaker: String, zone: AudioZone = AudioZone.ALL): Boolean

    /** SafetyPause 진입 시 즉시 끊는다. */
    fun stop()
}

enum class AudioZone { ALL, REAR }

/** 호명 뒤 짧은 답 듣기. 구현: platform:voice (Pleos STT, ON_DEVICE 고정). */
interface MicInput {
    suspend fun listen(window: Duration): Heard
}

sealed interface Heard {
    data class Text(val text: String) : Heard
    data object Silence : Heard
    data object Failed : Heard
}

/**
 * 회차가 쌓일수록 자라는 가족 기억. 구현: domain.memory.FamilyMemoryBook.
 * 회차가 끝나면 대답을 기억하고, 다음 회차 시작 때 이어 물을 코너를 내준다.
 */
interface FamilyMemory {
    suspend fun callbacksFor(roster: List<Guest>, episode: Int): List<Corner>
    suspend fun remember(record: EpisodeRecord, roster: List<Guest>)
}

/** 출연자 명부·회차. 구현: data:studio (차 안 암호화 저장). */
interface StudioRepository {
    suspend fun roster(): List<Guest>
    suspend fun lastEpisodeNumber(): Int
    suspend fun saveEpisode(record: EpisodeRecord)
}

/** 사람이 버튼을 눌렀을 때만 쓰는 녹음. 구현: platform:voice (임시 파일 하나, 저장 뒤 지움). */
interface VoiceRecorder {
    /** [max] 까지 녹음한다. 끝나면 소리 바이트, 취소·실패면 null. */
    suspend fun record(max: Duration): RecordedVoice?
    fun cancel()
}

class RecordedVoice(val bytes: ByteArray, val durationMillis: Long)

/** 복호화한 소리를 메모리에서 재생한다. 평문 파일을 만들지 않는다. */
interface VoicePlayer {
    suspend fun play(bytes: ByteArray): Boolean
    fun stop()
}

/** 앱 안 암호화 보관함. 구현: data:studio. 목록을 읽지 못하면 쓰기를 잠근다. */
interface VoiceVault {
    suspend fun shelf(): VoiceShelfState
    /** 저장하면 true. 쓰기가 잠겼거나 실패하면 false. */
    suspend fun add(clip: VoiceClip, audio: ByteArray): Boolean
    suspend fun read(id: String): ByteArray?
    suspend fun markPlayed(id: String)
    suspend fun delete(id: String)
    suspend fun deleteAll()
    suspend fun setMuted(guest: GuestId, muted: Boolean)
    suspend fun freeBytes(): Long
}
