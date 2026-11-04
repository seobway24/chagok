package com.team.chagok.core.domain.show

import com.team.chagok.core.model.Corner
import com.team.chagok.core.model.FormatPack
import com.team.chagok.core.model.Guest
import com.team.chagok.core.model.GuestId
import com.team.chagok.core.model.Seat
import com.team.chagok.core.model.SignalCategory
import com.team.chagok.core.model.SignalEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 방송 중에 늦게 탄 사람은 정차 중일 때 출연자로 들어오고, 게스트 입장 코너(또는 환영 한 줄)로 맞는다. */
class LateJoinTest {

    private val driver = Guest(GuestId("g0"), "캡틴", Seat.DRIVER)
    private val front = Guest(GuestId("g1"), "부캡틴", Seat.FRONT_PASSENGER)
    private val late = Guest(GuestId("g2"), "막내", Seat.REAR_LEFT)
    private val roster = listOf(driver, front)

    private val weather = Corner("weather", "기상 특보", SignalCategory.RAIN, "비?")
    private val entrance = Corner("door", "게스트 입장", SignalCategory.DOOR, "입장?")
    private val filler = Corner("balance", "밸런스 토크", SignalCategory.QUIET, "골라?")
    private val machine = ShowStateMachine(FormatPack("basic", "기본", listOf(weather, entrance, filler)))

    private val parked = ShowState.OnAir(episode = 1, roster = roster, driving = false, startedAtMillis = 0)

    private fun ShowState.join(guest: Guest, buckled: Boolean = true) = machine.reduce(this, ShowInput.Joined(guest, buckled))
    private fun ShowState.on(event: SignalEvent) = machine.reduce(this, ShowInput.Signal(event))
    private fun Transition.after(input: ShowInput) = machine.reduce(state, input)
    private fun ShowState.onAir() = this as ShowState.OnAir

    @Test
    fun `late boarder joins the roster while parked and the entrance corner greets them`() {
        val joined = parked.join(late)

        val onAir = joined.state.onAir()
        assertEquals(roster + late, onAir.roster)
        assertEquals("door", onAir.live?.corner?.id)
        assertEquals(late, onAir.live?.guest)
        assertTrue(joined.effects.any { it is ShowEffect.RunCorner && it.live.guest == late })
    }

    @Test
    fun `seat taken while driving is ignored`() {
        val driving = parked.copy(driving = true)

        val joined = driving.join(late)

        assertEquals(driving, joined.state)
        assertTrue(joined.effects.isEmpty())
    }

    @Test
    fun `someone already on the roster at that seat is not added twice`() {
        val joined = parked.join(front.copy(id = GuestId("other")))

        assertEquals(parked, joined.state)
    }

    @Test
    fun `door cue for the joined seat greets the joined person after the current corner`() {
        val busy = parked.on(SignalEvent.RainStarted).state
        assertEquals("weather", busy.onAir().live?.corner?.id)

        val joined = busy.join(late).state
        assertEquals(roster + late, joined.onAir().roster)
        assertEquals("weather", joined.onAir().live?.corner?.id)

        val next = machine.reduce(joined, ShowInput.CornerDone).state.onAir()
        assertEquals("door", next.live?.corner?.id)
        assertEquals(late, next.live?.guest)
    }

    @Test
    fun `entrance corner already played this episode means a one-line welcome with their name`() {
        val played = parked.copy(playedCornerIds = listOf("door"), turns = listOf(front.id))

        val joined = played.join(late)

        val onAir = joined.state.onAir()
        assertEquals(roster + late, onAir.roster)
        assertNull(onAir.live)
        assertEquals(listOf(late), onAir.greeting)
        assertTrue(joined.effects.contains(ShowEffect.PlayWelcome(listOf(late))))
    }

