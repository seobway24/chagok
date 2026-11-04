package com.team.chagok.core.domain.show

import com.team.chagok.core.model.Corner
import com.team.chagok.core.model.FormatPack
import com.team.chagok.core.model.Guest
import com.team.chagok.core.model.GuestId
import com.team.chagok.core.model.Seat
import com.team.chagok.core.model.SignalCategory
import com.team.chagok.core.model.SignalEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowStateMachineTest {

    private val captain = Guest(GuestId("g0"), "캡틴", Seat.DRIVER)
    private val jiho = Guest(GuestId("g1"), "지호", Seat.REAR_LEFT, isChild = true)
    private val roster = listOf(captain, jiho)

    private val weather = Corner("weather", "기상 특보", SignalCategory.RAIN, "{guest} 캐스터, 비 몇 점?")
    private val captainTalk = Corner("captain", "캡틴 토크", SignalCategory.TUNNEL, "캡틴?", needsDriver = true)
    private val machine = ShowStateMachine(FormatPack("basic", "기본", listOf(weather, captainTalk)))

    private val onAir = ShowState.OnAir(episode = 12, roster = roster, startedAtMillis = 0)

    private fun ShowState.on(event: SignalEvent) = machine.reduce(this, ShowInput.Signal(event))

    @Test
    fun `rain opens weather corner for a passenger, not the driver`() {
        val t = onAir.on(SignalEvent.RainStarted)

        val live = (t.state as ShowState.OnAir).live!!
        assertEquals(jiho, live.guest)
        assertEquals(listOf(ShowEffect.CancelQuiet, ShowEffect.RunCorner(live)), t.effects)
    }

    @Test
    fun `same corner does not open twice in one episode`() {
        val done = machine.reduce(onAir.on(SignalEvent.RainStarted).state, ShowInput.CornerDone).state

        val again = done.on(SignalEvent.RainStarted)
        assertEquals(done, again.state)
        assertTrue(again.effects.isEmpty())
    }

    @Test
    fun `quiet timer opens a filler corner and re-arms when nothing can open`() {
        val filler = Corner("dream", "꿈 인터뷰", SignalCategory.QUIET, "{guest}, 꿈은?")
        val m = ShowStateMachine(FormatPack("p", "p", listOf(filler)))
        val start = ShowState.OnAir(episode = 1, roster = roster, startedAtMillis = 0)

        val opened = m.reduce(start, ShowInput.QuietDue)
        assertEquals("dream", (opened.state as ShowState.OnAir).live!!.corner.id)

        // 다 쓴 뒤에는 열 코너가 없다 → 상태 그대로, 타이머만 다시 건다
        val done = m.reduce(opened.state, ShowInput.CornerDone)
        assertTrue(done.effects.any { it is ShowEffect.ScheduleQuiet })
        val nothing = m.reduce(done.state, ShowInput.QuietDue)
        assertEquals(done.state, nothing.state)
        assertTrue(nothing.effects.single() is ShowEffect.ScheduleQuiet)
    }

    @Test
    fun `mic rotates to whoever spoke least and longest ago`() {
        val mom = Guest(GuestId("g2"), "엄마", Seat.FRONT_PASSENGER)
        val picker = GuestPicker()
        val family = listOf(captain, mom, jiho)

        // 둘 다 한 번씩 나왔으면, 먼저 말한 엄마 차례
        assertEquals(mom, picker.pick(family, weather, null, listOf(mom.id, jiho.id), driving = true))
        // 엄마가 두 번이면 지호
        assertEquals(jiho, picker.pick(family, weather, null, listOf(mom.id, jiho.id, mom.id), driving = true))
    }

    @Test
    fun `turn signal interrupts the live corner and holds until released`() {
        val live = onAir.on(SignalEvent.RainStarted).state

        val paused = live.on(SignalEvent.TurnSignalChanged(on = true))
        assertTrue(paused.state is ShowState.SafetyPaused)
        assertTrue(ShowEffect.InterruptCorner in paused.effects)

        // 깜빡이가 켜져 있는 동안에는 재개 타이머가 와도 무시
        assertTrue(machine.reduce(paused.state, ShowInput.ResumeDue).state is ShowState.SafetyPaused)

        val released = paused.state.on(SignalEvent.TurnSignalChanged(on = false))
        assertTrue(released.effects.any { it is ShowEffect.ScheduleResume })

        // 끊긴 코너는 재개할 때 다시 연다
        val resumed = machine.reduce(released.state, ShowInput.ResumeDue).state as ShowState.OnAir
        assertEquals("weather", resumed.live?.corner?.id)
    }

    @Test
    fun `no new corner while braking`() {
        val braking = onAir.on(SignalEvent.BrakePedalChanged(pressed = true)).state

        val t = braking.on(SignalEvent.RainStarted)

        assertEquals(null, (t.state as ShowState.OnAir).live)
        assertTrue(t.effects.isEmpty())
    }

    @Test
    fun `driver corner opens only when parked`() {
        assertTrue(onAir.on(SignalEvent.LightsOn).effects.isEmpty())

        val parked = onAir.copy(driving = false).on(SignalEvent.LightsOn)
        assertEquals(captain, (parked.state as ShowState.OnAir).live!!.guest)
    }

    @Test
    fun `driver corner is cut the moment the car moves again`() {
        val talking = onAir.copy(driving = false).on(SignalEvent.LightsOn).state as ShowState.OnAir
        assertEquals(captain, talking.live!!.guest)

        val moving = talking.on(SignalEvent.DrivingChanged(driving = true))
        val state = moving.state as ShowState.OnAir
        assertEquals(null, state.live)
        assertTrue(state.driving)
        assertTrue(ShowEffect.InterruptCorner in moving.effects)
        // 끊긴 코너는 했던 것으로 치지 않는다
        assertTrue("captain" !in state.playedCornerIds)
    }

    private val opening = ShowState.Opening(episode = 12, roster = roster, startedAtMillis = 0)

    @Test
    fun `turn signal during opening cuts the opening and pauses`() {
        val paused = opening.on(SignalEvent.TurnSignalChanged(on = true))

        assertTrue(paused.state is ShowState.SafetyPaused)
        assertTrue(ShowEffect.InterruptCorner in paused.effects)

        // 끊긴 오프닝이 늦게 끝났다고 알려 와도 멈춤은 그대로
        assertTrue(machine.reduce(paused.state, ShowInput.OpeningDone).state is ShowState.SafetyPaused)

        val released = paused.state.on(SignalEvent.TurnSignalChanged(on = false))
        val resumed = machine.reduce(released.state, ShowInput.ResumeDue).state
        assertTrue(resumed is ShowState.OnAir)
    }

    @Test
    fun `gear park during opening goes straight to the ending`() {
        val t = opening.on(SignalEvent.GearParked)

        assertTrue(t.state is ShowState.Ending)
        assertTrue(ShowEffect.InterruptCorner in t.effects)
        assertTrue(t.effects.any { it is ShowEffect.PlayEnding })
    }

    @Test
    fun `driving and brake changes during opening carry into on air`() {
        val parked = opening
            .on(SignalEvent.DrivingChanged(driving = false)).state
            .on(SignalEvent.BrakePedalChanged(pressed = true)).state

        val onAir = machine.reduce(parked, ShowInput.OpeningDone).state as ShowState.OnAir
        assertEquals(false, onAir.driving)
        assertEquals(true, onAir.braking)
    }

    @Test
    fun `gear park ends the episode and saves after the ending`() {
        val ending = onAir.on(SignalEvent.GearParked)
        assertTrue(ending.state is ShowState.Ending)

        val done = machine.reduce(ending.state, ShowInput.EndingDone(atMillis = 99))
        assertTrue(done.state is ShowState.Idle)
        assertTrue(done.effects.single() is ShowEffect.SaveEpisode)
    }

    @Test
    fun `turn signal during ending cuts it and saves at once`() {
        val ending = onAir.on(SignalEvent.GearParked).state

        val cut = ending.on(SignalEvent.TurnSignalChanged(true))
        assertEquals(listOf<ShowEffect>(ShowEffect.CutEnding), cut.effects)

        val done = machine.reduce(cut.state, ShowInput.EndingDone(atMillis = 99))
        assertEquals(true, (done.state as ShowState.Idle).turnSignal)
        assertTrue(done.effects.single() is ShowEffect.SaveEpisode)
    }

    @Test
    fun `driving off during ending cuts it`() {
        val parked = onAir.copy(driving = false).on(SignalEvent.GearParked).state

        val cut = parked.on(SignalEvent.DrivingChanged(true))
        assertEquals(listOf<ShowEffect>(ShowEffect.CutEnding), cut.effects)
        assertEquals(true, (cut.state as ShowState.Ending).onAir.driving)
    }

    private val rainClip = com.team.chagok.core.model.VoiceClip("c1", 3, jiho.id, "지호", 1, 8000)
    private val start = ShowInput.Start(13, roster, 0, offer = rainClip, canKeep = true)

    @Test
    fun `offer appears only when parked and never plays by itself`() {
        val parked = machine.reduce(ShowState.Idle(driving = false), start)
        assertTrue(parked.state is ShowState.ReplayOffer)
        assertTrue(parked.effects.isEmpty())
    }

    @Test
    fun `episode does not start while driving`() {
        val idle = ShowState.Idle(driving = true)

        val started = machine.reduce(idle, start)

        assertEquals(idle, started.state)
        assertTrue(started.effects.isEmpty())
    }

    @Test
    fun `leaving park stops the voice and opens the show`() {
        val offer = machine.reduce(ShowState.Idle(driving = false), start).state
        val playing = machine.reduce(offer, ShowInput.PlayOffered).state

        val t = playing.on(SignalEvent.GearReleased)
        assertTrue(t.state is ShowState.Opening)
        assertEquals(ShowEffect.StopVoice, t.effects.first())
    }

    @Test
    fun `belt off during the offer holds the show instead of opening`() {
        val offer = machine.reduce(ShowState.Idle(driving = false), start).state
        val unbuckled = machine.reduce(offer, ShowInput.BeltsChanged(listOf(Seat.REAR_LEFT))).state

        val t = unbuckled.on(SignalEvent.GearReleased)

        val held = t.state as ShowState.SafetyPaused
        assertEquals(listOf(Seat.REAR_LEFT), held.beltOff)
        assertTrue(t.effects.none { it is ShowEffect.PlayOpening })
        assertEquals(ShowEffect.SayBelt(listOf("지호")), t.effects.last())
    }

    @Test
    fun `belt off at start holds the show and names the seat`() {
        val t = machine.reduce(ShowState.Idle(driving = false), start.copy(beltOff = listOf(Seat.REAR_LEFT)))

        val held = t.state as ShowState.SafetyPaused
        assertEquals(listOf(Seat.REAR_LEFT), held.beltOff)
        assertEquals(listOf<ShowEffect>(ShowEffect.SayBelt(listOf("지호"))), t.effects)
    }

    private fun keepsake(): ShowState {
        val ending = onAir.copy(driving = false, canKeep = true).on(SignalEvent.GearParked).state
        return machine.reduce(ending, ShowInput.EndingDone(atMillis = 9)).state
    }

    @Test
    fun `keepsake opens after a parked ending only with consent`() {
        assertTrue(keepsake() is ShowState.Keepsake)
        val noConsent = onAir.copy(driving = false).on(SignalEvent.GearParked).state
        assertTrue(machine.reduce(noConsent, ShowInput.EndingDone(atMillis = 9)).state is ShowState.Idle)
    }

    @Test
    fun `recording starts only from a person pressing the button`() {
        val t = machine.reduce(keepsake(), ShowInput.KeepBy(jiho))
        assertTrue(t.effects.single() is ShowEffect.RecordVoice)
        val saved = machine.reduce(t.state, ShowInput.KeepResult(rainClip)).state as ShowState.Keepsake
        assertEquals(KeepStage.Saved, saved.stage)
    }

    @Test
    fun `turn signal and driving discard the recording at once`() {
        val recording = machine.reduce(keepsake(), ShowInput.KeepBy(jiho)).state

        listOf(
            SignalEvent.TurnSignalChanged(true),
            SignalEvent.DrivingChanged(true),
            SignalEvent.GearReleased,
        ).forEach { event ->
            val t = recording.on(event)
            assertTrue(t.state is ShowState.Idle)
            assertTrue(ShowEffect.CancelRecording in t.effects)
        }
    }
}
