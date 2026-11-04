package com.team.chagok.core.domain.show

import com.team.chagok.core.model.Answer
import com.team.chagok.core.model.Corner
import com.team.chagok.core.model.Guest
import com.team.chagok.core.model.GuestId
import com.team.chagok.core.model.Seat
import com.team.chagok.core.model.SignalCategory
import com.team.chagok.core.model.SignalEvent
import com.team.chagok.core.model.VoiceClip
import kotlin.time.Duration

/** 회차 상태. UI 는 이것만 구독한다 (architecture §4.1). */
sealed interface ShowState {

    /**
     * 방송 전에도 차량 값을 들고 있는다. 시작할 때 [Opening] 으로 넘긴다.
     * [driving] 은 플랫폼 값이 오기 전까지 안전한 쪽(주행 중)으로 둔다.
     */
    data class Idle(
        val driving: Boolean = true,
        val braking: Boolean = false,
        val turnSignal: Boolean = false,
        val pending: List<Cue> = emptyList(),
        /** 내비 목적지 이름. 안내 중이 아니면 null. */
        val destination: String? = null,
        /** 목적지까지 남은 시간. 모르면 null. */
        val remaining: Duration? = null,
    ) : ShowState

    /** 오프닝 중에 들어온 주행·브레이크 값도 들고 있다가 [OnAir] 로 넘긴다. */
    data class Opening(
        val episode: Int,
        val roster: List<Guest>,
        val startedAtMillis: Long,
        val driving: Boolean = true,
        val braking: Boolean = false,
        val callbacks: List<Corner> = emptyList(),
        val pending: List<Cue> = emptyList(),
        val canKeep: Boolean = false,
        val destination: String? = null,
        val remaining: Duration? = null,
    ) : ShowState {
        fun toOnAir() = OnAir(
            episode, roster, driving, braking,
            startedAtMillis = startedAtMillis, callbacks = callbacks, pending = pending, canKeep = canKeep,
            destination = destination, remaining = remaining,
        )
    }

    data class OnAir(
        val episode: Int,
        val roster: List<Guest>,
        val driving: Boolean = true,
        val braking: Boolean = false,
        val live: LiveCorner? = null,
        val playedCornerIds: List<String> = emptyList(),
        /** 코너를 마친 출연자를 순서대로. 같은 사람이 여러 번 나올 수 있다. */
        val turns: List<GuestId> = emptyList(),
        val startedAtMillis: Long,
        /** 지난 회 대답으로 만든 이번 회 전용 코너. 형식 팩 코너보다 먼저 연다. */
        val callbacks: List<Corner> = emptyList(),
        val answers: List<Answer> = emptyList(),
        /** 코너를 못 여는 동안 온 신호. 온 순서대로 연다. */
        val pending: List<Cue> = emptyList(),
        /** 보호자가 동의했다. 없으면 엔딩 뒤 남기기 화면을 열지 않는다. */
        val canKeep: Boolean = false,
        /** 브레이크에서 막 발을 뗐다. 진정 시간이 지날 때까지 새 코너를 열지 않고 기다리게 둔다. */
        val calming: Boolean = false,
        /** 내비 목적지 이름. 화면 제목에만 쓴다. 회차 기록에 저장하지 않는다. */
        val destination: String? = null,
        /** 목적지까지 남은 시간. 채움 코너 간격을 길이에 맞춘다. 모르면 기본 간격. */
        val remaining: Duration? = null,
        /** 내비가 도착을 알렸다. 마무리 중이라 새 코너를 열지 않고, 다음 회로 목적지를 넘기지 않는다. */
        val arrived: Boolean = false,
    ) : ShowState {
        val appeared: Set<GuestId> get() = turns.toSet()
    }

    /**
     * 모든 상태보다 우선한다. 깜빡이가 켜져 있거나([heldBySignal]) 벨트를 안 맨 자리가 있으면([beltOff]) 재개 타이머를 걸지 않는다.
     */
    data class SafetyPaused(
        val resumeTo: OnAir,
        val heldBySignal: Boolean = true,
        val beltOff: List<Seat> = emptyList(),
    ) : ShowState

    data class Ending(val onAir: OnAir, val turnSignal: Boolean = false) : ShowState

    /** 방송 전 지난 목소리 제안. 기어 P 에서만 존재한다. 자동으로 틀지 않는다. */
    data class ReplayOffer(
        val opening: Opening,
        val clip: VoiceClip,
        val playing: Boolean = false,
        /** 제안 중에 벨트를 푼 좌석. 제안을 접고 오프닝으로 갈 때 이 값이 있으면 멈춘 채로 간다. */
        val beltOff: List<Seat> = emptyList(),
    ) : ShowState

