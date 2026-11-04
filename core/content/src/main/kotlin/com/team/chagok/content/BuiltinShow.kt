package com.team.chagok.content

import com.team.chagok.core.model.Corner
import com.team.chagok.core.model.FamilyFact
import com.team.chagok.core.model.ProfileBook
import com.team.chagok.core.model.FormatPack
import com.team.chagok.core.model.Guest
import com.team.chagok.core.model.GuestId
import com.team.chagok.core.model.HostSkin
import com.team.chagok.core.model.Seat
import com.team.chagok.core.model.SignalCategory
import com.team.chagok.core.quip.Reaction
import com.team.chagok.core.quip.TemplateBook
import com.team.chagok.core.quip.TemplateBook.Key

/**
 * 내장 콘텐츠 1차본.
 *
 * TODO(content-json): assets 의 formats · skins · templates JSON 으로 옮긴다.
 *  팩을 데이터로 공유(로드맵)하려면 결국 JSON 이어야 한다. 지금은 컴파일 타임 검사를 위해 Kotlin.
 */
object BuiltinShow {

    val basicPack = FormatPack(
        id = "basic",
        title = "우리 차 기본 편성",
        // 질문은 회차 번호로 돈다(Corner.callOutFor). 같은 코너도 다음 회엔 다른 질문이 나온다.
        // 코너 id 는 가족 기억·이어 묻기 키라 바꾸지 않는다. 제목만 바꾼다.
        corners = listOf(
            Corner(
                "weather", "기상 특보", SignalCategory.RAIN, "기상 특보! {guest} 캐스터, 오늘 비 몇 점이에요?",
                variants = listOf(
                    "기상 특보! {guest} 캐스터, 비 오는 날 생각나는 노래 제목은?",
                    "기상 특보! {guest} 캐스터, 비 오는 날 제일 먹고 싶은 건?",
                    "기상 특보! {guest} 캐스터, 이 비는 언제 그칠 것 같아요?",
                    "기상 특보! {guest} 캐스터, 비 오는 날 집에서 뭐 하고 싶어요?",
                    "기상 특보! {guest} 캐스터, 오늘 비에 이름을 붙인다면?",
                    "기상 특보! {guest} 캐스터, 비가 노래라면 무슨 노래 같아요?",
                ),
            ),
            Corner(
                "tunnel", "터널 토크", SignalCategory.TUNNEL, "터널 통과 기념 비밀 토크! {guest}, 요즘 제일 웃겼던 일은?",
                variants = listOf(
                    "터널 통과 기념 비밀 토크! {guest}, 아무한테도 안 한 작은 비밀 하나?",
                    "터널 통과 기념 비밀 토크! {guest}, 요즘 몰래 연습하는 거 있어요?",
                    "터널 통과 기념 비밀 토크! {guest}, 최근에 들은 제일 웃긴 말은?",
                    "터널 통과 기념 비밀 토크! {guest}, 요즘 빠져 있는 게 뭐예요?",
                    "터널 통과 기념 비밀 토크! {guest}, 이번 주 제일 민망했던 순간은?",
                    "터널 통과 기념 비밀 토크! {guest}, 가족한테 고마웠던 순간 하나?",
                ),
            ),
            Corner(
                "door", "게스트 입장", SignalCategory.DOOR, "{guest} 님 입장! 오늘 컨디션 한 단어로?",
                variants = listOf(
                    "{guest} 님 입장! 오늘 기분을 날씨로 말하면?",
                    "{guest} 님 입장! 지금 제일 생각나는 음식은?",
                    "{guest} 님 입장! 오늘 하루를 점수로 매기면?",
                    "{guest} 님 입장! 지금 배고픔 지수는 몇 점?",
                    "{guest} 님 입장! 오늘 입은 옷 자랑 한마디?",
                ),
            ),
            Corner(
                "milestone", "주행 특집", SignalCategory.MILESTONE, "함께 달린 거리 특집! {guest}, 기억나는 여행지?",
                variants = listOf(
                    "함께 달린 거리 특집! {guest}, 다음에 꼭 같이 가고 싶은 곳은?",
                    "함께 달린 거리 특집! {guest}, 차에서 먹은 간식 중 최고는?",
                    "함께 달린 거리 특집! {guest}, 우리 가족 드라이브 노래 하나 추천!",
                    "함께 달린 거리 특집! {guest}, 길에서 본 제일 신기한 건?",
                    "함께 달린 거리 특집! {guest}, 제일 멀리 가 본 곳은 어디?",
                    "함께 달린 거리 특집! {guest}, 휴게소에서 꼭 먹는 건?",
                ),
            ),
            // 내비 남은 시간 3분 → 도착 예고. 내릴 때 남길 한마디를 미리 떠올리게 한다
            Corner(
                "arrival", "도착 예고", SignalCategory.ARRIVAL, "곧 도착해요! {guest}, 오늘 방송을 한 줄로 줄이면?",
                variants = listOf(
                    "곧 도착해요! {guest}, 내리기 전에 꼭 하고 싶은 말 있어요?",
                    "곧 도착해요! {guest}, 오늘 제일 기억에 남는 장면은?",
                    "곧 도착해요! {guest}, 도착하면 제일 먼저 뭐 할 거예요?",
                    "곧 도착해요! {guest}, 다음 방송에서 듣고 싶은 질문 하나?",
                    "곧 도착해요! {guest}, 오늘 방송 점수는 몇 점?",
                ),
            ),
            // 채움 코너: 신호 없이 한동안 조용할 때 위에서부터 하나씩. 정차 중에만 운전자에게도 마이크가 간다
            Corner(
                "captain", "캡틴 한마디", SignalCategory.QUIET, "잠깐 멈춘 김에 {guest} 캡틴! 오늘 운전 기분 한 단어로?",
                needsDriver = true,
                variants = listOf(
                    "잠깐 멈춘 김에 {guest} 캡틴! 오늘 길은 몇 점이었어요?",
                    "잠깐 멈춘 김에 {guest} 캡틴! 승객들에게 한마디?",
                    "잠깐 멈춘 김에 {guest} 캡틴! 운전하면서 듣고 싶은 노래는?",
                    "잠깐 멈춘 김에 {guest} 캡틴! 오늘 제일 얌전한 승객은?",
                    "잠깐 멈춘 김에 {guest} 캡틴! 처음 운전하던 날 기억나요?",
                ),
            ),
            Corner(
                "best-moment", "오늘의 순간", SignalCategory.QUIET, "오늘의 질문! {guest}, 오늘 제일 좋았던 순간은?",
                variants = listOf(
                    "오늘의 질문! {guest}, 오늘 제일 많이 웃은 순간은?",
                    "오늘의 질문! {guest}, 오늘 처음 해 본 게 있어요?",
                    "오늘의 질문! {guest}, 오늘 칭찬하고 싶은 사람은?",
                    "오늘의 질문! {guest}, 오늘 먹은 것 중 최고는?",
                    "오늘의 질문! {guest}, 내일 기대되는 일 하나?",
                    "오늘의 질문! {guest}, 오늘 하루를 사진 한 장으로 남긴다면?",
                ),
            ),
            Corner(
                "balance", "둘 중 하나", SignalCategory.QUIET, "둘 중 하나! {guest}, 평생 짜장면 vs 평생 짬뽕?",
                variants = listOf(
                    "둘 중 하나! {guest}, 바다 vs 산?",
                    "둘 중 하나! {guest}, 여름 vs 겨울?",
                    "둘 중 하나! {guest}, 강아지 vs 고양이?",
                    "둘 중 하나! {guest}, 아침형 vs 저녁형?",
                    "둘 중 하나! {guest}, 떡볶이 vs 치킨?",
                    "둘 중 하나! {guest}, 하늘을 나는 능력 vs 순간 이동?",
                ),
            ),
            Corner(
                "dream", "꿈 인터뷰", SignalCategory.QUIET, "꿈 인터뷰! {guest}, 앞으로 꼭 해 보고 싶은 건?",
                variants = listOf(
                    "꿈 인터뷰! {guest}, 어른이 되면 꼭 가 보고 싶은 곳은?",
                    "꿈 인터뷰! {guest}, 하루 동안 무엇이든 될 수 있다면?",
                    "꿈 인터뷰! {guest}, 올해 안에 꼭 이루고 싶은 거 하나?",
                    "꿈 인터뷰! {guest}, 배워 보고 싶은 게 있어요?",
                    "꿈 인터뷰! {guest}, 내가 만든 가게를 연다면 무슨 가게?",
                    "꿈 인터뷰! {guest}, 십 년 뒤 나에게 한마디?",
                ),
            ),
            Corner(
                "family-quiz", "우리 집 투표", SignalCategory.QUIET, "우리 집 투표! {guest}, 이 차에서 제일 잠꾸러기는 누구?",
                variants = listOf(
                    "우리 집 투표! {guest}, 우리 집 제일 먹보는 누구?",
                    "우리 집 투표! {guest}, 노래를 제일 잘하는 사람은?",
                    "우리 집 투표! {guest}, 제일 웃음이 많은 사람은?",
                    "우리 집 투표! {guest}, 길을 제일 잘 찾는 사람은?",
                    "우리 집 투표! {guest}, 제일 늦게 일어나는 사람은?",
                    "우리 집 투표! {guest}, 오늘 제일 멋진 사람은?",
                ),
            ),
        ),
    )

