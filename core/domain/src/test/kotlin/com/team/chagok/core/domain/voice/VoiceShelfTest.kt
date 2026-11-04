package com.team.chagok.core.domain.voice

import com.team.chagok.core.model.GuestId
import com.team.chagok.core.model.SignalCategory
import com.team.chagok.core.model.VoiceClip
import com.team.chagok.core.model.VoiceShelfState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceShelfTest {

    private val little = GuestId("g-little")
    private val mom = GuestId("g-mom")

    private fun clip(id: String, ep: Int, who: GuestId = little, signal: SignalCategory? = null, plays: Int = 0) =
        VoiceClip(id, ep, who, "막내\t이름", ep * 1000L, 8000, signal, plays)

    @Test
    fun `same weather comes first`() {
        val shelf = VoiceShelfState(listOf(clip("a", 1), clip("b", 5, signal = SignalCategory.RAIN)))
        assertEquals("b", VoiceShelf.pick(shelf, SignalCategory.RAIN)?.id)
    }

    @Test
    fun `first episode is offered before it was ever played, then the latest`() {
        val fresh = VoiceShelfState(listOf(clip("a", 1), clip("b", 5)))
        assertEquals("a", VoiceShelf.pick(fresh, null)?.id)

        val played = VoiceShelfState(listOf(clip("a", 1, plays = 1), clip("b", 5)))
        assertEquals("b", VoiceShelf.pick(played, null)?.id)
    }

    @Test
    fun `muted person is never offered`() {
        val shelf = VoiceShelfState(listOf(clip("a", 1, mom)), muted = setOf(mom))
        assertNull(VoiceShelf.pick(shelf, null))
    }

    @Test
    fun `no room when full or low on space`() {
        assertTrue(VoiceShelf.hasRoom(4_999, VoiceShelf.MIN_FREE_BYTES))
        assertFalse(VoiceShelf.hasRoom(5_000, VoiceShelf.MIN_FREE_BYTES))
        assertFalse(VoiceShelf.hasRoom(0, VoiceShelf.MIN_FREE_BYTES - 1))
    }

    @Test
    fun `index survives a round trip`() {
        val shelf = VoiceShelfState(listOf(clip("a", 1, signal = SignalCategory.TUNNEL, plays = 2), clip("b", 2)), setOf(mom))
        assertEquals(shelf, VoiceIndexCodec.decode(VoiceIndexCodec.encode(shelf)))
    }

    @Test
    fun `broken index throws instead of looking empty`() {
        assertThrows(IllegalArgumentException::class.java) { VoiceIndexCodec.decode("garbage") }
        assertThrows(IllegalArgumentException::class.java) { VoiceIndexCodec.decode("chagok-voices v1\nC\tonly") }
    }
}
