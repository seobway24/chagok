package com.team.chagok.core.model

/**
 * 코너 = 신호 하나로 열리는 20~30초짜리 주고받기.
 *
 * @property callOut 호명 대사 템플릿. `{guest}` 가 출연자 호칭으로 바뀐다.
 * @property needsDriver 운전자의 말이 필요한 코너. 정차 중에만 연다.
 * @property forGuest 정해진 출연자만 부르는 코너(지난 회 이어 묻기). 그 사람이 못 받으면 열지 않는다.
 * @property variants 같은 주제의 다른 질문. 회차 번호로 돌려서 두 번째 탈 때 같은 질문을 듣지 않게 한다.
 */
data class Corner(
    val id: String,
    val title: String,
    val trigger: SignalCategory,
    val callOut: String,
    val needsDriver: Boolean = false,
    val forGuest: GuestId? = null,
    val variants: List<String> = emptyList(),
) {
    /** 이번 회에 쓸 호명 대사. 회차가 하나 오를 때마다 다음 질문으로 넘어가고, 다 쓰면 처음으로 돌아간다. */
    fun callOutFor(episode: Int): String {
        val all = listOf(callOut) + variants
        return all[Math.floorMod(episode, all.size)]
    }
}

/** 형식 팩 = 코너 묶음. 실행 코드 없이 데이터만 있어 공유해도 안전하다. */
data class FormatPack(
    val id: String,
    val title: String,
    val corners: List<Corner>,
) {
    fun cornerFor(trigger: SignalCategory): Corner? = corners.firstOrNull { it.trigger == trigger }
}

/**
 * 진행자 스킨 = 말투 장르 + TTS 화자.
 * 실존 방송·인물·유행어·목소리 흉내는 담지 않는다.
 */
data class HostSkin(
    val id: String,
    val title: String,
    val ttsSpeaker: String,
)

/** 회차 하나의 저장 기록. */
data class EpisodeRecord(
    val number: Int,
    val guestIds: List<GuestId>,
    val cornerIds: List<String>,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    /** 이번 회 대답. 가족 기억([ProfileBook])의 재료이고 차 밖으로 내보내지 않는다. */
    val answers: List<Answer> = emptyList(),
)
