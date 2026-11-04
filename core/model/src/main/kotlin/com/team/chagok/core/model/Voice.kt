package com.team.chagok.core.model

/** 가족이 남긴 8초 한마디 하나. 소리는 앱 안에만 있고 여기에는 목록 정보만 있다. */
data class VoiceClip(
    val id: String,
    val episode: Int,
    val guestId: GuestId,
    val guestName: String,
    val recordedAtMillis: Long,
    val durationMillis: Long,
    /** 녹음한 회차의 날씨·상황. 같은 상황이 오면 이 목소리를 먼저 고른다. */
    val signal: SignalCategory? = null,
    val playCount: Int = 0,
)

/** 목소리 목록을 읽은 결과. 읽지 못했으면 빈 목록이 아니라 [unreadable] 이다. */
data class VoiceShelfState(
    val clips: List<VoiceClip> = emptyList(),
    /** 재생을 끈 사람. 목소리는 남고 제안에서만 빠진다. */
    val muted: Set<GuestId> = emptySet(),
    val unreadable: Boolean = false,
)
