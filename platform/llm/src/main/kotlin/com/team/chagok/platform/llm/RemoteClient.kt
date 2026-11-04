package com.team.chagok.platform.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 외부 LLM 한 번 묻고 글만 받는다. 받아치기([RemoteQuip])·정보 뽑기([RemoteFactExtractor])가 같이 쓴다.
 *
 * 키는 헤더로만 나가고 로그·예외 메시지에 남기지 않는다. 실패하면 예외 — 붙이는 쪽이 대체 경로로 넘긴다.
 */
class RemoteClient(private val config: RemoteConfig) {

    suspend fun complete(prompt: String, maxTokens: Int): String = withContext(Dispatchers.IO) {
        val messages = JSONArray().put(JSONObject().put("role", "user").put("content", prompt))
        when (config.provider) {
            "anthropic" -> post(
                "${config.baseUrl}/messages",
                mapOf("x-api-key" to config.apiKey, "anthropic-version" to "2023-06-01"),
                JSONObject().put("model", config.model).put("max_tokens", maxTokens).put("messages", messages),
            ).getJSONArray("content").getJSONObject(0).getString("text")

            else -> post(
                "${config.baseUrl}/chat/completions",
                mapOf("Authorization" to "Bearer ${config.apiKey}"),
                JSONObject().put("model", config.model).put("max_tokens", maxTokens).put("messages", messages),
            ).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
        }
    }

    private fun post(url: String, headers: Map<String, String>, body: JSONObject): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = config.timeoutMs
            conn.readTimeout = config.timeoutMs
            conn.doOutput = true
            conn.setRequestProperty("content-type", "application/json")
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            // 응답 본문에는 키가 없다. 상태 코드와 앞부분만 남긴다
            if (code !in 200..299) error("HTTP $code ${text.take(200)}")
            return JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }
}
