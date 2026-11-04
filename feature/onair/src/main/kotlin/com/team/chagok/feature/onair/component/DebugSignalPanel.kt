package com.team.chagok.feature.onair.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.team.chagok.core.model.Seat
import com.team.chagok.core.model.SignalEvent
import kotlin.time.Duration.Companion.minutes

/** 시연용 신호 버튼. 디버그 빌드에서만 보인다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DebugSignalPanel(
    onInject: (SignalEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DEBUG_SIGNALS.forEach { (label, event) ->
            OutlinedButton(onClick = { onInject(event) }) { Text(label) }
        }
    }
}

private val DEBUG_SIGNALS = listOf(
    "주행" to SignalEvent.DrivingChanged(driving = true),
    "정차" to SignalEvent.DrivingChanged(driving = false),
    "비" to SignalEvent.RainStarted,
    // 실차는 전조등 5초~10분 켜졌다 꺼질 때 이 신호가 나온다(TunnelPass). 버튼은 그 결과만 넣는다
    "터널 통과" to SignalEvent.LightsOn,
    "뒷문 닫힘" to SignalEvent.DoorClosed(Seat.REAR_LEFT),
    "100km" to SignalEvent.MilestoneKm(100),
    // 내비 경로. 에뮬레이터에 경로를 못 넣을 때 시연용
    "경로: 할머니댁" to SignalEvent.RouteChanged("할머니댁"),
    "남은 40분" to SignalEvent.RemainingChanged(40.minutes),
    "도착 3분 전" to SignalEvent.ArrivingSoon,
    "도착" to SignalEvent.Arrived,
    "경로 끝" to SignalEvent.RouteChanged(null),
    "깜빡이 ON" to SignalEvent.TurnSignalChanged(on = true),
    "깜빡이 OFF" to SignalEvent.TurnSignalChanged(on = false),
    "기어 P" to SignalEvent.GearParked,
    "기어 D" to SignalEvent.GearReleased,
    "자리 값 없음" to SignalEvent.SeatSensors(available = false),
) + Seat.entries.flatMap { seat ->
    listOf(
        "${seat.label} 앉음" to SignalEvent.SeatChanged(seat, occupied = true, buckled = false),
        "${seat.label} 벨트" to SignalEvent.SeatChanged(seat, occupied = true, buckled = true),
        "${seat.label} 비움" to SignalEvent.SeatChanged(seat, occupied = false, buckled = false),
    )
}
