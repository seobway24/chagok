package com.team.chagok.core.domain.memory

import com.team.chagok.core.model.EpisodeSummary
import com.team.chagok.core.model.Guest
import com.team.chagok.core.model.ProfileBook

/** 화면·QR 에 보여 줄 한 사람 몫. [lines] 는 "꿈 인터뷰 · 공룡 박사" 처럼 최신순. */
data class ProfileCard(val nickname: String, val lines: List<String>)

object ProfileCards {

    /**
     * 정보가 하나라도 있는 사람만. 정리된 줄이 먼저, 그다음 코너에서 나온 말.
     * 이어 묻기에서 나온 정보는 원래 코너 이름에 "이어서" 를 붙인다. 같은 말은 한 번만 보여 준다.
     */
    fun of(
        book: ProfileBook,
        roster: List<Guest>,
        cornerTitles: Map<String, String>,
    ): List<ProfileCard> = roster.mapNotNull { guest ->
        val portraits = book.portraits.filter { it.guestId == guest.id }.asReversed()
        val facts = book.factsOf(guest.id).asReversed()
            .filter { fact -> portraits.none { fact.keyword in it.value } }
            .distinctBy { it.keyword }
        (portraits.map { "${it.label} · ${it.value}" } + facts.map { "${titleOf(it.cornerId, cornerTitles)} · ${it.keyword}" })
            .takeIf { it.isNotEmpty() }
            ?.let { ProfileCard(guest.nickname, it) }
    }

    private fun titleOf(cornerId: String, titles: Map<String, String>): String {
        // 이어 묻기 코너 id = PREFIX + "손님|원래코너|말"
        val origin = CallbackPlanner.factKeyOf(cornerId)?.split('|')?.getOrNull(1)
            ?: return titles[cornerId] ?: cornerId
        return "${titles[origin] ?: CallbackPlanner.TITLE} (이어서)"
    }

    /**
     * QR 에 담을 글. 아무 휴대폰 카메라로 찍어도 바로 읽히는 평문이고, 첫 줄 머리표로 나중에 앱이 다시 읽을 수 있다.
     * 네트워크를 타지 않는다 — 화면 → 카메라.
     *
     * QR 이 너무 빽빽하면 차 화면에서 안 찍힌다 → [maxBytes] 를 넘으면 사람마다 오래된 줄부터 뺀다.
     */
    fun qrText(cards: List<ProfileCard>, episode: Int, maxBytes: Int = QR_MAX_BYTES, summary: EpisodeSummary? = null): String {
        var perGuest = cards.maxOfOrNull { it.lines.size } ?: 0
        while (true) {
            val text = buildString {
                append(HEADER).append(" · 제").append(episode).append("회까지\n")
                summary?.let { append(it.oneLine).append('\n') }
                cards.forEach { card ->
                    append('\n').append(card.nickname).append('\n')
                    card.lines.take(perGuest).forEach { append("- ").append(it).append('\n') }
                }
            }.trimEnd()
            if (text.toByteArray(Charsets.UTF_8).size <= maxBytes || perGuest <= 1) return text
            perGuest--
        }
    }

    const val HEADER = "#차곡 프로필 v1"

    /** UTF-8 한글 1자 = 3바이트. 900바이트면 QR 버전 ~20, 차 화면에서 폰으로 무난히 찍힌다. */
    const val QR_MAX_BYTES = 900
}
