package com.team.chagok.data.studio

import android.content.Context
import android.util.Log
import com.team.chagok.core.model.AiSettings
import org.json.JSONObject

/** 외부 LLM 설정을 차 안에만 암호화해 저장한다. 읽기에 실패하면 설정 없음으로 본다 — 진행자는 준비된 대사로 간다. */
class AiSettingsStore(context: Context) {

    private val file = KeystoreFile(context, FILE_NAME, ALIAS)

    fun load(): AiSettings? = runCatching {
        file.read()?.toSettings()
    }.onFailure {
        Log.w(TAG, "AI 설정 읽기 실패: ${it.javaClass.simpleName}")
    }.getOrNull()

    fun save(settings: AiSettings) {
        runCatching { file.write(settings.toJson().toString()) }
            .onFailure { Log.w(TAG, "AI 설정 저장 실패: ${it.javaClass.simpleName}") }
    }

    fun clear() = file.delete()

    private fun AiSettings.toJson() = JSONObject()
        .put(PROVIDER, provider).put(API_KEY, apiKey).put(MODEL, model).put(BASE_URL, baseUrl)

    private fun String.toSettings() = JSONObject(this).run {
        AiSettings(getString(PROVIDER), getString(API_KEY), getString(MODEL), optString(BASE_URL))
    }

    private companion object {
        const val TAG = "ChagokAi"
        const val FILE_NAME = "ai-settings.bin"
        const val ALIAS = "chagok-ai-settings"
        const val PROVIDER = "provider"
        const val API_KEY = "apiKey"
        const val MODEL = "model"
        const val BASE_URL = "baseUrl"
    }
}
