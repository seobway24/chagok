package com.team.chagok.feature.onair

import com.team.chagok.core.domain.show.Cue
import com.team.chagok.core.domain.show.ShowState
import com.team.chagok.core.model.Guest
import com.team.chagok.core.model.GuestId
import com.team.chagok.core.model.Seat
import com.team.chagok.core.model.SignalCategory
import com.team.chagok.core.model.VoiceClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnAirUiStateTest {

    private val driver = Guest(GuestId("g0"), "캡틴", Seat.DRIVER)
    private val onAir = ShowState.OnAir(episode = 1, roster = listOf(driver), startedAtMillis = 0)

    @Test
    fun `ending keeps the driving value so inputs stay closed`() {
        val ui = ShowState.Ending(onAir.copy(driving = true)).toUiState()

        assertTrue(ui.driving)
        assertEquals(SeatStatus.WAITING, ui.seats.single().status)
    }

    @Test
    fun `parked ending stays parked`() {
        assertFalse(ShowState.Ending(onAir.copy(driving = false)).toUiState().driving)
    }

    private val rainClip = VoiceClip("v1", 13, GuestId("g0"), "캡틴", recordedAtMillis = 0, durationMillis = 5_000, signal = SignalCategory.RAIN)
    private val opening = ShowState.Opening(episode = 14, roster = listOf(driver), startedAtMillis = 0)

    @Test
    fun `a rainy-day voice is offered as rainy only when it is raining in this episode`() {
        // 제14회: 비 신호가 아직 없다. 제13회 비 오는 날 목소리를 골랐어도 "오늘도 비가 와서" 라고 하면 안 된다
        val dry = ShowState.ReplayOffer(opening, rainClip).toUiState()
        assertFalse(dry.offer!!.rainy)

        val wet = ShowState.ReplayOffer(opening.copy(pending = listOf(Cue(SignalCategory.RAIN))), rainClip).toUiState()
        assertTrue(wet.offer!!.rainy)

        val plain = ShowState.ReplayOffer(opening.copy(pending = listOf(Cue(SignalCategory.RAIN))), rainClip.copy(signal = null)).toUiState()
        assertFalse(plain.offer!!.rainy)
    }

    @Test
    fun `idle headline tells the driver to park before starting`() {
        val driving = ShowState.Idle(driving = true).toUiState()
        val parked = ShowState.Idle(driving = false).toUiState()

        assertEquals(PARK_TO_START, driving.headline)
        assertNotEquals(PARK_TO_START, parked.headline)
    }

    @Test
    fun driveLine_showsOnlyTheSpeakerName() {
        val line = OnAirUiState(captionBy = "지호", caption = "아주 긴 대사", corner = "오늘의 한 단어").driveLine
        assertEquals("지호 듣는 중", line)
    }

    @Test
    fun driveLine_fallsBackToCornerThenHeadline() {
        assertEquals("오늘의 한 단어", OnAirUiState(caption = "진행자 말", corner = "오늘의 한 단어").driveLine)
        assertEquals("잠깐 쉴게요", OnAirUiState(headline = "잠깐 쉴게요").driveLine)
    }

    @Test
    fun coverTitle_dropsTheEpisodePrefix() {
        val recap = EpisodeRecapUi(3, "제3회, 엄마, 지호 함께 코너 2개를 했어요", emptyList(), emptyList())
        assertEquals("엄마, 지호 함께 코너 2개를 했어요", recap.coverTitle())
    }
}
