package com.team.chagok.core.quip

import com.team.chagok.core.model.SignalCategory

/**
 * 받아치기 한 줄을 만드는 블랙박스.
 *
 * 앱은 이 인터페이스만 안다. Pleos LLM · 외부 LLM · 템플릿은 모두 구현체(카트리지)이고
 * [ChainQuip] 으로 순서만 바꿔 끼운다.
 *
 * 계약 — 모든 구현체가 지킨다:
 * - 예외를 던지지 않는다. 실패는 [QuipResult.Unavailable] 로 돌려준다(취소는 예외).
 * - [QuipRequest.maxChars] 를 넘는 줄을 돌려주지 않는다.
 */
interface QuipEngine {
    val id: String
    suspend fun quip(request: QuipRequest): QuipResult
}

/**
 * @property speaker 출연자 호칭. 외부로 나가기 전에 GuardedQuip 이 `게스트N` 으로 바꾼다.
 * @property utterance 출연자의 짧은 답(STT 텍스트). 비어 있으면 무응답.
 * @property question 진행자가 던진 질문(호명 대사). 받아치기가 질문을 알아야 말이 이어진다.
 */
data class QuipRequest(
    val cornerId: String,
    val skinId: String,
    val speaker: String,
    val utterance: String,
    val signal: SignalCategory,
    val kidSafe: Boolean,
    val maxChars: Int = DEFAULT_MAX_CHARS,
    val question: String = "",
) {
    companion object {
        const val DEFAULT_MAX_CHARS = 60
    }
}

sealed interface QuipResult {
    data class Line(val text: String, val engineId: String, val latencyMs: Long) : QuipResult
    data class Unavailable(val reason: Reason) : QuipResult

    enum class Reason { TIMEOUT, OFFLINE, REFUSED, FILTERED, ERROR }
}
