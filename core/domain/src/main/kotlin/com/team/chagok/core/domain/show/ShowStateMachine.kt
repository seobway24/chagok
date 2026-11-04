package com.team.chagok.core.domain.show

import com.team.chagok.core.domain.show.ShowEffect.CancelQuiet
import com.team.chagok.core.domain.show.ShowEffect.CancelResume
import com.team.chagok.core.domain.show.ShowEffect.InterruptCorner
import com.team.chagok.core.domain.show.ShowEffect.ScheduleResume
import com.team.chagok.core.domain.show.ShowState.Ending
import com.team.chagok.core.domain.show.ShowState.Idle
import com.team.chagok.core.domain.show.ShowState.Keepsake
import com.team.chagok.core.domain.show.ShowState.OnAir
import com.team.chagok.core.domain.show.ShowState.Opening
import com.team.chagok.core.domain.show.ShowState.ReplayOffer
import com.team.chagok.core.domain.show.ShowState.SafetyPaused
import com.team.chagok.core.model.FormatPack
import com.team.chagok.core.model.Guest
import com.team.chagok.core.model.Seat
import com.team.chagok.core.model.SignalCategory
import com.team.chagok.core.model.SignalEvent
import com.team.chagok.core.domain.signal.DEFAULT_ARRIVING_SOON
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * 회차 규칙 전부. 순수 함수라 I/O·시간·코루틴이 없다.
 *
 * 우선순위: SafetyPause(깜빡이·벨트) > 엔딩(기어 P) > 코너.
 * 코너는 한 번에 하나만, 브레이크를 밟는 동안에는 새로 열지 않는다. 한 회차에 같은 코너는 한 번만.
 * 코너를 못 여는 순간에 온 신호는 [Cue] 로 기다렸다가 장면이 끝나면 먼저 연다.
 * 장면이 끝나고 기다리는 신호도 없으면 [quietAfter] 타이머를 건다 — 그동안 신호가 없으면 채움 코너(QUIET)를 연다.
 */