    /** 방송 뒤 한마디 남기기. 기어 P 에서만 존재한다. 사람이 버튼을 눌러야 녹음한다. */
    data class Keepsake(
        val onAir: OnAir,
        val stage: KeepStage = KeepStage.Choose,
        val saved: VoiceClip? = null,
        val failed: Boolean = false,
    ) : ShowState
}

sealed interface KeepStage {
    data object Choose : KeepStage
    data class Recording(val guest: Guest) : KeepStage
    data object Saved : KeepStage
    data object Full : KeepStage
}

data class LiveCorner(val corner: Corner, val guest: Guest)

/** 코너를 열어 달라는 요청. [seat] 는 신호가 좌석을 가리킬 때(도어)만. */
data class Cue(val category: SignalCategory, val seat: Seat? = null)

/** 상태 머신에 들어오는 입력. 신호와, 부수 효과가 끝났다는 알림. */
sealed interface ShowInput {
    data class Start(
        val episode: Int,
        val roster: List<Guest>,
        val atMillis: Long,
        val callbacks: List<Corner> = emptyList(),
        val offer: VoiceClip? = null,
        val canKeep: Boolean = false,
        /** 시작 요청을 보내는 순간 벨트를 안 맨 좌석. 대기 중엔 벨트 변화를 안 받으므로 여기서 넘긴다. */
        val beltOff: List<Seat> = emptyList(),
    ) : ShowInput
    data class Signal(val event: SignalEvent) : ShowInput
    data object OpeningDone : ShowInput
    /** 코너에서 대답을 들었다. [CornerDone] 보다 먼저 온다. */
    data class Answered(val answer: Answer) : ShowInput
    data object CornerDone : ShowInput
    data object ResumeDue : ShowInput
    /** 앉아 있는데 벨트를 안 맨 자리 전부. 바뀔 때마다 온다. */
    data class BeltsChanged(val unbuckled: List<Seat>) : ShowInput
    /** 코너 없이 한동안 조용했다 → 채움 코너(QUIET)를 연다. */
    data object QuietDue : ShowInput
    data class EndingDone(val atMillis: Long) : ShowInput

    data object PlayOffered : ShowInput
    data object SkipOffer : ShowInput
    data object VoicePlayed : ShowInput

    data class KeepBy(val guest: Guest) : ShowInput
    data object KeepCancel : ShowInput
    data class KeepResult(val clip: VoiceClip?, val full: Boolean = false) : ShowInput
    /** 저장한 목소리를 바로 들어 본다. */
    data object KeepListen : ShowInput
    data object KeepDelete : ShowInput
    data object KeepClose : ShowInput
}

/** 상태 머신이 요청하는 부수 효과. 실행은 [ShowEngine] 이 한다. */
sealed interface ShowEffect {
    data class PlayOpening(val episode: Int, val roster: List<Guest>, val destination: String? = null) : ShowEffect
    data class RunCorner(val live: LiveCorner) : ShowEffect
    /** 지금 소리 내고 있는 장면(오프닝 또는 코너)을 끊는다. */
    data object InterruptCorner : ShowEffect
    data class ScheduleResume(val after: Duration) : ShowEffect
    data object CancelResume : ShowEffect
    /** 벨트를 매라고 이름을 불러 안내한다. */
    data class SayBelt(val names: List<String>) : ShowEffect
    /** [after] 동안 아무 코너도 안 열리면 [ShowInput.QuietDue] 를 넣는다. 다시 걸면 앞의 것은 취소된다. */
    data class ScheduleQuiet(val after: Duration) : ShowEffect
    data object CancelQuiet : ShowEffect
    data class PlayEnding(val onAir: ShowState.OnAir) : ShowEffect
    /** 엔딩 대사를 끊고 바로 [ShowInput.EndingDone] 을 넣는다. */
    data object CutEnding : ShowEffect
    data class SaveEpisode(val onAir: ShowState.OnAir, val endedAtMillis: Long) : ShowEffect
    data class PlayVoice(val clip: VoiceClip) : ShowEffect
    data object StopVoice : ShowEffect
    data class RecordVoice(val onAir: ShowState.OnAir, val guest: Guest) : ShowEffect
    data object CancelRecording : ShowEffect
    data class DeleteVoice(val id: String) : ShowEffect
}

data class Transition(val state: ShowState, val effects: List<ShowEffect> = emptyList())
