package com.team.chagok.platform.llm

import android.util.Log
import com.team.chagok.core.domain.memory.FactExtractor
import com.team.chagok.core.domain.memory.FactPrompt
import com.team.chagok.core.model.Answer
import com.team.chagok.core.model.FamilyFact
import com.team.chagok.core.model.Guest
import com.team.chagok.core.privacy.Masker
import com.team.chagok.core.privacy.PrivacyGate

/**
 * 외부 LLM 으로 회차 대답에서 가족 정보를 뽑는다. 호칭은 게스트N 으로 가려서 보내고 [PrivacyGate] 를 거친다.
 *
 * 실패하면 빈 목록 — 뒤에 붙은 규칙 추출이 이어받는다. 지어낸 정보는 FactCheck 가 버린다.
 */
class RemoteFactExtractor(
    private val settings: () -> RemoteConfig?,
    private val gate: PrivacyGate,
    private val roster: suspend () -> List<Guest>,
) : FactExtractor {

    override suspend fun extract(answers: List<Answer>, episode: Int): List<FamilyFact> {
        if (answers.isEmpty()) return emptyList()
        val config = settings() ?: return emptyList()
        val guests = roster()
        val session = Masker().open(guests.map { it.nickname })
        val names = guests.associate { it.id to it.nickname }
        val lines = answers.mapIndexed { i, a ->
            FactPrompt.line(i, session.mask(names[a.guestId].orEmpty()), a.cornerId, session.mask(a.text))
        }
        val output = try {
            gate.egress(ID, FactPrompt.build(lines)) { RemoteClient(config).complete(it, MAX_TOKENS) }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "정보 뽑기 실패 → 규칙으로: ${e.javaClass.simpleName}")
            return emptyList()
        }
        return FactPrompt.parse(output, answers, episode, session::unmask)
    }

    companion object {
        const val ID = "remote-facts"
        private const val TAG = "ChagokMemory"
        private const val MAX_TOKENS = 400
    }
}
