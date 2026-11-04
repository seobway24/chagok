package com.team.chagok.feature.onair.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** 모서리. 조작 요소는 알약 모양, 면은 부드럽게 둥글다. */
object ChagokShapes {
    val Pill = CircleShape
    val Control = RoundedCornerShape(22.dp)
    val Chip = RoundedCornerShape(10.dp)
}

/** 움직임 기준값. 차 안에서는 시선을 뺏지 않게 짧게 끝낸다. */
object ChagokMotion {
    /** 시작이 빠른 강한 ease-out. 들어오는 것·상태 변화에 쓴다. */
    val EaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
    /** 화면 위를 가로지르는 이동. */
    val EaseInOut = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)
    const val PRESS_MS = 120
    const val STATE_MS = 200
    const val SCREEN_MS = 240
    /** 방송이 끝나고 표지가 지난 방송으로 들어가는 시간. 한 회에 한 번뿐이라 길어도 된다. */
    const val LANDING_MS = 620
}
