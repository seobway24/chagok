package com.team.chagok.core.model

/**
 * 사용자가 정차 화면에서 넣는 외부 LLM 설정. APK 안에는 키가 없다.
 *
 * @property provider `anthropic` 또는 `openai`(호환 서버 포함)
 * @property baseUrl 비어 있으면 서비스 기본 주소
 */
data class AiSettings(
    val provider: String,
    val apiKey: String,
    val model: String,
    val baseUrl: String = "",
) {
    /** 키가 로그·화면에 새지 않게 가린다. */
    override fun toString() = "AiSettings($provider, $model, $baseUrl, key=***)"
}
