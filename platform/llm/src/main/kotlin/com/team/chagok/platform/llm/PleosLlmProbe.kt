package com.team.chagok.platform.llm

import ai.pleos.playground.llm.LLM
import ai.pleos.playground.llm.constant.Mode
import ai.pleos.playground.llm.listener.OnServerConnectionListener
import ai.pleos.playground.llm.listener.ResultListener
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * 디버그 빌드 전용: 이 기기에서 Pleos LLM 이 실제로 답하는지 모드별로 확인해 로그로 남긴다.
 *
 * `adb logcat -s ChagokLlmProbe` 로 본다. 자격증명 값은 찍지 않는다.
 *
 * [enabled] 일 때만 돈다: `adb shell setprop debug.chagok.llm_probe 1`. 에뮬레이터에선 ON_DEVICE 가 30초씩 세 번
 * 무응답, SERVER 가 UNKNOWN 오류를 내며 앱을 켤 때마다 2분 가까이 방송과 같은 LLM 서비스를 붙잡았다(11/03 logcat).
 */
object PleosLlmProbe {

    private const val TAG = "ChagokLlmProbe"
    private const val PROMPT = "한국어 한 문장으로 짧게 인사해 줘."

    fun enabled(): Boolean = runCatching {
        val sp = Class.forName("android.os.SystemProperties")
        sp.getMethod("get", String::class.java, String::class.java).invoke(null, "debug.chagok.llm_probe", "0") == "1"
    }.getOrDefault(false)

    suspend fun run(context: Context, credentials: PleosCredentials?) {
        Log.i(TAG, "start credentials=${credentials != null}")
        for (mode in listOf(Mode.ON_DEVICE, Mode.SERVER)) probe(context, mode, credentials)
        Log.i(TAG, "done")
    }

    private suspend fun probe(context: Context, mode: Mode, credentials: PleosCredentials?) {
        val llm = LLM(context.applicationContext, mode)
        try {
            llm.initialize()
            Log.i(TAG, "[$mode] initialize ok")
            if (mode == Mode.SERVER) {
                if (credentials == null) {
                    Log.w(TAG, "[$mode] skip: no credentials")
                    return
                }
                val connected = CompletableDeferred<String>()
                llm.registerApp(credentials.clientId, credentials.clientSecret, object : OnServerConnectionListener {
                    override fun onConnected() { connected.complete("connected") }
                    override fun onFailed(message: String) { connected.complete("failed: $message") }
                })
                Log.i(TAG, "[$mode] registerApp -> ${withTimeoutOrNull(15.seconds) { connected.await() } ?: "timeout"}")
            }
            // 서비스 바인딩이 비동기일 수 있어 몇 번 다시 시도한다
            repeat(3) { attempt ->
                if (generateOnce(llm, mode, attempt)) return
                delay(3.seconds)
            }
        } catch (e: Exception) {
            Log.e(TAG, "[$mode] exception ${e.javaClass.simpleName}: ${e.message}")
        } finally {
            runCatching { llm.release() }
        }
    }

    private suspend fun generateOnce(llm: LLM, mode: Mode, attempt: Int): Boolean {
        val started = TimeSource.Monotonic.markNow()
        val result = CompletableDeferred<Boolean>()
        llm.generateContent(PROMPT, object : ResultListener {
            override fun onResponse(text: String, isFinal: Boolean) {
                Log.i(TAG, "[$mode#$attempt] onResponse final=$isFinal ${started.elapsedNow().inWholeMilliseconds}ms text=${text.take(200)}")
                if (isFinal) result.complete(true)
            }

            override fun onError(message: String) {
                Log.w(TAG, "[$mode#$attempt] onError ${started.elapsedNow().inWholeMilliseconds}ms $message")
                result.complete(false)
            }
        })
        return withTimeoutOrNull(30.seconds) { result.await() }
            ?: false.also { Log.w(TAG, "[$mode#$attempt] timeout 30s") }
    }
}
