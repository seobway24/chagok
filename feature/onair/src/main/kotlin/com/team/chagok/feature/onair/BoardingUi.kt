package com.team.chagok.feature.onair

import androidx.compose.runtime.Immutable
import com.team.chagok.core.domain.show.Cabin
import com.team.chagok.core.model.Seat

/** 탑승 확인 화면의 단계. */
enum class BoardMode {
    /** 타는 중. 앉은 자리가 칸으로 나타난다. */
    WATCH,

    /** 벨트를 안 맨 사람의 이름을 부른다. 방송을 시작하지 않는다. */
    BELT,

    /** 벨트를 맸는데 카드가 없는 자리에 이름을 묻는다. */
    GUEST,

    /** 모두 맸다. 잠시 뒤 방송을 시작한다. */
    READY,

    /** 차가 자리 값을 주지 않아 사람을 직접 고른다. */
    MANUAL,
}

enum class BoardSeatState { EMPTY, BUCKLED, UNBUCKLED, UNKNOWN }

@Immutable
data class BoardSeat(val seat: Seat, val name: String?, val state: BoardSeatState, val tone: Int)

@Immutable
data class ManualPick(val id: String, val name: String, val picked: Boolean)

/** 화면이 그리는 값만. */
@Immutable
data class BoardingUi(
    val mode: BoardMode,
    val seats: List<BoardSeat>,
    val riders: Int,
    /** 벨트를 안 맨 사람. 카드가 없으면 자리 이름. */
    val unbuckled: List<String>,
    val askSeat: Seat? = null,
    val heard: String? = null,
    val listening: Boolean = false,
    val driving: Boolean = false,
    val episode: Int = 1,
    val manual: List<ManualPick> = emptyList(),
)

/** 탑승 확인 중에 화면이 쌓는 값. */
internal data class BoardDraft(
    val open: Boolean = false,
    val warned: Boolean = false,
    val guests: Map<Seat, String> = emptyMap(),
    val heard: String? = null,
    val listening: Boolean = false,
    /** 다시 말하기를 누를 때마다 는다. */
    val askTurn: Int = 0,
    /** 직접 고를 때 뺀 사람. */
    val unpicked: Set<String> = emptySet(),
)

internal fun boardingOf(
    cabin: Cabin,
    people: List<PersonCard>,
    draft: BoardDraft,
    driving: Boolean,
    episode: Int,
): BoardingUi {
    val nameAt = { seat: Seat -> draft.guests[seat] ?: people.firstOrNull { it.seat == seat }?.nickname }
    val toneAt = { seat: Seat -> people.indexOfFirst { it.seat == seat }.takeIf { it >= 0 } ?: (people.size + seat.ordinal) }
    if (cabin.sensed != true) {
        val manual = people.map { ManualPick(it.id, it.nickname, it.id !in draft.unpicked) }
        val seats = Seat.entries.map { seat ->
            val person = people.firstOrNull { it.seat == seat && it.id !in draft.unpicked }
            BoardSeat(seat, person?.nickname, if (person != null) BoardSeatState.BUCKLED else BoardSeatState.EMPTY, toneAt(seat))
        }
        return BoardingUi(BoardMode.MANUAL, seats, manual.count { it.picked }, emptyList(), driving = driving, episode = episode, manual = manual)
    }
    val seats = Seat.entries.map { seat ->
        val signal = cabin.seats[seat]
        val state = when {
            signal?.present != true -> BoardSeatState.EMPTY
            signal.buckled && nameAt(seat) == null -> BoardSeatState.UNKNOWN
            signal.buckled -> BoardSeatState.BUCKLED
            else -> BoardSeatState.UNBUCKLED
        }
        BoardSeat(seat, nameAt(seat).takeIf { signal?.present == true }, state, toneAt(seat))
    }
    val off = cabin.unbuckled
    val asking = seats.firstOrNull { it.state == BoardSeatState.UNKNOWN }?.seat
    val mode = when {
        off.isNotEmpty() && (draft.warned || driving) -> BoardMode.BELT
        asking != null -> BoardMode.GUEST
        off.isNotEmpty() || cabin.present.isEmpty() -> BoardMode.WATCH
        else -> BoardMode.READY
    }
    return BoardingUi(
        mode, seats, cabin.present.size, off.map { nameAt(it) ?: it.label },
        askSeat = asking.takeIf { mode == BoardMode.GUEST },
        heard = draft.heard.takeIf { mode == BoardMode.GUEST },
        listening = draft.listening && mode == BoardMode.GUEST,
        driving = driving, episode = episode,
    )
}

/** "저는 지우예요" → "지우". 말을 글로 바꾼 결과에서 이름만 남긴다. */
internal fun nameFromSpeech(text: String): String? = text.trim().trimEnd('.', '!', '?', ',')
    .replace(NAME_LEAD, "")
    .replace(NAME_TAIL, "")
    .trim()
    .takeIf { it.isNotEmpty() && it.length <= MAX_NAME }

private val NAME_LEAD = Regex("^(안녕하세요[, ]*)?(저는|전|제 이름은|이름은|나는|난)\\s*")
private val NAME_TAIL = Regex("(이에요|예요|입니다|이요|요)$")
private const val MAX_NAME = 8
