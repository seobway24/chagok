package com.team.chagok.core.domain.show

import com.team.chagok.core.domain.port.FamilyMemory
import com.team.chagok.core.domain.port.SignalSource
import com.team.chagok.core.domain.port.StudioRepository
import com.team.chagok.core.domain.voice.VoiceDesk
import com.team.chagok.core.model.EpisodeRecord
import com.team.chagok.core.model.Guest
import com.team.chagok.core.model.Seat
import com.team.chagok.core.model.SignalCategory
import com.team.chagok.core.model.SignalEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 단일 actor. 모든 입력을 한 줄로 세워 [ShowStateMachine] 에 넣고, 나온 효과만 실행한다.
 *
 * 상태를 바꾸는 곳이 inbox 처리 루프 하나뿐이라 신호·음성 콜백이 겹쳐도 경쟁 상태가 없다.
 */
class ShowEngine(
    private val machine: ShowStateMachine,
    private val director: ShowDirector,
    private val signals: SignalSource,
    private val studio: StudioRepository,
    private val memory: FamilyMemory? = null,
    private val voices: VoiceDesk? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow<ShowState>(ShowState.Idle())
    val state: StateFlow<ShowState> = _state.asStateFlow()

    private val _cabin = MutableStateFlow(Cabin())
    val cabin: StateFlow<Cabin> = _cabin.asStateFlow()

    private val inbox = Channel<ShowInput>(Channel.UNLIMITED)
    private var sceneJob: Job? = null
    private var resumeJob: Job? = null
    private var quietJob: Job? = null
    private var voiceJob: Job? = null
    private var beltJob: Job? = null

    /** [scope] 가 끝나면 엔진도 끝난다. 앱에서는 서비스/뷰모델 수명에 묶는다. */
    fun launchIn(scope: CoroutineScope) {
        scope.launch { signals.events.collect { route(it) } }
        scope.launch {
            for (input in inbox) {
                val transition = machine.reduce(_state.value, input)
                _state.value = transition.state
                transition.effects.forEach { scope.execute(it) }
            }
        }
    }

    private suspend fun route(event: SignalEvent) {
        if (event is SignalEvent.SeatSensors || event is SignalEvent.SeatChanged) {
            val before = _cabin.value.unbuckled
            _cabin.value = _cabin.value.on(event)
            if (_cabin.value.unbuckled != before) inbox.send(ShowInput.BeltsChanged(_cabin.value.unbuckled))
        } else {
            inbox.send(ShowInput.Signal(event))
        }
    }

    /**
     * 탑승 확인을 마친 명부로 다음 회차를 시작한다. 가족 기억이 있으면 지난 회 이어 묻기 코너를 얹는다.
     * 벨트를 안 맨 사람이 있으면 시작하지 않는다.
     */
    suspend fun startEpisode(keep: (Guest) -> Boolean = { true }, extras: List<Guest> = emptyList()) {
        if (_cabin.value.unbuckled.isNotEmpty()) return
        val roster = studio.roster().filter(keep) + extras
        val episode = studio.lastEpisodeNumber() + 1
        val callbacks = memory?.callbacksFor(roster, episode).orEmpty()
        val raining = (_state.value as? ShowState.Idle)?.pending.orEmpty().any { it.category == SignalCategory.RAIN }
        val offer = voices?.offer(if (raining) SignalCategory.RAIN else null)
        // 위의 읽기 동안 벨트가 풀렸을 수 있다. 대기 상태는 벨트 변화를 버리므로 보내는 순간 값을 같이 넘긴다
        val beltOff = _cabin.value.unbuckled
        inbox.send(ShowInput.Start(episode, roster, clock(), callbacks, offer, voices?.canKeep() == true, beltOff))
    }

    /** 탑승 확인 중 안내. 방송이 아니라 말만 한다. */
    suspend fun remindBelt(names: List<String>) = director.belt(names)

    suspend fun askName(seat: Seat): String? = director.askName(seat)

    /** 화면 버튼. 신호와 같은 줄에 세워 상태 머신이 판단한다. */
    fun send(input: ShowInput) {
        inbox.trySend(input)
    }

    private fun CoroutineScope.execute(effect: ShowEffect) {
        when (effect) {
            is ShowEffect.PlayOpening -> scene(done = { ShowInput.OpeningDone }) {
                director.opening(effect.episode, effect.roster, effect.destination)
            }
            is ShowEffect.RunCorner -> scene(done = { ShowInput.CornerDone }) {
                // 대답은 들은 즉시 넣는다(반응보다 먼저). 반응 중에 끊겨도 재개 때 같은 코너를 되풀이하지 않는다
                director.corner(effect.live, currentRoster(), currentEpisode()) { inbox.send(ShowInput.Answered(it)) }
            }
            is ShowEffect.PlayEnding -> scene(done = { ShowInput.EndingDone(clock()) }) {
                director.ending(effect.onAir)?.let { inbox.send(ShowInput.Answered(it)) }
            }
            ShowEffect.CutEnding -> {
                sceneJob?.cancel()
                director.interrupt()
                inbox.trySend(ShowInput.EndingDone(clock()))
            }
            ShowEffect.InterruptCorner -> {
                sceneJob?.cancel()
                director.interrupt()
            }
            is ShowEffect.ScheduleResume -> {
                resumeJob?.cancel()
                resumeJob = launch {
                    delay(effect.after)
                    inbox.send(ShowInput.ResumeDue)
                }
            }
            ShowEffect.CancelResume -> resumeJob?.cancel()
            is ShowEffect.SayBelt -> {
                beltJob?.cancel()
                beltJob = launch { director.belt(effect.names) }
            }
            is ShowEffect.ScheduleQuiet -> {
                quietJob?.cancel()
                quietJob = launch {
                    delay(effect.after)
                    inbox.send(ShowInput.QuietDue)
                }
            }
            ShowEffect.CancelQuiet -> quietJob?.cancel()
            is ShowEffect.PlayVoice -> {
                voiceJob?.cancel()
                voiceJob = launch {
                    voices?.play(effect.clip)
                    inbox.send(ShowInput.VoicePlayed)
                }
            }
            ShowEffect.StopVoice -> {
                voiceJob?.cancel()
                voices?.stopPlaying()
            }
            is ShowEffect.RecordVoice -> {
                voiceJob?.cancel()
                voiceJob = launch {
                    inbox.send(voices?.keep(effect.onAir, effect.guest, signalOf(effect.onAir)) ?: ShowInput.KeepResult(null))
                }
            }
            ShowEffect.CancelRecording -> {
                voiceJob?.cancel()
                voices?.cancelRecording()
            }
            is ShowEffect.DeleteVoice -> launch { voices?.delete(effect.id) }
            is ShowEffect.SaveEpisode -> launch {
                val record = effect.toRecord()
                studio.saveEpisode(record)
                memory?.remember(record, effect.onAir.roster)
            }
        }
    }

    /** 장면은 하나씩만 돈다. 끝까지 가면 [done] 을 inbox 에 넣고, 취소되면 넣지 않는다. */
    private fun CoroutineScope.scene(done: () -> ShowInput, block: suspend () -> Unit) {
        sceneJob?.cancel()
        sceneJob = launch {
            block()
            inbox.send(done())
        }
    }

    /** 이번 회에 지난 상황. 다음에 같은 상황이 오면 이 목소리를 먼저 고른다. */
    private fun signalOf(onAir: ShowState.OnAir): SignalCategory? =
        machine.triggersOf(onAir).firstOrNull { it == SignalCategory.RAIN || it == SignalCategory.TUNNEL }

    private fun currentEpisode() = when (val s = _state.value) {
        is ShowState.OnAir -> s.episode
        is ShowState.SafetyPaused -> s.resumeTo.episode
        else -> 0
    }

    private fun currentRoster() = when (val s = _state.value) {
        is ShowState.OnAir -> s.roster
        is ShowState.SafetyPaused -> s.resumeTo.roster
        else -> emptyList()
    }

    private fun ShowEffect.SaveEpisode.toRecord() = EpisodeRecord(
        number = onAir.episode,
        guestIds = onAir.appeared.toList(),
        cornerIds = onAir.playedCornerIds,
        startedAtMillis = onAir.startedAtMillis,
        endedAtMillis = endedAtMillis,
        answers = onAir.answers,
    )
}
