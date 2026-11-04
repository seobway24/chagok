package com.team.chagok.core.model

import kotlin.time.Duration

/**
 * 차량 신호를 앱 의미로 정규화한 이벤트.
 *
 * 원값(속도·좌표·VIN)은 담지 않는다. 어댑터가 디바운스까지 끝낸 뒤 내보낸다.
 */
sealed interface SignalEvent {

    /** CarUxRestrictions 기준. 속도로 판단하지 않는다(Pleos 원칙). */
    data class DrivingChanged(val driving: Boolean) : SignalEvent

    /** 진행 멈춤(SafetyPause) 트리거. 진행 중인 코너를 끊는다. */
    data class TurnSignalChanged(val on: Boolean) : SignalEvent

    /**
     * 브레이크 페달. 밟는 동안은 새 코너를 열지 않는다(진행 중 코너는 끊지 않는다).
     * SDK 는 페달 on/off 만 준다 — 급제동·주차 브레이크는 알 수 없다.
     */
    data class BrakePedalChanged(val pressed: Boolean) : SignalEvent

    /** 엔딩 트리거: 기어 P. */
    data object GearParked : SignalEvent

    /** 기어를 P 에서 뺐다. 녹음·재생은 기어 P 에서만 하므로 바로 끊는다. */
    data object GearReleased : SignalEvent

    /** 코너 트리거. */
    data object RainStarted : SignalEvent

    /** 터널을 지났다(전조등이 잠깐 켜졌다 꺼짐). 이름은 신호 출처 그대로 둔다. 밤의 계속 켜짐은 오지 않는다. */
    data object LightsOn : SignalEvent

    /** 비·터널 상황이 끝났다. 아직 열지 못한 코너를 거둔다. */
    data object RainStopped : SignalEvent
    data object LightsOff : SignalEvent
    data class DoorOpened(val seat: Seat) : SignalEvent
    data class DoorClosed(val seat: Seat) : SignalEvent
    data class MilestoneKm(val totalKm: Int) : SignalEvent

    /** 이 차가 자리별 착석·벨트 값을 주는지. 차종마다 달라 런타임에 묻는다. */
    data class SeatSensors(val available: Boolean) : SignalEvent

    /** 자리 하나의 착석·벨트가 바뀌었다. 탑승 확인과 방송 중 벨트 안내에 쓴다. */
    data class SeatChanged(val seat: Seat, val occupied: Boolean, val buckled: Boolean) : SignalEvent

    /**
     * 내비 경로 안내가 시작·변경·종료됐다. [destination] = 목적지 이름(안내가 끝나면 null).
     * 이름은 화면 제목과 오프닝 한 줄에만 쓴다. 저장하지 않고, LLM 으로 보내지 않는다. 좌표는 받지 않는다.
     */
    data class RouteChanged(val destination: String?) : SignalEvent

    /** 목적지까지 몇 분 남지 않았다 → 도착 예고 코너. 경로 하나에 한 번만 온다. */
    data object ArrivingSoon : SignalEvent

    /** 목적지까지 남은 시간. 1분 넘게 바뀔 때만 온다. 채움 코너 간격을 길이에 맞추는 데만 쓴다. */
    data class RemainingChanged(val left: Duration) : SignalEvent

    /** 내비가 목적지 도착을 알렸다. 방송을 마무리로 넘긴다(엔딩은 그대로 기어 P). */
    data object Arrived : SignalEvent
}

/**
 * LLM 에 넘겨도 되는 신호 맥락. 범주값만 있다. QUIET 는 차량 신호가 아니라 "한동안 조용함"(엔진 타이머).
 * ARRIVAL 은 내비 남은 시간(목적지 이름은 담지 않는다).
 */
enum class SignalCategory { RAIN, TUNNEL, DOOR, MILESTONE, QUIET, ARRIVAL }
