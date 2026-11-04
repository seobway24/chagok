package com.team.chagok.core.domain.voice

import com.team.chagok.core.domain.port.RecordedVoice
import com.team.chagok.core.domain.port.VoicePlayer
import com.team.chagok.core.domain.port.VoiceRecorder
import com.team.chagok.core.domain.port.VoiceVault
import com.team.chagok.core.domain.show.ShowState
import com.team.chagok.core.model.Guest
import com.team.chagok.core.model.GuestId
import com.team.chagok.core.model.Seat
import com.team.chagok.core.model.VoiceClip
import com.team.chagok.core.model.VoiceShelfState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration

class VoiceDeskTest {

    private val dad = Guest(GuestId("g0"), "아빠", Seat.DRIVER)
    private val onAir = ShowState.OnAir(episode = 3, roster = listOf(dad), startedAtMillis = 0)

    private class FakeVault : VoiceVault {
        val clips = mutableListOf<VoiceClip>()
        var unreadable = false
        var free = Long.MAX_VALUE
        var addWorks = true
        var played = 0

        override suspend fun shelf() = VoiceShelfState(clips.toList(), emptySet(), unreadable)
        override suspend fun add(clip: VoiceClip, audio: ByteArray) = addWorks.also { if (it) clips += clip }
        override suspend fun read(id: String) = byteArrayOf(1).takeIf { clips.any { c -> c.id == id } }
        override suspend fun markPlayed(id: String) { played++ }
        override suspend fun delete(id: String) { clips.removeAll { it.id == id } }
        override suspend fun deleteAll() = clips.clear()
        override suspend fun setMuted(guest: GuestId, muted: Boolean) = Unit
        override suspend fun freeBytes() = free
    }

    private class FakeRecorder(var result: RecordedVoice? = RecordedVoice(byteArrayOf(1), 8_000)) : VoiceRecorder {
        var calls = 0
        override suspend fun record(max: Duration): RecordedVoice? = result.also { calls++ }
        override fun cancel() = Unit
    }

    private class FakePlayer(val works: Boolean = true) : VoicePlayer {
        override suspend fun play(bytes: ByteArray) = works
        override fun stop() = Unit
    }

    private val vault = FakeVault()
    private val recorder = FakeRecorder()

    private fun desk(consent: Boolean = true, player: VoicePlayer = FakePlayer()) =
        VoiceDesk(vault, recorder, player, { consent }, { "id${vault.clips.size}" }, { 1L })

    @Test
    fun `동의가 없으면 남길 수 없고 제안도 없다`() = runTest {
        desk().keep(onAir, dad, null)
        val refused = desk(consent = false)
        assertFalse(refused.canKeep())
        assertNull(refused.offer(null))
    }

    @Test
    fun `공간이 없으면 녹음을 시작하지 않는다`() = runTest {
        vault.free = 0
        val result = desk().keep(onAir, dad, null)
        assertTrue(result.full)
        assertEquals(0, recorder.calls)
    }

    @Test
    fun `목록을 못 읽으면 저장하지 않고 제안하지 않는다`() = runTest {
        desk().keep(onAir, dad, null)
        vault.unreadable = true
        assertTrue(desk().keep(onAir, dad, null).full)
        assertNull(desk().offer(null))
        assertEquals(1, recorder.calls)
    }

    @Test
    fun `녹음이 실패하거나 취소되면 저장하지 않는다`() = runTest {
        recorder.result = null
        assertNull(desk().keep(onAir, dad, null).clip)
        assertTrue(vault.clips.isEmpty())
    }

    @Test
    fun `저장에 실패하면 결과를 내지 않는다`() = runTest {
        vault.addWorks = false
        assertNull(desk().keep(onAir, dad, null).clip)
    }

    @Test
    fun `저장되면 회차와 사람을 기록하고 끝까지 들어야 들은 횟수가 는다`() = runTest {
        val clip = assertNotNullClip(desk().keep(onAir, dad, null).clip)
        assertEquals(3, clip.episode)
        assertEquals("아빠", clip.guestName)
        desk(player = FakePlayer(works = false)).play(clip)
        assertEquals(0, vault.played)
        desk().play(clip)
        assertEquals(1, vault.played)
    }

    private fun assertNotNullClip(clip: VoiceClip?): VoiceClip {
        assertNotNull(clip)
        return clip!!
    }
}
