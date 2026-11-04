package com.team.chagok.core.model

/** 코너 하나에서 들은 대답. [question] 은 진행자가 실제로 읽은 호명 대사. */
data class Answer(
    val guestId: GuestId,
    val cornerId: String,
    val question: String,
    val text: String,
)

/**
 * 대답에서 뽑은 가족 정보 한 조각. 예: 막내 · dream · "공룡 박사".
 *
 * [keyword] 는 그 사람이 실제로 한 말 안에 있어야 한다(지어낸 정보 금지 — [ProfileBook] 에 넣기 전에 검사).
 */
data class FamilyFact(
    val guestId: GuestId,
    val cornerId: String,
    val keyword: String,
    val episode: Int,
) {
    val key: String get() = "${guestId.value}|$cornerId|$keyword"
}

/** 사람 카드 한 줄. 예: 좋아하는 것 · 짬뽕. 그 사람이 실제로 한 대답을 근거로만 만든다. */
data class PortraitLine(
    val guestId: GuestId,
    val label: String,
    val value: String,
    val episode: Int,
)

/** 회차의 명장면. [text] 는 그 사람이 한 말 원문이다. */
data class EpisodeHighlight(
    val guestId: GuestId,
    val cornerId: String,
    val text: String,
)

/** 회차 정리: 한 줄 요약 · 명장면 · 인사이트. 대답 원문 전체는 남기지 않는다. */
data class EpisodeSummary(
    val number: Int,
    val oneLine: String,
    val highlights: List<EpisodeHighlight> = emptyList(),
    val insights: List<String> = emptyList(),
)

/** 한 회차를 정리한 결과. [ProfileBook.withDigest] 로 기억에 넣는다. */
data class EpisodeDigest(
    val summary: EpisodeSummary,
    val portraits: List<PortraitLine> = emptyList(),
)

/**
 * 회차가 쌓일수록 자라는 가족 기억. 불변 — 바꿀 때마다 새 값을 만든다.
 *
 * @property calledBack 다음 회에 이미 이어 물어본 정보([FamilyFact.key]). 같은 얘기를 두 번 꺼내지 않는다.
 * @property lastEpisode 마지막으로 기억한 회차. 앱을 다시 켜도 회차 번호가 이어지게 같이 저장한다.
 * @property people 사람 카드(명부). 가족이 정차 화면에서 직접 추가·수정·삭제한다. 친구도 들어간다.
 * @property portraits 사람 카드에 보여 줄 정리된 줄. 같은 사람·같은 항목은 새 값으로 바꾼다.
 * @property episodes 회차 정리. 최근 [MAX_EPISODES] 회만 남긴다.
 */
data class ProfileBook(
    val facts: List<FamilyFact> = emptyList(),
    val calledBack: Set<String> = emptySet(),
    val lastEpisode: Int = 0,
    val people: List<Guest> = emptyList(),
    val portraits: List<PortraitLine> = emptyList(),
    val episodes: List<EpisodeSummary> = emptyList(),
) {
    fun factsOf(guestId: GuestId): List<FamilyFact> = facts.filter { it.guestId == guestId }

    /** 같은 id 면 바꾸고, 없으면 끝에 붙인다. */
    fun withPerson(guest: Guest): ProfileBook =
        if (people.any { it.id == guest.id }) {
            copy(people = people.map { if (it.id == guest.id) guest else it })
        } else {
            copy(people = people + guest)
        }

    /** 사람을 지우면 그 사람에 대해 쌓인 기억도 같이 지운다. */
    fun withoutPerson(id: GuestId): ProfileBook = copy(
        people = people.filterNot { it.id == id },
        facts = facts.filterNot { it.guestId == id },
        calledBack = calledBack.filterNot { it.startsWith("${id.value}|") }.toSet(),
        portraits = portraits.filterNot { it.guestId == id },
        episodes = episodes.map { e -> e.copy(highlights = e.highlights.filterNot { it.guestId == id }) },
    )

    /** 같은 회차를 다시 넣으면 바꾼다(규칙 정리 → AI 정리). 같은 사람·같은 항목은 새 값이 이긴다. */
    fun withDigest(digest: EpisodeDigest, perGuest: Int = MAX_PORTRAIT_PER_GUEST): ProfileBook {
        val replaced = digest.portraits.map { it.guestId to it.label }.toSet()
        val merged = portraits.filterNot { (it.guestId to it.label) in replaced } + digest.portraits
        return copy(
            portraits = merged.groupBy { it.guestId }.values.flatMap { it.takeLast(perGuest) },
            episodes = (episodes.filterNot { it.number == digest.summary.number } + digest.summary)
                .sortedBy { it.number }.takeLast(MAX_EPISODES),
        )
    }

    /** 같은 사람·같은 코너·같은 말은 한 번만. 한 사람당 [perGuest] 개까지, 오래된 것부터 버린다. */
    fun plus(newFacts: List<FamilyFact>, perGuest: Int = MAX_FACTS_PER_GUEST): ProfileBook {
        val known = facts.map { it.key }.toSet()
        val merged = facts + newFacts.filter { it.key !in known }.distinctBy { it.key }
        val trimmed = merged.groupBy { it.guestId }.values.flatMap { it.takeLast(perGuest) }
        return copy(facts = merged.filter { it in trimmed })
    }

    fun markCalledBack(keys: Collection<String>): ProfileBook = copy(calledBack = calledBack + keys)

    companion object {
        const val MAX_FACTS_PER_GUEST = 12
        const val MAX_PORTRAIT_PER_GUEST = 6
        const val MAX_EPISODES = 20
    }
}
