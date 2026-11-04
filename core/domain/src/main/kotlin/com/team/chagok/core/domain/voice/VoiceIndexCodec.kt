package com.team.chagok.core.domain.voice

import com.team.chagok.core.model.GuestId
import com.team.chagok.core.model.SignalCategory
import com.team.chagok.core.model.VoiceClip
import com.team.chagok.core.model.VoiceShelfState
import java.util.Base64

/**
 * 목소리 색인 ↔ 글. 암호화는 저장하는 쪽이 한다. 이름에 탭이 들어가도 깨지지 않게 이름만 Base64 로 둔다.
 *
 * ```
 * chagok-voices v1
 * C	id	회차	사람id	이름(Base64)	녹음시각	길이ms	상황	재생횟수
 * M	사람id
 * ```
 * 머리글이 없거나 칸 수가 틀리면 예외를 던진다. 빈 목록으로 넘기면 다음 저장이 목록을 덮는다.
 */
object VoiceIndexCodec {

    private const val HEADER = "chagok-voices v1"
    private const val CLIP_FIELDS = 9

    fun encode(shelf: VoiceShelfState): String = buildString {
        appendLine(HEADER)
        shelf.clips.forEach { appendLine(encodeClip(it)) }
        shelf.muted.forEach { appendLine("M\t${it.value}") }
    }

    fun decode(text: String): VoiceShelfState {
        val lines = text.lines().filter(String::isNotEmpty)
        require(lines.firstOrNull() == HEADER) { "색인 머리글 없음" }
        val body = lines.drop(1).map { it.split('\t') }
        return VoiceShelfState(
            clips = body.filter { it[0] == "C" }.map(::decodeClip),
            muted = body.filter { it[0] == "M" }.map { GuestId(it[1]) }.toSet(),
        )
    }

    /** 소리 파일 머리글로도 쓴다. 색인이 저장되기 전에 끊겨도 이 줄로 되살린다. */
    fun encodeClip(c: VoiceClip): String = listOf(
        "C", c.id, c.episode, c.guestId.value, encodeName(c.guestName),
        c.recordedAtMillis, c.durationMillis, c.signal?.name.orEmpty(), c.playCount,
    ).joinToString("\t")

    fun decodeClip(line: String): VoiceClip = decodeClip(line.split('\t'))

    private fun decodeClip(f: List<String>): VoiceClip {
        require(f.size == CLIP_FIELDS && f[0] == "C") { "색인 줄 형식 오류" }
        return VoiceClip(
            id = f[1],
            episode = f[2].toInt(),
            guestId = GuestId(f[3]),
            guestName = String(Base64.getDecoder().decode(f[4]), Charsets.UTF_8),
            recordedAtMillis = f[5].toLong(),
            durationMillis = f[6].toLong(),
            signal = f[7].takeIf(String::isNotEmpty)?.let(SignalCategory::valueOf),
            playCount = f[8].toInt(),
        )
    }

    private fun encodeName(name: String) = Base64.getEncoder().encodeToString(name.toByteArray(Charsets.UTF_8))
}
