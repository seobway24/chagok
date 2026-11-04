package com.team.chagok.core.domain.memory

import com.team.chagok.core.model.Corner
import com.team.chagok.core.model.FamilyFact
import com.team.chagok.core.model.Guest
import com.team.chagok.core.model.ProfileBook
import com.team.chagok.core.model.Seat
import com.team.chagok.core.model.SignalCategory

/**
 * 지난 회 대답으로 이번 회 "이어 묻기" 코너를 만든다.
 *
 * AI 가 하는 일은 정보 뽑기까지다. 질문 틀은 [templates] 로 고정이라 코너 규칙(운전자는 정차 중에만 등)을 못 바꾼다.
 *
 * @param templates 정보가 나온 코너 id → 호명 대사. `{guest}` `{keyword}` 가 채워진다. `{keyword}` 자리는 이름 꼴("…은 {keyword}!").
 * @param quoteTemplates 대답이 이름이 아니라 문장일 때 쓰는 틀. 지난 대답을 따옴표로 그대로 옮긴다("…기억나요? '{keyword}'.").
 *  이 틀이 없는 코너의 문장 대답은 꺼내지 않는다 — "대답은 죽 끓여 준!" 처럼 어색하게 말하느니 안 묻는다.
 * @param freshFor 몇 회 전 정보까지 꺼내나. 오래된 얘기를 계속 꺼내면 지루하다.
 * @param accept 완성된 대사 검사(금칙어 등). false 면 버린다.
 */
class CallbackPlanner(
    private val templates: Map<String, String>,
    private val maxPerEpisode: Int = 3,
    private val freshFor: Int = 2,
    private val accept: (String) -> Boolean = { true },
    private val quoteTemplates: Map<String, String> = emptyMap(),
) {

    fun plan(book: ProfileBook, roster: List<Guest>, episode: Int): List<Corner> {
        // 최근 회차 먼저, 같은 회차면 [templates] 순서(앞 코너 정보가 더 이야깃거리)
        val priority = templates.keys.withIndex().associate { it.value to it.index }
        val fresh = book.facts
            .filter { it.key !in book.calledBack && it.cornerId in templates && episode - it.episode in 1..freshFor }
            .sortedWith(compareByDescending<FamilyFact> { it.episode }.thenBy { priority.getValue(it.cornerId) })
            .groupBy { it.guestId }
        // 동승자 먼저, 운전자는 마지막(정차 중에만 열리니 뒤에 둬도 손해가 없다). 한 사람씩 돌아가며
        val queues = roster.sortedBy { it.seat == Seat.DRIVER }.mapNotNull { guest -> fresh[guest.id]?.let { guest to it } }
        val picked = (0 until (queues.maxOfOrNull { it.second.size } ?: 0))
            .flatMap { round -> queues.mapNotNull { (guest, facts) -> facts.getOrNull(round)?.let { guest to it } } }

        return picked.mapNotNull { (guest, fact) -> cornerFor(guest, fact) }.take(maxPerEpisode)
    }

    private fun cornerFor(guest: Guest, fact: FamilyFact): Corner? {
        val keyword = fact.keyword.trim().replace("'", "")
        val template = if (readsAsNoun(keyword)) templates.getValue(fact.cornerId) else quoteTemplates[fact.cornerId] ?: return null
        val callOut = template.replace("{keyword}", keyword)
        if (!accept(callOut.replace("{guest}", guest.nickname))) return null
        return Corner(
            id = PREFIX + fact.key,
            title = TITLE,
            trigger = SignalCategory.QUIET,
            callOut = callOut,
            needsDriver = guest.seat == Seat.DRIVER,
            forGuest = guest.id,
        )
    }

    companion object {
        const val PREFIX = "callback:"
        const val TITLE = "지난 회 이어서"

        /** 이어 묻기 코너 id → 그 코너가 꺼낸 정보의 key. 이어 묻기 코너가 아니면 null. */
        fun factKeyOf(cornerId: String): String? = cornerId.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)

        /**
         * "짜장면", "강릉 바다" 처럼 이름 칸에 들어가도 말이 되나. 끝 낱말이 서술어 꼴(…요, …했다, …준, …보러)이거나
         * 잘린 대답(…)이면 문장으로 본다. 이름을 문장으로 잘못 보면 따옴표로 옮길 뿐이라 그쪽으로 기운다.
         */
        fun readsAsNoun(keyword: String): Boolean {
            val text = keyword.trim()
            if (text.isEmpty() || text.endsWith("…")) return false
            val last = text.substringAfterLast(' ')
            return CLAUSE_ENDINGS.none { last.endsWith(it) }
        }

        private val CLAUSE_ENDINGS = listOf(
            "요", "죠", "니다", "었다", "았다", "했다", "였다", "겠다", "는다", "한다", "된다",
            "해", "어", "아", "야", "네", "까", "지만", "는데", "면서",
            "준", "한", "는", "던", "은", "운", "된", "러", "려", "서", "고",
        )
    }
}
