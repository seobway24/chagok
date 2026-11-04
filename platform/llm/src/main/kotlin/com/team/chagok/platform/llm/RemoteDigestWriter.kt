package com.team.chagok.platform.llm

import android.util.Log
import com.team.chagok.core.domain.memory.DigestPrompt
import com.team.chagok.core.domain.memory.DigestWriter
import com.team.chagok.core.domain.memory.FactPrompt
import com.team.chagok.core.model.EpisodeDigest
import com.team.chagok.core.model.EpisodeRecord
import com.team.chagok.core.model.Guest
import com.team.chagok.core.model.ProfileBook
import com.team.chagok.core.privacy.Masker
import com.team.chagok.core.privacy.PrivacyGate

/**
 * 회차가 끝나면 외부 LLM 에 정리를 맡긴다. 호칭은 게스트N 으로 가려서 한 번 보내고, 답이 검사를 못 넘으면 한 번 더 묻는다.
 * 두 번 다 안 되면 null — 이미 저장된 규칙 정리가 남는다.
 *
 * @param accept 요약·카드 문장 안전 검사(금칙어 등).
 */
class RemoteDigestWriter(
    private val settings: () -> RemoteConfig?,
    private val gate: PrivacyGate,
    private val accept: (String) -> Boolean,
) : DigestWriter {

    override suspend fun write(record: EpisodeRecord, roster: List<Guest>, book: ProfileBook): EpisodeDigest? {
        val config = settings() ?: return null
        val session = Masker().open(roster.map { it.nickname })
        val names = roster.associate { it.id to it.nickname }
        val lines = record.answers.mapIndexed { i, a ->
            FactPrompt.line(i, session.mask(names[a.guestId].orEmpty()), a.cornerId, session.mask(a.text))
        }
        val known = book.portraits.filter { it.guestId in names }.map {
            session.mask("${names[it.guestId]}|${it.label}|${it.value}")
        }
        val prompt = DigestPrompt.build(lines, known)
        repeat(ATTEMPTS) { attempt ->
            val output = try {
                gate.egress(ID, prompt) { RemoteClient(config.withTimeout(TIMEOUT_MS)).complete(it, MAX_TOKENS) }
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "회차 정리 실패(${attempt + 1}회째): ${e.javaClass.simpleName}")
                return@repeat
            }
            DigestPrompt.parse(output, record, roster, session::unmask, accept)?.let { return it }
            Log.w(TAG, "회차 정리 검사 탈락(${attempt + 1}회째)")
        }
        return null
    }

    companion object {
        const val ID = "remote-digest"
        private const val TAG = "ChagokMemory"
        private const val ATTEMPTS = 2
        private const val MAX_TOKENS = 700
        private const val TIMEOUT_MS = 30_000
    }
}
