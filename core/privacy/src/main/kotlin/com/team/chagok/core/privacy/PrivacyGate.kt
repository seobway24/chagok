package com.team.chagok.core.privacy

/**
 * 차 밖으로 나가는 유일한 길.
 *
 * 외부 LLM 같은 원격 카트리지는 HTTP 를 직접 부르지 않고 [egress] 에 전송 함수를 넘긴다.
 * 그래서 나간 바이트가 빠짐없이 [EgressLog] 에 남는다.
 */
class PrivacyGate(
    private val log: EgressLog,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun egress(
        engineId: String,
        maskedPayload: String,
        send: suspend (String) -> String,
    ): String {
        val response = send(maskedPayload)
        log.add(
            EgressRecord(
                atMillis = clock(),
                engineId = engineId,
                bytesOut = maskedPayload.utf8Size(),
                bytesIn = response.utf8Size(),
                maskedText = maskedPayload,
            ),
        )
        return response
    }

    private fun String.utf8Size() = toByteArray(Charsets.UTF_8).size
}