    /**
     * 지난 회 이어 묻기 틀. 정보가 나온 코너 id → 호명 대사. `{keyword}` 는 그 사람이 실제로 한 말.
     * `{keyword}` 뒤에 조사를 붙이지 않는다(받침에 따라 틀린다) — "…은 {keyword}!" 꼴로만 쓴다.
     * 순서 = 우선순위. 한 사람에게 정보가 여럿이면 앞 코너 것부터 꺼낸다(꿈 > 기상 특보).
     * 질문이 회차마다 돌기 때문에 어느 질문에서 나온 대답인지 모른다 — 코너 주제로만 되묻고 특정 질문을 가정하지 않는다.
     */
    val callbackTemplates: Map<String, String> = linkedMapOf(
        "dream" to "지난 회 {guest}의 꿈은 {keyword}! 그 꿈, 오늘은 조금 더 자세히 들려줄래요?",
        "best-moment" to "지난 회 오늘의 순간, {guest}의 대답은 {keyword}! 오늘은 그걸 이길 순간이 있었어요?",
        "tunnel" to "지난 회 터널 토크, {guest}의 대답은 {keyword}! 그 뒤로 어떻게 됐어요?",
        "milestone" to "지난 회 주행 특집, {guest}의 대답은 {keyword}! 그거 조금 더 자세히 알려 줄래요?",
        "balance" to "지난 회 {guest}의 선택은 {keyword}! 오늘도 그대로예요, 아니면 바뀌었어요?",
        "family-quiz" to "지난 회 {guest}의 투표는 {keyword}! 오늘도 같은 사람이에요?",
        "captain" to "지난 회 캡틴 {guest}의 한마디는 {keyword}! 오늘은 어때요?",
        "weather" to "지난 회 {guest} 캐스터의 기상 특보는 {keyword}! 오늘 날씨는 몇 점이에요?",
        "arrival" to "지난 회 도착 직전, {guest}의 한 줄은 {keyword}! 오늘은 어떤 하루였어요?",
    )

