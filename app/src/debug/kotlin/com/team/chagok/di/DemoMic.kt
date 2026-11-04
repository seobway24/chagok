package com.team.chagok.di

import com.team.chagok.BuildConfig
import com.team.chagok.core.domain.port.MicInput
import com.team.chagok.core.domain.show.ShowState
import com.team.chagok.core.domain.signal.RehearsalMic
import com.team.chagok.core.domain.signal.RehearsalScript

/**
 * debug 빌드 전용 리허설 마이크(-PDEMO_ANSWERS=true). 에뮬레이터는 음성 입력이 없어 대본 대답을 쓴다.
 * release 소스에는 같은 이름의 빈 구현만 있어 이 대본은 출시 APK 에 들어가지 않는다.
 */
object DemoMic {

    fun orNull(now: () -> ShowState?): MicInput? =
        if (BuildConfig.DEMO_ANSWERS) RehearsalMic(script, now) else null

    /** 코너 id → 대답. 순서는 BuiltinShow 의 질문 순서(callOut, 그다음 variants)와 같다. */
    val byCorner: Map<String, List<String>> = mapOf(
        "weather" to listOf(
            "칠 점이요. 빗소리가 좋아서요",
            "우산 노래요. 빗소리랑 잘 어울려요",
            "김치전이요! 바삭한 걸로요",
            "저녁쯤 그칠 것 같아요. 무지개 뜨면 좋겠다",
            "이불 덮고 만화책 보고 싶어요",
            "토독이요. 창문을 토독토독 쳐서요",
            "느린 자장가 같아요. 자꾸 졸려요",
        ),
        "tunnel" to listOf(
            "아빠가 양말을 짝짝이로 신고 나간 거요",
            "사실 어제 아이스크림 두 개 먹었어요",
            "휘파람이요. 아직 바람 소리만 나요",
            "친구가 김밥을 밥김이라고 부른 거요",
            "종이접기요. 학을 백 마리 접을 거예요",
            "엘리베이터에서 혼자 노래하다 들킨 거요",
            "아플 때 다들 죽 끓여 준 거요",
        ),
        "door" to listOf(
            "쌩쌩이요!",
            "맑음이요. 구름은 조금",
            "떡볶이요!",
            "구십 점이요",
            "팔십 점이요. 간식 주세요",
            "새로 산 노란 모자요",
        ),
        "milestone" to listOf(
            "강릉 바다요. 파도가 엄청 컸어요",
            "제주도요. 다 같이 귤 따러 가요",
            "휴게소 호두과자요",
            "아기상어요. 다 같이 따라 부르게요",
            "트럭에 실린 커다란 배요",
            "부산이요. 다섯 시간 걸렸어요",
            "통감자요! 버터 듬뿍",
        ),
        "arrival" to listOf(
            "웃다가 도착한 날이요",
            "운전해 줘서 고마워요",
            "다 같이 크게 웃은 순간이요",
            "손 씻고 물부터 마실 거예요",
            "제일 좋아하는 동물이 뭔지요",
            "백 점이요!",
        ),
        "captain" to listOf(
            "평화!",
            "팔십 점이요. 조금 막혔어요",
            "벨트 잘 매 줘서 고마워요",
            "신나는 옛날 가요요",
            "막내요. 계속 창밖만 봤어요",
            "그럼요. 손에 땀이 났어요",
        ),
        "best-moment" to listOf(
            "출발할 때 다 같이 노래 부른 거요",
            "아빠가 하품하다 사레들린 거요",
            "휴게소에서 혼자 계산해 봤어요",
            "엄마요. 간식을 딱 맞게 챙겼어요",
            "아침에 먹은 김밥이요",
            "친구랑 자전거 타기요",
            "차창 밖 노을 사진이요",
        ),
        "balance" to listOf(
            "짜장면이요. 짬뽕은 매워요",
            "바다요! 모래성 쌓을래요",
            "겨울이요. 눈사람 만들 수 있어서요",
            "강아지요. 같이 산책하고 싶어요",
            "저녁형이요. 아침엔 졸려요",
            "치킨이요. 양념 반 후라이드 반",
            "순간 이동이요. 학교까지 일 초",
        ),
        "dream" to listOf(
            "가족이랑 캠핑카 여행이요",
            "남극이요. 펭귄 보러요",
            "고양이요. 하루 종일 낮잠 잘래요",
            "줄넘기 백 개 넘기기요",
            "피아노요. 생일 축하 노래 치고 싶어요",
            "제주도에 작은 책방이요",
            "지금처럼 많이 웃고 있지?",
        ),
        "family-quiz" to listOf(
            "막내요. 어제도 차에서 바로 잤어요",
            "아빠요. 간식을 제일 먼저 찾아요",
            "엄마요. 목소리가 제일 고와요",
            "누나요. 작은 일에도 깔깔 웃어요",
            "아빠요. 지도 없이도 잘 찾아요",
            "누나요. 주말엔 점심에 일어나요",
            "운전한 아빠요!",
        ),
    )

    /** 지난 회 이어 묻기: 그 정보가 나온 코너 → 한 걸음 더 간 대답. */
    val callbacks: Map<String, String> = mapOf(
        "dream" to "그 꿈 그대로예요. 요즘 책도 찾아봐요",
        "best-moment" to "오늘은 휴게소에서 다 같이 웃은 게 이겼어요",
        "tunnel" to "그 뒤로 생각만 해도 웃음이 나요",
        "milestone" to "파도 소리가 아직 기억나요. 또 가고 싶어요",
        "balance" to "오늘도 그대로예요. 안 바뀌어요",
        "family-quiz" to "오늘도 같은 사람이에요. 벌써 하품해요",
        "captain" to "오늘도 평화예요. 길이 잘 뚫려요",
        "weather" to "오늘은 팔 점이요. 빗소리가 좋아요",
        "arrival" to "오늘도 웃다가 도착할 것 같아요",
    )

    const val FAREWELL = "다들 고마워요. 다음 방송도 기대할게요"

    private val script = RehearsalScript(byCorner, callbacks, FAREWELL)
}
