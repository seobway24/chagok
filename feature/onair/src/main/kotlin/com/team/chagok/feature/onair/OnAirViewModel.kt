package com.team.chagok.feature.onair

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.team.chagok.core.domain.show.Caption
import com.team.chagok.core.domain.show.Cabin
import com.team.chagok.core.domain.show.KeepStage
import com.team.chagok.core.domain.show.PLAIN_GUEST_NAME
import com.team.chagok.core.domain.show.plainGuest
import com.team.chagok.core.domain.show.ShowEngine
import com.team.chagok.core.domain.show.ShowInput
import com.team.chagok.core.domain.show.ShowState
import com.team.chagok.core.domain.signal.ManualSignalSource
import com.team.chagok.core.model.AiSettings
import com.team.chagok.core.model.Guest
import com.team.chagok.core.model.Seat
import com.team.chagok.core.model.SignalEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * TODO(onair-service): 화면을 벗어나도 방송이 이어지도록 엔진을 포그라운드 서비스로 옮긴다.
 *  지금은 뷰모델 수명 = 방송 수명.
 */
class OnAirViewModel(
    private val engine: ShowEngine,
    private val manualSignals: ManualSignalSource,
    profile: Flow<FamilyProfileUi?> = flowOf(null),
    private val forgetFamily: suspend () -> Unit = {},
    captions: Flow<Caption?> = flowOf(null),
    private val savePerson: suspend (PersonCard) -> Unit = {},
    private val removePerson: suspend (String) -> Unit = {},
    aiStatus: Flow<AiStatusUi> = flowOf(AiStatusUi()),
    private val testAi: suspend (AiSettings) -> Boolean = { false },
    private val saveAi: suspend (AiSettings) -> Unit = {},
    private val clearAi: suspend () -> Unit = {},
    val library: VoiceLibrary = NoVoices,
    private val clearCaption: () -> Unit = {},
    /** 시연 빌드는 대본 대답을 쓰므로 이름을 말로 받지 않는다. */
    private val asksNameByVoice: Boolean = true,
) : ViewModel() {

    init {
        engine.launchIn(viewModelScope)
        // 녹음이 끝나면 목소리 목록을 다시 읽는다
        viewModelScope.launch {
            engine.state.map { it is ShowState.Idle || (it as? ShowState.Keepsake)?.stage == KeepStage.Saved }
                .distinctUntilChanged().collect { library.refresh() }
        }
        // 방송이 끝나면 지난 회 자막을 다음 회로 넘기지 않는다
        viewModelScope.launch {
            engine.state.map { it is ShowState.Idle }.distinctUntilChanged().collect { idle -> if (idle) clearCaption() }
        }
    }

    val voice: StateFlow<VoiceUi> = library.state.stateIn(viewModelScope, SharingStarted.Eagerly, VoiceUi())

    val voiceActions = VoiceActions(
        playOffer = { engine.send(ShowInput.PlayOffered) },
        skipOffer = { engine.send(ShowInput.SkipOffer) },
        keepBy = { id ->
            (engine.state.value as? ShowState.Keepsake)?.onAir?.roster?.firstOrNull { it.id.value == id }
                ?.let { engine.send(ShowInput.KeepBy(it)) }
        },
        keepCancel = { engine.send(ShowInput.KeepCancel) },
        keepListen = { engine.send(ShowInput.KeepListen) },
        keepDelete = { engine.send(ShowInput.KeepDelete) },
        keepClose = { engine.send(ShowInput.KeepClose) },
    )

    fun library(action: suspend VoiceLibrary.() -> Unit) {
        viewModelScope.launch { library.action() }
    }

    private val family: StateFlow<FamilyProfileUi?> = profile.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val uiState: StateFlow<OnAirUiState> = combine(engine.state, family, captions, aiStatus) { state, people, caption, ai ->
        state.toUiState(caption = caption, profile = people).copy(ai = ai)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), OnAirUiState())

    // ---- 탑승 확인: 벨트를 모두 매야 방송을 시작한다 ----

    private val draft = MutableStateFlow(BoardDraft())

    /** 닫혀 있으면 null. */
    val boarding: StateFlow<BoardingUi?> = combine(draft, engine.cabin, family, engine.state) { d, cabin, people, state ->
        val driving = (state as? ShowState.Idle)?.driving ?: false
        val episode = (people?.lastEpisode ?: 0) + 1
        boardingOf(cabin, people?.people.orEmpty(), d, driving, episode).takeIf { d.open }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val boardActions = BoardActions(
        go = ::beginShow,
        later = { draft.update { BoardDraft(guests = it.guests, unpicked = it.unpicked) } },
        confirmName = ::confirmHeardName,
        askAgain = { draft.update { it.copy(heard = null, askTurn = it.askTurn + 1) } },
        plainGuest = ::keepAsGuest,
        togglePick = { id -> draft.update { d -> d.copy(unpicked = if (id in d.unpicked) d.unpicked - id else d.unpicked + id) } },
        savePerson = ::onSavePerson,
    )

    init {
        viewModelScope.launch { openOnFirstPark() }
        viewModelScope.launch { followSeats() }
        viewModelScope.launch { watchBoarding() }
    }

    /** 앱을 켜고 처음 차를 세운 상태면 바로 자리부터 본다. 카드를 처음 만든 뒤에도 같다. */
    private suspend fun openOnFirstPark() {
        combine(engine.state, family, voice) { state, people, v ->
            state is ShowState.Idle && !state.driving && !people?.people.isNullOrEmpty() && v.consent != ConsentUi.UNKNOWN
        }.first { it }
        draft.update { it.copy(open = true) }
    }

    /** 방송이 시작되면 닫고, 벨트를 안 맨 채 출발하면 어떤 화면이든 다시 띄운다. */
    private suspend fun followSeats() {
        combine(engine.state, engine.cabin) { state, cabin -> state to cabin.unbuckled }.collect { (state, off) ->
            val idle = state as? ShowState.Idle
            when {
                idle == null -> draft.update { if (it.open) BoardDraft() else it }
                idle.driving && off.isNotEmpty() -> draft.update { it.copy(open = true) }
                idle.driving -> draft.update { it.copy(open = false, warned = false) }
                off.isEmpty() -> draft.update { it.copy(warned = false) }
            }
        }
    }

    private data class BoardCue(val mode: BoardMode, val names: List<String>, val seat: Seat?, val turn: Int, val heard: Boolean)

    private suspend fun watchBoarding() {
        combine(boarding, draft) { b, d -> b?.let { BoardCue(it.mode, it.unbuckled, it.askSeat, d.askTurn, it.heard != null) } }
            .distinctUntilChanged()
            .collectLatest { cue ->
                when {
                    cue == null -> Unit
                    cue.mode == BoardMode.WATCH && cue.names.isNotEmpty() -> {
                        delay(BELT_GRACE_MS)
                        draft.update { it.copy(warned = true) }
                    }
                    // 출석 부르듯 한 번만. 계속 울리는 경고는 차가 이미 한다 — 앱이 겹쳐 말하지 않는다
                    cue.mode == BoardMode.BELT -> engine.remindBelt(cue.names)
                    cue.mode == BoardMode.GUEST && !cue.heard && cue.seat != null && asksNameByVoice -> listenForName(cue.seat)
                }
            }
    }

    private suspend fun listenForName(seat: Seat) {
        draft.update { it.copy(listening = true) }
        var name: String? = null
        try {
            name = engine.askName(seat)?.let(::nameFromSpeech)
        } finally {
            // 안내가 바뀌어 듣기가 취소돼도 "듣는 중" 표시가 남지 않게 한다
            draft.update { it.copy(listening = false, heard = name) }
        }
    }

    private fun askedSeat(): Seat? = boarding.value?.askSeat

    private fun confirmHeardName() {
        val seat = askedSeat() ?: return
        val name = draft.value.heard ?: return
        draft.update { it.copy(heard = null) }
        onSavePerson(PersonCard(id = "", nickname = name, seat = seat))
    }

    private fun keepAsGuest() {
        val seat = askedSeat() ?: return
        draft.update { it.copy(heard = null, guests = it.guests + (seat to PLAIN_GUEST_NAME)) }
    }

    private fun beginShow() {
        val cabin = engine.cabin.value
        val d = draft.value
        viewModelScope.launch {
            if (cabin.sensed == true) {
                engine.startEpisode(keep = { it.seat in cabin.present }, extras = extrasOf(d, cabin))
            } else {
                engine.startEpisode(keep = { it.id.value !in d.unpicked })
            }
        }
    }

    private fun extrasOf(d: BoardDraft, cabin: Cabin): List<Guest> = d.guests.filterKeys { it in cabin.present }
        .map { (seat, name) -> plainGuest(seat, name) }

    /** 대기 화면의 방송 시작. 먼저 자리를 본다. */
    fun onStartClick() {
        draft.update { it.copy(open = true) }
    }

    /** 보호자가 두 번 눌러 확인했다. 쌓인 가족 정보를 지운다(회차 번호는 남는다). */
    fun onForgetFamilyConfirmed() {
        viewModelScope.launch { forgetFamily() }
    }

    /** 정차 화면 사람 카드 편집: 저장(새 사람이면 id 가 비어 있다) · 삭제. */
    fun onSavePerson(card: PersonCard) {
        viewModelScope.launch { savePerson(card) }
    }

    fun onRemovePerson(id: String) {
        viewModelScope.launch { removePerson(id) }
    }

    suspend fun onTestAi(settings: AiSettings): Boolean = testAi(settings)

    fun onSaveAi(settings: AiSettings) {
        viewModelScope.launch { saveAi(settings) }
    }

    fun onClearAi() {
        viewModelScope.launch { clearAi() }
    }

    /** 시연·디버그 패널 전용. 실제 신호와 같은 길로 들어간다. */
    fun inject(event: SignalEvent) = manualSignals.emit(event)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val BELT_GRACE_MS = 5_000L
    }
}
