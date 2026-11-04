package com.team.chagok.core.privacy

import com.team.chagok.core.quip.QuipEngine
import com.team.chagok.core.quip.QuipRequest
import com.team.chagok.core.quip.QuipResult

/**
 * 어떤 카트리지가 끼워져 있든 앞뒤로 개인정보·안전 처리를 강제하는 데코레이터.
 *
 * ```
 * mask(호칭 → 게스트N) → delegate → SafetyFilter → unmask(게스트N → 호칭)
 * ```
 *
 * 필터에서 걸리면 [fallback] (템플릿)으로 한 번 더 시도해 방송이 끊기지 않게 한다.
 */
class GuardedQuip(
    private val delegate: QuipEngine,
    private val fallback: QuipEngine,
    private val masker: Masker,
    private val filter: SafetyFilter,
    private val nicknames: suspend () -> List<String>,
) : QuipEngine {

    override val id: String = "guarded(${delegate.id})"

    override suspend fun quip(request: QuipRequest): QuipResult {
        val session = masker.open(nicknames())
        val masked = request.copy(
            speaker = session.mask(request.speaker),
            utterance = session.mask(request.utterance),
            question = session.mask(request.question),
        )
        val passed = delegate.quip(masked).filtered(masked)
            ?: fallback.quip(masked).filtered(masked)
            ?: return QuipResult.Unavailable(QuipResult.Reason.FILTERED)
        return passed.copy(text = session.unmask(passed.text))
    }

    private fun QuipResult.filtered(request: QuipRequest): QuipResult.Line? {
        if (this !is QuipResult.Line) return null
        val safe = filter.accept(text, request.maxChars, request.kidSafe, heard = request.utterance) ?: return null
        return copy(text = safe)
    }
}
