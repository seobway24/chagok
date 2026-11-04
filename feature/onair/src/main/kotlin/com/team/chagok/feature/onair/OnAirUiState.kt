package com.team.chagok.feature.onair

import com.team.chagok.core.domain.show.Caption
import com.team.chagok.core.domain.show.KeepStage
import com.team.chagok.core.domain.show.ShowState
import com.team.chagok.core.model.SignalCategory
import com.team.chagok.core.model.Guest
import com.team.chagok.core.model.Seat

/**
 * 화면이 그리는 값만. 위 = 진행자 한 줄(자막), 아래 = 탄 사람 자리 카드.
 * 주행 중에는 카드 안의 기억 줄을 숨긴다(1~2초 안에 읽힘).
 */
data class OnAirUiState(
    val phase: Phase = Phase.IDLE,
    val episode: Int? = null,
    val headline: String = "오늘 방송을 시작할까요?",
    val driving: Boolean = false,
    val seats: List<SeatUi> = emptyList(),
    /** 지금 오가는 말. 없으면 [headline] 을 보여 준다. */
    val caption: String? = null,
    /** 자막을 말한 사람. 진행자면 null. */
    val captionBy: String? = null,
    val corner: String? = null,
    /** 정차·대기 중에만 보인다. 사람 카드 저장소를 아직 못 읽었으면 null. */
    val profile: FamilyProfileUi? = null,
    val ai: AiStatusUi = AiStatusUi(),
    val offer: OfferUi? = null,
    val keep: KeepUi? = null,
    /** 내비 목적지 이름. 안내 중일 때만. 제목 옆에 "○○ 가는 길"로 붙는다. */
    val destination: String? = null,
) {
    /** 앱 바 제목. 예: "제2회 · 할머니댁 가는 길". */
    val episodeTitle: String
        get() = episode?.let { n -> "제${n}회" + destination?.let { " · $it 가는 길" }.orEmpty() }.orEmpty()

    enum class Phase { IDLE, OPENING, ON_AIR, PAUSED, ENDING, OFFER, KEEP }

    val hostSpeaking: Boolean get() = caption != null && captionBy == null

    /** 주행 중 보이는 한 줄. 말하는 사람이 있으면 이름만 쓴다. */
    val driveLine: String get() = captionBy?.takeIf { it.isNotBlank() }?.let { "$it 듣는 중" } ?: corner ?: headline
}

/** AI 진행자 상태. 키 값은 화면으로 오지 않는다. [on] = 지금 외부 LLM 을 쓸 수 있다. */
data class AiStatusUi(
    val on: Boolean = false,
    val provider: String = "",
    val model: String = "",
    val baseUrl: String = "",
    val hasSavedKey: Boolean = false,
)

/** 자리 하나. [tone] 은 탑승 순서 번호라 처음 탄 친구도 색이 생긴다. */
data class SeatUi(
    val seat: Seat,
    val name: String,
    val status: SeatStatus,
    val tone: Int,
    val lines: List<String> = emptyList(),
)

enum class SeatStatus { IN, SPEAKING, LISTENING, WAITING }

/**
 * 사람 카드 전부(기억이 아직 없는 사람 포함) + 휴대폰으로 가져갈 QR 글.
 * [qrText] 는 쌓인 기억이 하나라도 있을 때만.
 */
data class FamilyProfileUi(
    val people: List<PersonCard>,
    val qrText: String?,
    val lastEpisode: Int = 0,
    val recap: EpisodeRecapUi? = null,
    /** 지난 방송 전부. 오래된 회부터. */
    val recaps: List<EpisodeRecapUi> = emptyList(),
)

/** 가장 최근 회차 정리. [highlights] = 말한 사람 to 원문. */
data class EpisodeRecapUi(
    val number: Int,
    val oneLine: String,
    val highlights: List<Pair<String, String>>,
    val insights: List<String>,
)

/** 사람 카드 한 장. [id] 가 비면 아직 저장 전인 새 사람. 친구도 같은 카드다. */
data class PersonCard(
    val id: String,
    val nickname: String,
    val seat: Seat,
    val isChild: Boolean = false,
    val lines: List<String> = emptyList(),
)

internal const val PARK_TO_START = "차를 세우면 방송을 시작할 수 있어요"

