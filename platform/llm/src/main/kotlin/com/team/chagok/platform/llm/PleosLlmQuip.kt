package com.team.chagok.platform.llm

import ai.pleos.playground.llm.LLM
import ai.pleos.playground.llm.constant.Mode
import ai.pleos.playground.llm.listener.OnServerConnectionListener
import ai.pleos.playground.llm.listener.ResultListener
import android.content.Context
import android.util.Log
import com.team.chagok.core.quip.QuipEngine
import com.team.chagok.core.quip.QuipRequest
import com.team.chagok.core.quip.QuipResult
import kotlinx.coroutines.CompletableDeferred
import kotlin.time.TimeSource

/** Pleos My Project 의 앱 자격증명. 값은 로그에 남기지 않는다. */
class PleosCredentials(val clientId: String, val clientSecret: String) {
    override fun toString() = "PleosCredentials(***)"
}

/**
 * Pleos LLM SDK → [QuipEngine] 카트리지 ("pleos-llm").
 *
 * SDK 는 차 안의 시스템 서비스(ai.pleos.playground.caas)에 붙어 동작한다.
 * SERVER 모드는 [PleosCredentials] 로 registerApp 해야 쓸 수 있다.
 * 계약대로 예외를 던지지 않고 실패는 [QuipResult.Unavailable] 로 돌려준다.
 */
class PleosLlmQuip(
    context: Context,
    server: Boolean,
    credentials: PleosCredentials?,
) : QuipEngine {

    override val id: String = ID

    private val mode = if (server) Mode.SERVER else Mode.ON_DEVICE
    private val llm = LLM(context.applicationContext, mode)
    private val ready = CompletableDeferred<Boolean>()

    init {
        runCatching {
            // 차에 LLM 서비스가 없으면(에뮬레이터 등) 붙지 않고 바로 비켜 준다 → 체인이 기다림 없이 다음 엔진으로
            if (!serviceInstalled(context)) {
                Log.i(TAG, "LLM 서비스 없음 — 건너뜀")
                ready.complete(false)
                return@runCatching
            }
            llm.initialize()
            if (mode == Mode.SERVER) register(credentials) else ready.complete(true)
        }.onFailure {
            Log.e(TAG, "LLM 초기화 실패", it)
            ready.complete(false)
        }
    }

    override suspend fun quip(request: QuipRequest): QuipResult {
        if (!ready.await()) return QuipResult.Unavailable(QuipResult.Reason.OFFLINE)
        val started = TimeSource.Monotonic.markNow()
        val text = generate(PromptBook.reaction(request)) ?: return QuipResult.Unavailable(QuipResult.Reason.ERROR)
        val line = PromptBook.clean(text, request.maxChars)
        if (line.isEmpty()) return QuipResult.Unavailable(QuipResult.Reason.ERROR)
        return QuipResult.Line(line, id, started.elapsedNow().inWholeMilliseconds)
    }

    fun release() = runCatching { llm.release() }

    private fun register(credentials: PleosCredentials?) {
        if (credentials == null) {
            Log.w(TAG, "SERVER 모드인데 자격증명 없음")
            ready.complete(false)
            return
        }
        llm.registerApp(credentials.clientId, credentials.clientSecret, object : OnServerConnectionListener {
            override fun onConnected() { ready.complete(true) }
            override fun onFailed(message: String) {
                Log.w(TAG, "registerApp 실패: $message")
                ready.complete(false)
            }
        })
    }

    /** 응답 한 번을 기다린다. SDK 가 여러 번 불러도 첫 최종 응답만 쓴다. */
    private suspend fun generate(prompt: String): String? {
        val answer = CompletableDeferred<String?>()
        runCatching {
            llm.generateContent(prompt, object : ResultListener {
                override fun onResponse(text: String, isFinal: Boolean) {
                    if (isFinal) answer.complete(text)
                }

                override fun onError(message: String) {
                    Log.w(TAG, "generateContent 오류: $message")
                    answer.complete(null)
                }
            })
        }.onFailure {
            Log.e(TAG, "generateContent 호출 실패", it)
            answer.complete(null)
        }
        return answer.await()
    }

    companion object {
        const val ID = "pleos-llm"
        private const val TAG = "ChagokLlm"

        /** LLM SDK 가 붙는 차 안 시스템 서비스. */
        private const val SERVICE_PACKAGE = "ai.pleos.playground.caas"

        @Suppress("DEPRECATION")
        private fun serviceInstalled(context: Context): Boolean =
            runCatching { context.packageManager.getPackageInfo(SERVICE_PACKAGE, 0) }.isSuccess
    }
}
