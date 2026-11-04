package com.team.chagok.feature.onair

import com.team.chagok.core.domain.port.AudioZone
import com.team.chagok.core.domain.port.Heard
import com.team.chagok.core.domain.port.HostVoice
import com.team.chagok.core.domain.port.MicInput
import com.team.chagok.core.domain.port.StudioRepository
import com.team.chagok.core.domain.show.ShowDirector
import com.team.chagok.core.domain.show.ShowEngine
import com.team.chagok.core.domain.show.ShowStateMachine
import com.team.chagok.core.domain.signal.ManualSignalSource
import com.team.chagok.core.model.Corner
import com.team.chagok.core.model.EpisodeRecord
import com.team.chagok.core.model.FormatPack
import com.team.chagok.core.model.Guest
import com.team.chagok.core.model.GuestId
import com.team.chagok.core.model.HostSkin
import com.team.chagok.core.model.Seat
import com.team.chagok.core.model.SignalCategory
import com.team.chagok.core.model.SignalEvent
import com.team.chagok.core.quip.QuipEngine
import com.team.chagok.core.quip.QuipRequest
import com.team.chagok.core.quip.QuipResult
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration

/** 탑승 확인 중 벨트 안내는 한 번. 마지막 사람이 매는 순간 같은 안내가 또 나오면 안 된다. */
@OptIn(ExperimentalCoroutinesApi::class)
class BoardingBeltCallTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private val spoken = mutableListOf<String>()

    /** 차 스피커에서 아직 나오는 중이거나 차례를 기다리는 줄. */
    private val playing = mutableListOf<String>()

    /**
     * 차 TTS 처럼 한 줄에 3.4초 걸린다. 차 TTS 는 넘겨받은 줄을 제 줄에 세워 두고 읽으므로,
     * 부른 코루틴이 취소돼도 이미 넘긴 줄은 끝까지 나온다. [stop] 만 그 줄을 끊는다.
     */
    private val voice = object : HostVoice {
        private val speaking = Mutex()
        override suspend fun speak(text: String, speaker: String, zone: AudioZone): Boolean = speaking.withLock {
            spoken += text
            playing += text
            try {
                delay(LINE_MS)
            } catch (e: CancellationException) {
                // 코루틴만 취소됐다. 차 스피커는 계속 읽는다
                throw e
            }
            playing -= text
            true
        }
        override fun stop() = playing.clear()
    }

    private val family = listOf(
        PersonCard("g-dad", "아빠", Seat.DRIVER),
        PersonCard("g-mom", "엄마", Seat.FRONT_PASSENGER),
        PersonCard("g-sister", "누나", Seat.REAR_LEFT, isChild = true),
        PersonCard("g-little", "막내", Seat.REAR_RIGHT, isChild = true),
    )

    private fun engine(signals: ManualSignalSource) = ShowEngine(
        machine = ShowStateMachine(FormatPack("p", "p", listOf(Corner("balance", "밸런스", SignalCategory.QUIET, "{guest}, 골라?")))),
        director = ShowDirector(
            voice,
            object : MicInput { override suspend fun listen(window: Duration): Heard = Heard.Silence },
            object : QuipEngine {
                override val id = "none"
                override suspend fun quip(request: QuipRequest): QuipResult = QuipResult.Unavailable(QuipResult.Reason.ERROR)
            },
            HostSkin("basic", "다정한 진행자", "haeun"),
        ),
        signals = signals,
        studio = object : StudioRepository {
            override suspend fun roster() = family.map { Guest(GuestId(it.id), it.nickname, it.seat, isChild = it.isChild) }
            override suspend fun lastEpisodeNumber() = 13
            override suspend fun saveEpisode(record: EpisodeRecord) = Unit
        },
    )

    @Test
    fun `last rider buckling during the belt call does not repeat it`() = runTest(dispatcher) {
        val signals = ManualSignalSource()
        val vm = OnAirViewModel(engine(signals), signals, profile = flowOf(FamilyProfileUi(family, qrText = null, lastEpisode = 13)))
        runCurrent()
        signals.emit(SignalEvent.DrivingChanged(driving = false))
        runCurrent()
        vm.onStartClick()
        runCurrent()

        // 녹화 순서(clipA): 엄마·누나 벨트 → 막내 앉음(안 맴) → 아빠 벨트 → 5초 뒤 막내 안내 → 막내 벨트
        signals.emit(SignalEvent.SeatChanged(Seat.FRONT_PASSENGER, occupied = true, buckled = true)); advanceTimeBy(1_300)
        signals.emit(SignalEvent.SeatChanged(Seat.REAR_LEFT, occupied = true, buckled = true)); advanceTimeBy(1_400)
        signals.emit(SignalEvent.SeatChanged(Seat.REAR_RIGHT, occupied = true, buckled = false)); advanceTimeBy(1_400)
        signals.emit(SignalEvent.SeatChanged(Seat.DRIVER, occupied = true, buckled = true)); advanceTimeBy(5_000)
        assertEquals(spoken.toString(), 1, spoken.count { "벨트를 매면" in it })

        // 안내를 읽는 도중 막내가 맸다 → 그 자리에서 안내가 멈춘다(남은 말·대기 줄 없음)
        signals.emit(SignalEvent.SeatChanged(Seat.REAR_RIGHT, occupied = true, buckled = true)); runCurrent()
        assertEquals(emptyList<String>(), playing)
        advanceTimeBy(LINE_MS * 2)
        assertEquals(spoken.toString(), 1, spoken.count { "벨트를 매면" in it })
        vm.viewModelScope.cancel()
    }

    private companion object {
        const val LINE_MS = 3_400L
    }
}