internal fun ShowState.toUiState(caption: Caption? = null, profile: FamilyProfileUi? = null): OnAirUiState {
    val base = when (this) {
        is ShowState.Idle -> {
            val idle = OnAirUiState(profile = profile, driving = driving)
            return if (driving) idle.copy(headline = PARK_TO_START) else idle
        }
        is ShowState.Opening -> OnAirUiState(
            phase = OnAirUiState.Phase.OPENING,
            episode = episode,
            headline = "제${episode}회를 시작해요",
            corner = "오프닝",
            driving = driving,
            seats = roster.toSeats(speaking = null, driving = false, opening = true),
        )
        is ShowState.OnAir -> OnAirUiState(
            phase = OnAirUiState.Phase.ON_AIR,
            episode = episode,
            headline = live?.let { "${it.guest.nickname} 차례예요" } ?: "다음 차례를 기다리고 있어요",
            corner = live?.corner?.title,
            driving = driving,
            seats = roster.toSeats(speaking = live?.guest, driving = driving),
        )
        is ShowState.SafetyPaused -> OnAirUiState(
            phase = OnAirUiState.Phase.PAUSED,
            episode = resumeTo.episode,
            headline = beltLine() ?: "잠깐 쉴게요",
            corner = "잠시 쉬는 중",
            driving = resumeTo.driving,
            seats = resumeTo.roster.toSeats(speaking = null, driving = resumeTo.driving),
        )
        is ShowState.ReplayOffer -> OnAirUiState(
            phase = OnAirUiState.Phase.OFFER,
            episode = opening.episode,
            driving = opening.driving,
            offer = OfferUi(clip.toUi(), clip.signal == SignalCategory.RAIN, playing),
        )
        is ShowState.Keepsake -> OnAirUiState(
            phase = OnAirUiState.Phase.KEEP,
            episode = onAir.episode,
            driving = onAir.driving,
            keep = KeepUi(
                people = onAir.roster.map { it.id.value to it.nickname },
                recordingBy = (stage as? KeepStage.Recording)?.guest?.nickname,
                saved = saved?.toUi().takeIf { stage == KeepStage.Saved },
                full = stage == KeepStage.Full,
                failed = failed,
            ),
        )
        is ShowState.Ending -> OnAirUiState(
            phase = OnAirUiState.Phase.ENDING,
            episode = onAir.episode,
            headline = "제${onAir.episode}회를 마무리해요",
            corner = "엔딩",
            driving = onAir.driving,
            seats = onAir.roster.toSeats(speaking = null, driving = onAir.driving),
        )
    }
    val destination = when (this) {
        is ShowState.Opening -> destination
        is ShowState.OnAir -> destination
        is ShowState.SafetyPaused -> resumeTo.destination
        is ShowState.Ending -> onAir.destination
        else -> null
    }
    val speaker = (this as? ShowState.OnAir)?.live?.guest?.nickname
    val withLines = if (base.driving || profile == null) base else base.copy(seats = base.seats.withLines(profile))
    return withLines.copy(
        caption = caption?.text,
        captionBy = caption?.takeUnless { it.fromHost }?.let { speaker.orEmpty() },
        profile = profile,
        destination = destination,
    )
}

private fun ShowState.SafetyPaused.beltLine(): String? = beltOff.takeIf { it.isNotEmpty() }?.joinToString { seat ->
    resumeTo.roster.firstOrNull { it.seat == seat }?.nickname ?: seat.label
}?.let { "$it, 안전벨트를 매 주세요" }

private fun List<Guest>.toSeats(speaking: Guest?, driving: Boolean, opening: Boolean = false): List<SeatUi> =
    mapIndexed { i, g ->
        val status = when {
            g.id == speaking?.id -> SeatStatus.SPEAKING
            driving && g.seat == Seat.DRIVER -> SeatStatus.WAITING
            opening -> SeatStatus.IN
            else -> SeatStatus.LISTENING
        }
        SeatUi(seat = g.seat, name = g.nickname, status = status, tone = i)
    }

private fun List<SeatUi>.withLines(profile: FamilyProfileUi): List<SeatUi> = map { s ->
    s.copy(lines = profile.people.firstOrNull { it.nickname == s.name }?.lines.orEmpty())
}