class ShowStateMachine(
    private val pack: FormatPack,
    private val picker: GuestPicker = GuestPicker(),
    private val resumeDelay: Duration = DEFAULT_RESUME_DELAY,
    private val quietAfter: Duration = DEFAULT_QUIET_AFTER,
    private val calmAfterBrake: Duration = DEFAULT_CALM_AFTER_BRAKE,
) {

    fun reduce(state: ShowState, input: ShowInput): Transition = when (input) {
        is ShowInput.Start -> start(state, input)
        is ShowInput.Signal -> when (state) {
            is Idle -> onIdleSignal(state, input.event)
            is ReplayOffer -> onOfferSignal(state, input.event)
            is Keepsake -> onKeepSignal(state, input.event)
            else -> onSignal(state, input.event)
        }
        ShowInput.OpeningDone -> openingDone(state)
        is ShowInput.Answered -> updateOnAir(state) { it.copy(answers = it.answers + input.answer) }
        ShowInput.CornerDone -> cornerDone(state)
        ShowInput.ResumeDue -> resume(state)
        is ShowInput.BeltsChanged -> belts(state, input.unbuckled)
        ShowInput.QuietDue -> quiet(state)
        is ShowInput.EndingDone -> endingDone(state, input.atMillis)
        ShowInput.PlayOffered -> playOffered(state)
        ShowInput.SkipOffer, ShowInput.VoicePlayed -> leaveOffer(state)
        is ShowInput.KeepBy -> keepBy(state, input.guest)
        ShowInput.KeepCancel -> keepCancel(state)
        is ShowInput.KeepResult -> keepResult(state, input)
        ShowInput.KeepListen -> keepListen(state)
        ShowInput.KeepDelete -> keepDelete(state)
        ShowInput.KeepClose -> closeKeep(state)
    }

    fun triggersOf(onAir: OnAir): List<SignalCategory> =
        (onAir.callbacks + pack.corners).filter { it.id in onAir.playedCornerIds }.map { it.trigger }

    private fun scheduleQuiet(state: OnAir) = ShowEffect.ScheduleQuiet(state.quietGap())

    /**
     * 채움 코너 간격. 남은 시간을 모르면 [quietAfter]. 알면 도착 예고 전까지 남은 채움 코너가 고르게 퍼지도록 늘린다 —
     * 짧은 길에선 촘촘히, 긴 길에선 [MAX_QUIET_GAP] 까지 띄운다. [quietAfter] 보다 짧아지지는 않는다.
     */
    private fun OnAir.quietGap(): Duration {
        val left = remaining ?: return quietAfter
        val fillers = (callbacks + pack.corners).count { it.trigger == SignalCategory.QUIET && it.id !in playedCornerIds }
        val usable = left - DEFAULT_ARRIVING_SOON
        if (fillers == 0 || usable <= Duration.ZERO) return quietAfter
        return (usable / (fillers + 1)).coerceIn(quietAfter, maxOf(quietAfter, MAX_QUIET_GAP))
    }

    /** 도착 예고를 했거나 도착했다. 이제 새 코너는 열지 않고 엔딩(기어 P)을 기다린다. */
    private val OnAir.wrappingUp: Boolean
        get() = arrived || (callbacks + pack.corners).any { it.trigger == SignalCategory.ARRIVAL && it.id in playedCornerIds }

    // ---- 진행 단계 ----

    /**
     * 대기 중에 들어온 차량 값으로 시작한다. 깜빡이가 켜져 있으면 오프닝 없이 멈춘 채로 시작한다.
     * 시작은 터치 입력이라 주행 중에는 받지 않는다. 주행 값을 아직 못 받았을 때도 주행 중으로 친다.
     */
    private fun start(state: ShowState, input: ShowInput.Start): Transition {
        if (state !is Idle || state.driving) return Transition(state)
        val opening = Opening(
            input.episode, input.roster, input.atMillis,
            driving = state.driving, braking = state.braking, callbacks = input.callbacks, pending = state.pending,
            canKeep = input.canKeep, destination = state.destination, remaining = state.remaining,
        )
        if (input.beltOff.isNotEmpty()) {
            val held = SafetyPaused(opening.toOnAir(), heldBySignal = state.turnSignal, beltOff = input.beltOff)
            return Transition(held, listOf(sayBelt(held.resumeTo, input.beltOff)))
        }
        if (state.turnSignal) return Transition(SafetyPaused(opening.toOnAir()))
        if (input.offer != null) return Transition(ReplayOffer(opening, input.offer))
        return Transition(opening, listOf(playOpening(opening)))
    }

    private fun openingDone(state: ShowState): Transition {
        if (state !is Opening) return Transition(state)
        return settle(state.toOnAir())
    }

    private fun cornerDone(state: ShowState): Transition {
        val live = (state as? OnAir)?.live ?: return Transition(state)
        return settle(
            state.copy(
                live = null,
                playedCornerIds = state.playedCornerIds + live.corner.id,
                turns = state.turns + live.guest.id,
            ),
        )
    }

    /** 장면이 끝났다. 기다리는 신호의 코너를 앞에서부터 열고, 열 게 없으면 조용함 타이머를 건다. */
    private fun settle(state: OnAir): Transition {
        // 브레이크 중이거나 진정 중이면 기다리는 신호를 그대로 두고 뒤에 연다
        if (state.braking || state.calming) return Transition(state, listOf(scheduleQuiet(state)))
        val opened = state.pending.indices.firstNotNullOfOrNull { i ->
            val rest = state.copy(pending = state.pending.drop(i + 1))
            openCorner(rest, state.pending[i]).takeIf { it.state !== rest }
        }
        return opened ?: Transition(state.copy(pending = emptyList()), listOf(scheduleQuiet(state)))
    }

    /** 조용함 타이머가 울렸다. 못 열면(브레이크·남은 코너 없음 등) 다시 기다린다. 마무리 중이면 채우지 않는다. */
    private fun quiet(state: ShowState): Transition {
        if (state !is OnAir || state.live != null || state.wrappingUp) return Transition(state)
        if (state.braking || state.calming) return Transition(state, listOf(scheduleQuiet(state)))
        val opened = openCorner(state, Cue(SignalCategory.QUIET))
        return if (opened.state === state) Transition(state, listOf(scheduleQuiet(state))) else opened
    }

    private fun endingDone(state: ShowState, atMillis: Long): Transition {
        if (state !is Ending) return Transition(state)
        val save = ShowEffect.SaveEpisode(state.onAir, atMillis)
        val parked = !state.onAir.driving && !state.turnSignal
        if (state.onAir.canKeep && parked) return Transition(Keepsake(state.onAir), listOf(save))
        return Transition(state.onAir.toIdle(state.turnSignal), listOf(save))
    }

    // ---- 지난 목소리 제안 ----

    private fun playOffered(state: ShowState): Transition {
        if (state !is ReplayOffer || state.playing) return Transition(state)
        return Transition(state.copy(playing = true), listOf(ShowEffect.PlayVoice(state.clip)))
    }

    private fun leaveOffer(state: ShowState): Transition {
        if (state !is ReplayOffer) return Transition(state)
        val stop = listOfNotNull(ShowEffect.StopVoice.takeIf { state.playing })
        return openFromOffer(state, state.opening, stop)
    }

    /** 제안을 접고 오프닝으로 간다. 제안 중에 벨트를 푼 사람이 있으면 오프닝 대신 멈추고 이름을 부른다. */
    private fun openFromOffer(state: ReplayOffer, opening: Opening, stop: List<ShowEffect>): Transition {
        if (state.beltOff.isEmpty()) return Transition(opening, stop + playOpening(opening))
        val held = SafetyPaused(opening.toOnAir(), heldBySignal = false, beltOff = state.beltOff)
        return Transition(held, stop + sayBelt(held.resumeTo, state.beltOff))
    }

    /** 출발·깜빡이·기어 변경이면 소리를 끊고 제안을 접는다. 깜빡이는 평소처럼 멈춘 채로 시작한다. */
    private fun onOfferSignal(state: ReplayOffer, event: SignalEvent): Transition {
        val stop = listOfNotNull(ShowEffect.StopVoice.takeIf { state.playing })
        val opening = state.opening
        return when {
            event is SignalEvent.TurnSignalChanged && event.on ->
                Transition(SafetyPaused(opening.toOnAir(), beltOff = state.beltOff), stop)
            event is SignalEvent.DrivingChanged && event.driving -> openFromOffer(state, opening.copy(driving = true), stop)
            event == SignalEvent.GearReleased -> openFromOffer(state, opening, stop)
            else -> Transition(state)
        }
    }

    // ---- 한마디 남기기 ----

    private fun keepBy(state: ShowState, guest: Guest): Transition {
        if (state !is Keepsake || state.stage != KeepStage.Choose) return Transition(state)
        return Transition(state.copy(stage = KeepStage.Recording(guest), failed = false), listOf(ShowEffect.RecordVoice(state.onAir, guest)))
    }

    private fun keepCancel(state: ShowState): Transition {
        if (state !is Keepsake || state.stage !is KeepStage.Recording) return Transition(state)
        return Transition(state.copy(stage = KeepStage.Choose), listOf(ShowEffect.CancelRecording))
    }

    private fun keepResult(state: ShowState, input: ShowInput.KeepResult): Transition {
        if (state !is Keepsake || state.stage !is KeepStage.Recording) return Transition(state)
        return Transition(
            when {
                input.clip != null -> state.copy(stage = KeepStage.Saved, saved = input.clip)
                input.full -> state.copy(stage = KeepStage.Full)
                else -> state.copy(stage = KeepStage.Choose, failed = true)
            },
        )
    }

    private fun keepListen(state: ShowState): Transition {
        val clip = (state as? Keepsake)?.saved ?: return Transition(state)
        return Transition(state, listOf(ShowEffect.PlayVoice(clip)))
    }

    private fun keepDelete(state: ShowState): Transition {
        val clip = (state as? Keepsake)?.saved ?: return Transition(state)
        return Transition(state.copy(stage = KeepStage.Choose, saved = null), listOf(ShowEffect.StopVoice, ShowEffect.DeleteVoice(clip.id)))
    }

    private fun closeKeep(state: ShowState, turnSignal: Boolean = false): Transition {
        if (state !is Keepsake) return Transition(state)
        val effects = listOfNotNull(
            ShowEffect.CancelRecording.takeIf { state.stage is KeepStage.Recording },
            ShowEffect.StopVoice.takeIf { state.saved != null },
        )
        return Transition(state.onAir.toIdle(turnSignal), effects)
    }

    /** 방송이 끝나도 차량 값과 내비 목적지는 이어진다(다음 회를 바로 시작할 수 있게). 도착한 목적지는 넘기지 않는다. */
    private fun OnAir.toIdle(turnSignal: Boolean) = Idle(
        driving, braking, turnSignal,
        destination = destination.takeUnless { arrived },
        remaining = remaining.takeUnless { arrived },
    )

    private fun playOpening(opening: Opening) = ShowEffect.PlayOpening(opening.episode, opening.roster, opening.destination)

    /** 출발·깜빡이·기어 변경이면 녹음을 버리고 바로 끝낸다. */
    private fun onKeepSignal(state: Keepsake, event: SignalEvent): Transition = when {
        event is SignalEvent.TurnSignalChanged && event.on -> closeKeep(state, turnSignal = true)
        event is SignalEvent.DrivingChanged && event.driving -> closeKeep(state.copy(onAir = state.onAir.copy(driving = true)))
        event == SignalEvent.GearReleased -> closeKeep(state)
        else -> Transition(state)
    }

    // ---- 신호 ----

    /** 방송 전에는 차량 값만 기억한다. 비·전조등은 상태가 이어지므로 시작할 때 코너로 넘긴다. */
    private fun onIdleSignal(state: Idle, event: SignalEvent): Transition = Transition(
        when (event) {
            is SignalEvent.DrivingChanged -> state.copy(driving = event.driving)
            is SignalEvent.BrakePedalChanged -> state.copy(braking = event.pressed)
            is SignalEvent.TurnSignalChanged -> state.copy(turnSignal = event.on)
            SignalEvent.RainStarted, SignalEvent.LightsOn -> state.copy(pending = state.pending.plusOnce(event.toCue()!!))
            SignalEvent.RainStopped -> state.copy(pending = state.pending.without(SignalCategory.RAIN))
            SignalEvent.LightsOff -> state.copy(pending = state.pending.without(SignalCategory.TUNNEL))
            is SignalEvent.RouteChanged -> state.copy(destination = event.destination, remaining = state.remaining.takeIf { event.destination != null })
            is SignalEvent.RemainingChanged -> state.copy(remaining = event.left)
            SignalEvent.Arrived -> state.copy(destination = null, remaining = null)
            // 방송 전 도착 예고는 버린다. 시작하자마자 "곧 도착"은 맞지 않는다
            else -> state
        },
    )

    private fun onSignal(state: ShowState, event: SignalEvent): Transition = when (event) {
        is SignalEvent.TurnSignalChanged -> if (event.on) pause(state) else release(state)
        is SignalEvent.BrakePedalChanged -> brake(state, event.pressed)
        is SignalEvent.DrivingChanged -> if (event.driving) startDriving(state) else updateOnAir(state) { it.copy(driving = false) }
        SignalEvent.GearParked -> end(state)
        SignalEvent.RainStopped -> withdraw(state, SignalCategory.RAIN)
        SignalEvent.LightsOff -> withdraw(state, SignalCategory.TUNNEL)
        is SignalEvent.RouteChanged -> route(state, event.destination)
        is SignalEvent.RemainingChanged -> remaining(state, event.left)
        SignalEvent.Arrived -> arrive(state)
        else -> event.toCue()?.let { onCue(state, it) } ?: Transition(state)
    }

    private fun remaining(state: ShowState, left: Duration): Transition = when (state) {
        is Opening -> Transition(state.copy(remaining = left))
        else -> updateOnAir(state) { it.copy(remaining = left) }
    }

    /**
     * 도착. 도착 예고를 못 했으면(짧은 길·늦게 시작) 지금 도착 코너를 연다. 그 뒤엔 마무리 —
     * 새 코너 없이 기어 P 엔딩을 기다린다. 엔딩을 도착으로 당기지 않는 건 도착 뒤에도 주차 자리를 찾으며 움직이기 때문이다.
     */
    private fun arrive(state: ShowState): Transition {
        val arrival = Cue(SignalCategory.ARRIVAL)
        if (state is Opening) return Transition(state.copy(pending = state.pending.plusOnce(arrival)))
        val marked = updateOnAir(state) { it.copy(arrived = true, pending = it.pending.filter { cue -> cue == arrival }) }
        return onCue(marked.state, arrival)
    }

    /** 지금 열 수 있으면 열고, 장면이 도는 중이거나 브레이크를 밟고 있거나 막 뗐으면 기다리게 둔다. */
    private fun onCue(state: ShowState, cue: Cue): Transition {
        if (state is OnAir && state.live == null && !state.braking && !state.calming) return openCorner(state, cue)
        if (state is Opening) return Transition(state.copy(pending = state.pending.plusOnce(cue)))
        return updateOnAir(state) { if (it.canStillOpen(cue)) it.copy(pending = it.pending.plusOnce(cue)) else it }
    }

    private fun OnAir.canStillOpen(cue: Cue) = (callbacks + pack.corners).any {
        it.trigger == cue.category && it.id !in playedCornerIds && it.id != live?.corner?.id
    }

    /** 비가 그치거나 터널을 나왔다. 기다리던 코너는 이제 맞지 않는다. */
    private fun withdraw(state: ShowState, category: SignalCategory): Transition =
        if (state is Opening) {
            Transition(state.copy(pending = state.pending.without(category)))
        } else {
            updateOnAir(state) { it.copy(pending = it.pending.without(category)) }
        }

    /** 목적지가 바뀌었다. 안내가 끝났으면 기다리던 도착 예고도 거둔다. */
    private fun route(state: ShowState, destination: String?): Transition {
        val updated = when (state) {
            is Opening -> Transition(state.copy(destination = destination, remaining = state.remaining.takeIf { destination != null }))
            else -> updateOnAir(state) { it.copy(destination = destination, remaining = it.remaining.takeIf { destination != null }) }
        }
        return if (destination == null) withdraw(updated.state, SignalCategory.ARRIVAL) else updated
    }

    private fun List<Cue>.plusOnce(cue: Cue) = if (cue in this) this else this + cue
    private fun List<Cue>.without(category: SignalCategory) = filterNot { it.category == category }

    /**
     * 브레이크는 운전 부담이 큰 순간이다. 밟는 동안엔 새 코너를 열지 않고, 뗀 뒤에도 [calmAfterBrake] 동안 조용하면 그때 연다.
     * 진정 중에 다시 밟으면 처음부터 다시 잰다. 이미 돌고 있는 코너는 건드리지 않는다.
     */
    private fun brake(state: ShowState, pressed: Boolean): Transition {
        if (state !is OnAir) return updateOnAir(state) { it.copy(braking = pressed) }
        if (pressed) return Transition(state.copy(braking = true, calming = false), listOf(CancelResume))
        if (!state.braking) return Transition(state)
        return Transition(state.copy(braking = false, calming = true), listOf(CancelResume, ScheduleResume(calmAfterBrake)))
    }

    /** 진정 시간이 끝났다. 그동안 온 신호를 연다. */
    private fun calmDone(state: OnAir): Transition {
        val calm = state.copy(calming = false)
        return if (calm.live == null && !calm.braking && calm.pending.isNotEmpty()) settle(calm) else Transition(calm)
    }

    /**
     * 출발. 정차 중에 운전자에게 연 코너가 아직 돌고 있으면 그 자리에서 끊는다 —
     * 신호가 바뀌었는데 운전자가 대답을 이어 가면 안 된다. 끊긴 코너는 했던 것으로 치지 않는다.
     */
    private fun startDriving(state: ShowState): Transition {
        if (state is Ending) return cutEnding(state.copy(onAir = state.onAir.copy(driving = true)))
        if (state is OnAir && state.live?.corner?.needsDriver == true) {
            val cut = state.copy(driving = true, live = null)
            return Transition(cut, listOf(InterruptCorner, scheduleQuiet(cut)))
        }
        return updateOnAir(state) { it.copy(driving = true) }
    }

    /** 진행 중인 장면을 끊고 멈춘다. 깜빡이가 꺼질 때까지 재개하지 않는다. */
    private fun pause(state: ShowState): Transition = when (state) {
        // 오프닝은 이어 붙이지 않는다. 재개하면 바로 방송 중으로 간다
        is Opening -> Transition(SafetyPaused(state.toOnAir()), listOf(InterruptCorner, CancelResume, CancelQuiet))
        is OnAir -> Transition(
            SafetyPaused(state.cutLive()),
            listOfNotNull(InterruptCorner.takeIf { state.live != null }, CancelResume, CancelQuiet),
        )
        is SafetyPaused -> Transition(state.copy(heldBySignal = true), listOf(CancelResume))
        is Ending -> cutEnding(state.copy(turnSignal = true))
        else -> Transition(state)
    }

    /** 엔딩 질문은 정차를 전제로 한다. 출발·깜빡이가 오면 대사를 끊고 지금까지 들은 것을 바로 저장한다. */
    private fun cutEnding(state: Ending): Transition = Transition(state, listOf(ShowEffect.CutEnding))

    /** 끊긴 코너는 재개 뒤 맨 앞에서 다시 연다. 채움 코너는 조용함 타이머가 다시 고른다. */
    private fun OnAir.cutLive(): OnAir {
        val cut = live ?: return this
        val trigger = cut.corner.trigger
        if (trigger == SignalCategory.QUIET) return copy(live = null)
        val cue = Cue(trigger, cut.guest.seat.takeIf { trigger == SignalCategory.DOOR })
        return copy(live = null, pending = listOf(cue) + pending.filterNot { it == cue })
    }

    /** 깜빡이가 꺼지면 바로가 아니라 [resumeDelay] 뒤에 재개한다. */
    private fun release(state: ShowState): Transition = when (state) {
        is SafetyPaused -> Transition(state.copy(heldBySignal = false), resumeEffects(state.beltOff))
        is Ending -> Transition(state.copy(turnSignal = false))
        else -> Transition(state)
    }

    private fun resume(state: ShowState): Transition = when {
        state is SafetyPaused && !state.heldBySignal && state.beltOff.isEmpty() -> settle(state.resumeTo.copy(calming = false))
        state is OnAir && state.calming -> calmDone(state)
        else -> Transition(state)
    }

    /** 방송 중에 벨트를 풀면 깜빡이처럼 바로 끊고 이름을 부른다. 모두 매면 [resumeDelay] 뒤 이어 간다. */
    private fun belts(state: ShowState, unbuckled: List<Seat>): Transition = when (state) {
        is SafetyPaused -> {
            val added = unbuckled - state.beltOff.toSet()
            val say = listOfNotNull(sayBelt(state.resumeTo, added).takeIf { added.isNotEmpty() })
            val resume = if (state.heldBySignal) emptyList() else resumeEffects(unbuckled)
            Transition(state.copy(beltOff = unbuckled), resume + say)
        }
        is Opening, is OnAir -> {
            if (unbuckled.isEmpty()) {
                Transition(state)
            } else {
                val paused = pause(state)
                val held = (paused.state as SafetyPaused).copy(heldBySignal = false, beltOff = unbuckled)
                Transition(held, paused.effects + sayBelt(held.resumeTo, unbuckled))
            }
        }
        // 제안은 정차 중이라 멈출 소리가 없다. 기억해 두었다가 오프닝으로 넘어갈 때 막는다
        is ReplayOffer -> Transition(state.copy(beltOff = unbuckled))
        else -> Transition(state)
    }

    private fun resumeEffects(beltOff: List<Seat>): List<ShowEffect> =
        if (beltOff.isEmpty()) listOf(CancelResume, ScheduleResume(resumeDelay)) else listOf(CancelResume)

    private fun sayBelt(onAir: OnAir, seats: List<Seat>) =
        ShowEffect.SayBelt(seats.map { seat -> onAir.roster.firstOrNull { it.seat == seat }?.nickname ?: seat.label })

    private fun end(state: ShowState): Transition {
        val onAir = when (state) {
            is Opening -> state.toOnAir()
            is OnAir -> state
            is SafetyPaused -> state.resumeTo
            else -> return Transition(state)
        }
        val effects = listOfNotNull(
            InterruptCorner.takeIf { onAir.live != null || state is Opening },
            CancelResume,
            CancelQuiet,
            ShowEffect.PlayEnding(onAir.copy(live = null)),
        )
        val turnSignal = state is SafetyPaused && state.heldBySignal
        return Transition(Ending(onAir.copy(live = null), turnSignal), effects)
    }

    private fun openCorner(state: ShowState, cue: Cue): Transition {
        if (state !is OnAir || state.live != null || state.braking || state.calming) return Transition(state)
        // 마무리 중에는 도착 코너만 연다. 작별 인사 뒤에 터널 퀴즈가 이어지면 어색하다
        if (state.wrappingUp && cue.category != SignalCategory.ARRIVAL) return Transition(state)
        // 지난 회 이어 묻기가 먼저. 운전자의 말이 필요한 코너는 정차 중에만 연다.
        // 부를 사람이 없으면(예: 운전 중인데 운전자 전용) 다음 코너로 넘어간다
        val live = (state.callbacks + pack.corners).asSequence()
            .filter { it.trigger == cue.category && it.id !in state.playedCornerIds && !(it.needsDriver && state.driving) }
            .firstNotNullOfOrNull { corner ->
                picker.pick(state.roster, corner, cue.seat, state.turns, state.driving)?.let { LiveCorner(corner, it) }
            } ?: return Transition(state)

        return Transition(state.copy(live = live), listOf(CancelQuiet, ShowEffect.RunCorner(live)))
    }

    /** OnAir 이든 오프닝·멈춤·엔딩 중이든 같은 갱신을 적용한다(넘어갔을 때 최신 값이 남도록). */
    private inline fun updateOnAir(state: ShowState, update: (OnAir) -> OnAir): Transition = when (state) {
        is Opening -> update(state.toOnAir()).let { Transition(state.copy(driving = it.driving, braking = it.braking)) }
        is OnAir -> Transition(update(state))
        is SafetyPaused -> Transition(state.copy(resumeTo = update(state.resumeTo)))
        is Ending -> Transition(state.copy(onAir = update(state.onAir)))
        else -> Transition(state)
    }

    private fun SignalEvent.toCue(): Cue? = when (this) {
        SignalEvent.RainStarted -> Cue(SignalCategory.RAIN)
        SignalEvent.LightsOn -> Cue(SignalCategory.TUNNEL)
        is SignalEvent.DoorClosed -> Cue(SignalCategory.DOOR, seat)
        is SignalEvent.MilestoneKm -> Cue(SignalCategory.MILESTONE)
        SignalEvent.ArrivingSoon -> Cue(SignalCategory.ARRIVAL)
        else -> null
    }

    companion object {
        /** 깜빡이가 꺼진 뒤 재개까지 (architecture §4.1). */
        val DEFAULT_RESUME_DELAY: Duration = 2.seconds

        /** 장면이 끝나고 이만큼 신호가 없으면 채움 코너. 너무 짧으면 수다스럽고, 길면 방송이 끊긴 것처럼 들린다. */
        val DEFAULT_QUIET_AFTER: Duration = 45.seconds

        /** 브레이크에서 발을 뗀 뒤 새 코너를 열기까지. 막히는 길·정지 직후에 말을 걸지 않는다 (검토 B). */
        val DEFAULT_CALM_AFTER_BRAKE: Duration = 10.seconds

        /** 긴 길에서도 채움 코너 사이를 이 이상 비우지 않는다. 길면 방송이 꺼진 줄 안다. */
        val MAX_QUIET_GAP: Duration = 4.minutes
    }
}