    val cornerTitles: Map<String, String>
        get() = basicPack.corners.associate { it.id to it.title } + ("farewell" to "하차 한마디")

    /** 음성 입력이 없는 에뮬레이터 리허설용 대답. debug 빌드에서 -PDEMO_ANSWERS=true 일 때만 쓴다. */
    val rehearsalAnswers = listOf(
        "오늘 비 오니까 칠 점이요. 그래도 짬뽕 먹으러 가서 좋아요",
        "저는 나중에 제주도에서 작은 책방을 열고 싶어요",
        "요즘은 게임 만드는 공부를 하고 있어요",
        "터널 지날 때 창문 닫는 게 제일 웃겨요",
        "오늘 다들 고마웠어요. 다음엔 오사카 가요",
    )

    /** 장르 스킨만. 실존 방송·인물·유행어·목소리 흉내는 넣지 않는다. */
    val skins = listOf(
        HostSkin(id = "basic", title = "다정한 진행자", ttsSpeaker = "haeun"),
        HostSkin(id = "hiphop", title = "힙합 MC", ttsSpeaker = "sanghyeok"),
        HostSkin(id = "roast", title = "독설 토크쇼", ttsSpeaker = "eunwoo"),
    )

    val templates = TemplateBook(
        lines = mapOf(
            // 같은 묶음 안 문장은 다 쓸 때까지 다시 안 나온다(TemplateQuip). 대답 낱말을 되받는 짧은 반응 위주
            Key("weather", "basic", Reaction.ANSWERED) to listOf(
                "{answer}! {guest} 캐스터의 정확한 예보였습니다~",
                "{guest} 캐스터 피셜 {answer}, 와이퍼도 박수 칩니다!",
                "{answer}, 기상청도 받아 적겠어요.",
                "빗속 현장 연결, {guest} 캐스터 {answer}! 수고했어요.",
            ),
            Key("weather", "hiphop", Reaction.ANSWERED) to listOf(
                "Yo {guest}, {answer}! 빗소리가 비트, 와이퍼는 드럼!",
            ),
            Key("balance", "basic", Reaction.ANSWERED) to listOf(
                "{guest}의 선택은 {answer}! 망설임이 없네요.",
                "{answer} 팀 한 명 추가요! 다른 분들은 어느 쪽?",
                "오, {answer}! {guest} 취향 확실히 알았습니다.",
            ),
            Key("dream", "basic", Reaction.ANSWERED) to listOf(
                "{answer}! {guest}의 꿈, 이번 회 기록에 꼭 남겨 둘게요.",
                "{answer}, 멋지다! 그날 첫 손님은 우리 가족이에요.",
                "{guest}의 꿈은 {answer}. 다음 회에 또 물어볼게요!",
            ),
            Key("family-quiz", "basic", Reaction.ANSWERED) to listOf(
                "{answer}! {guest}의 한 표 들어왔습니다.",
                "{answer}에게 한 표! 다른 분들 생각은요?",
            ),
        ),
        common = mapOf(
            Reaction.ANSWERED to listOf(
                "{answer}! 역시 {guest}, 오늘의 명언 등록입니다.",
                "{guest}의 한마디, {answer}. 이번 회 하이라이트 확정!",
                "{answer}! 그거 좋다.",
                "오, {answer}? {guest} 답다!",
                "{answer}, 메모해 둘게요.",
                "{guest} 덕분에 차 안이 환해졌어요. {answer}!",
                "{answer}! 다들 들었죠?",
                "{answer}, 그 대답 기다렸어요.",
            ),
            Reaction.SILENT to listOf(
                "{guest}, 오늘은 수줍음 모드네요. 다음 코너에서 만나요!",
                "마이크가 잠시 쉬어 갑니다. {guest}, 다음엔 꼭이요!",
                "생각할 시간이 필요하죠. {guest}, 나중에 알려 줘요.",
                "{guest}, 괜찮아요. 다음 차례에 또 물어볼게요.",
            ),
        ),
    )

