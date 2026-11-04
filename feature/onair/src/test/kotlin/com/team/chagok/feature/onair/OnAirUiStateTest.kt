package com.team.chagok.feature.onair

import com.team.chagok.core.domain.show.ShowState
import com.team.chagok.core.model.Guest
import com.team.chagok.core.model.GuestId
import com.team.chagok.core.model.Seat
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
