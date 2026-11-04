package com.team.chagok.core.domain.signal

import com.team.chagok.core.domain.memory.CallbackPlanner
import com.team.chagok.core.domain.port.Heard
import com.team.chagok.core.domain.port.MicInput
import com.team.chagok.core.domain.show.ShowState
import com.team.chagok.core.model.Corner
import kotlin.time.Duration

/**
 * 리허설 대본. 지금 열린 코너와 그 회차에 실제로 나간 질문에 맞는 대답을 고른다. 같은 회차·코너면 늘 같은 대답이다.
 *
 * @param byCorner 코너 id → 대답. 순서는 그 코너의 질문 순서([Corner.callOut], 그다음 variants)와 같다.
 * @param callbacks 지난 회 이어 묻기 대답. 키는 그 정보가 나온 코너 id(예: "milestone").
 * @param farewell 엔딩의 운전자 하차 한마디.
 */
class RehearsalScript(
    private val byCorner: Map<String, List<String>>,
    private val callbacks: Map<String, String>,
    private val farewell: String,
) {
    fun answerFor(state: ShowState?): String? = when (state) {
        is ShowState.OnAir -> state.live?.let { answerFor(it.corner, state.episode) }
        is ShowState.Ending -> farewell
        else -> null
    }

    fun answerFor(corner: Corner, episode: Int): String? {
        CallbackPlanner.factKeyOf(corner.id)?.let { key -> return callbacks[topicOf(key)] }
        val pool = byCorner[corner.id] ?: return null
        val asked = Math.floorMod(episode, 1 + corner.variants.size)
        return pool.getOrNull(asked) ?: pool.firstOrNull()
    }

    /** FamilyFact.key = "guestId|cornerId|keyword". */
    private fun topicOf(factKey: String) = factKey.split('|').getOrNull(1).orEmpty()
}

/**
 * 음성 입력이 없는 에뮬레이터 리허설용. [now] 로 지금 방송 상태를 보고 대본에서 대답을 꺼낸다.
 * 대본에 없는 순간(이름 묻기 등)은 침묵. 출시 빌드에는 대본 자체가 들어가지 않는다(app 의 debug 소스).
 */
class RehearsalMic(
    private val script: RehearsalScript,
    private val now: () -> ShowState?,
) : MicInput {
    override suspend fun listen(window: Duration): Heard =
        script.answerFor(now())?.let { Heard.Text(it) } ?: Heard.Silence
}
