package com.team.chagok.platform.llm

import android.util.Log
import com.team.chagok.core.privacy.PrivacyGate
import com.team.chagok.core.quip.QuipEngine
import com.team.chagok.core.quip.QuipRequest
import com.team.chagok.core.quip.QuipResult
import kotlin.time.TimeSource

/**
 * 외부 LLM 설정. 정차 화면 "AI 설정"에서 넣은 값이 먼저이고,
 * debug 빌드는 chagok/.env 의 AI_PROVIDER · AI_API_KEY · AI_MODEL · AI_BASE_URL 을 기본값으로 쓴다.
 *
 * - `anthropic` : Claude Messages API
 * - `openai`    : OpenAI 호환 Chat Completions. [baseUrl] 만 바꾸면 Gemini 등 호환 서버도 된다
 */
class RemoteConfig(
    val provider: String,
    val apiKey: String,
    val model: String,
    val baseUrl: String,
    /** 연결·읽기 각각의 제한. 로컬 중계(claude -p)는 몇 초씩 걸려 길게 잡는다 */
    val timeoutMs: Int = DEFAULT_TIMEOUT_MS,
) {
    fun withTimeout(ms: Int) = RemoteConfig(provider, apiKey, model, baseUrl, ms)

    override fun toString() = "RemoteConfig($provider, $model, $baseUrl, ${timeoutMs}ms, key=***)"

    companion object {
        const val DEFAULT_TIMEOUT_MS = 4_000

        /** 값이 모자라면 null — 카트리지를 붙이지 않는다. */
        fun of(provider: String, apiKey: String, model: String, baseUrl: String, timeoutMs: Long = 0): RemoteConfig? {
            val p = provider.trim().lowercase()
            if (p !in DEFAULT_BASE || apiKey.isBlank() || model.isBlank()) return null
            return RemoteConfig(
                p, apiKey.trim(), model.trim(),
                baseUrl.trim().ifEmpty { DEFAULT_BASE.getValue(p) },
                timeoutMs.toInt().takeIf { it > 0 } ?: DEFAULT_TIMEOUT_MS,
            )
        }

        private val DEFAULT_BASE = mapOf(
            "anthropic" to "https://api.anthropic.com/v1",
            "openai" to "https://api.openai.com/v1",
        )
    }
}

/**
 * 외부 LLM 카트리지 ("remote"). 나가는 글은 반드시 [PrivacyGate] 를 거친다.
 *
 * 이름은 앞단 GuardedQuip 이 이미 `게스트N` 으로 바꿔서 들어온다.
 * 계약대로 예외를 던지지 않는다(취소는 예외).
 */
class RemoteQuip(
    private val settings: () -> RemoteConfig?,
    private val gate: PrivacyGate,
) : QuipEngine {

    override val id: String = ID

    override suspend fun quip(request: QuipRequest): QuipResult {
        // 설정은 방송 사이에 정차 화면에서 바뀔 수 있어 부를 때마다 읽는다
        val config = settings() ?: return QuipResult.Unavailable(QuipResult.Reason.OFFLINE)
        val started = TimeSource.Monotonic.markNow()
        val prompt = PromptBook.reaction(request)
        val text = try {
            gate.egress(id, prompt) { RemoteClient(config).complete(it, MAX_TOKENS) }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "${config.provider} 호출 실패: ${e.javaClass.simpleName}")
            return QuipResult.Unavailable(QuipResult.Reason.OFFLINE)
        }
        val line = PromptBook.clean(text, request.maxChars)
        if (line.isEmpty()) return QuipResult.Unavailable(QuipResult.Reason.ERROR)
        return QuipResult.Line(line, id, started.elapsedNow().inWholeMilliseconds)
    }

    companion object {
        const val ID = "remote"
        private const val TAG = "ChagokRemote"
        private const val MAX_TOKENS = 120
    }
}
