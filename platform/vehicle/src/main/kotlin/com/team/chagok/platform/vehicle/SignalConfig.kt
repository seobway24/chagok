package com.team.chagok.platform.vehicle

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * 신호를 이벤트로 바꾸는 기준값 (architecture §2).
 * 시연에서는 짧게 줄여 쓴다 — 예: rainAfter = 3.seconds.
 */
data class SignalConfig(
    /** 와이퍼가 이만큼 계속 돌면 "비가 온다". 한 번 닦기는 무시. */
    val rainAfter: Duration = 20.seconds,
    /** 전조등이 이만큼 이상 켜졌다가 꺼지면 터널을 지난 것. 10분 넘게 켜져 있으면 밤으로 보고 무시한다. */
    val tunnelAfter: Duration = 5.seconds,
    /** 문이 닫히고 이만큼 지나야 탑승 완료로 본다. */
    val doorSettle: Duration = 1.seconds,
    /** 누적 주행거리 특집 간격. */
    val milestoneStepKm: Int = 1_000,
)