    @Test
    fun `welcome waits for the corner on air and opens nothing on top of it`() {
        val busy = parked.copy(playedCornerIds = listOf("door")).on(SignalEvent.RainStarted).state

        val joined = busy.join(late)
        assertTrue(joined.effects.none { it is ShowEffect.PlayWelcome })

        val done = joined.after(ShowInput.CornerDone)
        assertTrue(done.effects.contains(ShowEffect.PlayWelcome(listOf(late))))
        // 환영 한 줄이 나가는 동안 채움 코너를 겹쳐 열지 않는다
        val quiet = machine.reduce(done.state, ShowInput.QuietDue)
        assertNull(quiet.state.onAir().live)

        val after = machine.reduce(done.state, ShowInput.WelcomeDone)
        assertTrue(after.state.onAir().greeting.isEmpty())
        assertTrue(after.effects.any { it is ShowEffect.ScheduleQuiet })
    }

    @Test
    fun `unbuckled joiner is named in the belt reminder and greeted after buckling`() {
        val joined = parked.join(late, buckled = false)
        // 벨트 전에는 말을 걸지 않는다
        assertTrue(joined.effects.none { it is ShowEffect.RunCorner || it is ShowEffect.PlayWelcome })

        val held = joined.after(ShowInput.BeltsChanged(listOf(Seat.REAR_LEFT)))
        assertTrue(held.state is ShowState.SafetyPaused)
        assertTrue(held.effects.contains(ShowEffect.SayBelt(listOf("막내"))))

        val buckled = held.after(ShowInput.BeltsChanged(emptyList()))
        val resumed = buckled.after(ShowInput.ResumeDue).state.onAir()
        assertEquals("door", resumed.live?.corner?.id)
        assertEquals(late, resumed.live?.guest)
    }

    @Test
    fun `turn signal cuts the welcome line and it plays again after resume`() {
        val greeting = parked.copy(playedCornerIds = listOf("door")).join(late).state

        val paused = machine.reduce(greeting, ShowInput.Signal(SignalEvent.TurnSignalChanged(on = true)))
        assertTrue(paused.effects.contains(ShowEffect.InterruptCorner))

        val released = paused.after(ShowInput.Signal(SignalEvent.TurnSignalChanged(on = false)))
        val resumed = released.after(ShowInput.ResumeDue)
        assertTrue(resumed.effects.contains(ShowEffect.PlayWelcome(listOf(late))))
    }

    @Test
    fun `joined person takes turns and appears in the episode`() {
        val greeted = parked.join(late)
        val done = greeted.after(ShowInput.CornerDone).state.onAir()

        assertTrue(late.id in done.appeared)
        val ending = done.on(SignalEvent.GearParked)
        val play = ending.effects.filterIsInstance<ShowEffect.PlayEnding>().single()
        assertTrue(late in play.onAir.roster)
    }

    @Test
    fun `joining during the opening adds them and queues the entrance corner`() {
        val opening = ShowState.Opening(1, roster, startedAtMillis = 0, driving = false)

        val joined = opening.join(late).state as ShowState.Opening
        assertEquals(roster + late, joined.roster)

        val onAir = machine.reduce(joined, ShowInput.OpeningDone).state.onAir()
        assertEquals("door", onAir.live?.corner?.id)
        assertEquals(late, onAir.live?.guest)
    }

    @Test
    fun `joining while paused is remembered and greeted on resume`() {
        val paused = parked.on(SignalEvent.TurnSignalChanged(on = true)).state

        val joined = paused.join(late).state as ShowState.SafetyPaused
        assertEquals(roster + late, joined.resumeTo.roster)

        val resumed = machine.reduce(joined, ShowInput.Signal(SignalEvent.TurnSignalChanged(on = false)))
            .after(ShowInput.ResumeDue).state.onAir()
        assertEquals(late, resumed.live?.guest)
    }

    @Test
    fun `door cue for an empty seat greets nobody`() {
        val opened = parked.on(SignalEvent.DoorClosed(Seat.REAR_RIGHT))

        assertNull(opened.state.onAir().live)
        assertTrue(opened.effects.none { it is ShowEffect.RunCorner })
    }

    @Test
    fun `picker never hands a seat-hinted corner to someone in another seat`() {
        val picker = GuestPicker()

        assertNull(picker.pick(roster, entrance, Seat.REAR_RIGHT, emptyList(), driving = false))
        assertEquals(front, picker.pick(roster, entrance, Seat.FRONT_PASSENGER, emptyList(), driving = false))
    }
}