    val bannedWords: Set<String> = setOf("씨발", "병신", "개새")
    val kidBannedWords: Set<String> = setOf("술", "담배")

    /** 브랜드 금지어. 진행자가 먼저 쓰지 않는다(출연자 말을 되받는 건 괜찮다). */
    val hostAvoidWords: Set<String> = setOf("이야기", "라디오")

    /** 시연용 지난 회차 번호와 그 회에 쌓인 가족 기억. 에뮬레이터(마이크 없음)·시연 영상에서 이어 묻기를 보여 준다. */
    const val DEMO_LAST_EPISODE = 12

    val demoMemory: ProfileBook
        get() = ProfileBook(
            listOf(
                FamilyFact(GuestId("g-dad"), "captain", "평화", DEMO_LAST_EPISODE),
                FamilyFact(GuestId("g-mom"), "milestone", "강릉 바다", DEMO_LAST_EPISODE),
                FamilyFact(GuestId("g-sister"), "tunnel", "셀카 50장", DEMO_LAST_EPISODE),
                FamilyFact(GuestId("g-sister"), "balance", "짜장면", DEMO_LAST_EPISODE),
                FamilyFact(GuestId("g-little"), "dream", "공룡 박사", DEMO_LAST_EPISODE),
                FamilyFact(GuestId("g-little"), "best-moment", "강아지 보러", DEMO_LAST_EPISODE),
            ),
            lastEpisode = DEMO_LAST_EPISODE,
        )

    /** 시연용 명부(4인 가족). 실제 명부는 정차 중 편집 화면에서 만든다(TODO roster). */
    val demoRoster = listOf(
        Guest(GuestId("g-dad"), "아빠", Seat.DRIVER),
        Guest(GuestId("g-mom"), "엄마", Seat.FRONT_PASSENGER),
        Guest(GuestId("g-sister"), "누나", Seat.REAR_LEFT, isChild = true),
        Guest(GuestId("g-little"), "막내", Seat.REAR_RIGHT, isChild = true),
    )
}
