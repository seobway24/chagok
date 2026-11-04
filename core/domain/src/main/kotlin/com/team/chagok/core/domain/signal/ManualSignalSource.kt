package com.team.chagok.core.domain.signal

import com.team.chagok.core.domain.port.SignalSource
import com.team.chagok.core.model.SignalEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 손으로 넣는 신호. 시연 화면의 디버그 버튼과 테스트에서 쓴다.
 *
 * 앱에서는 실제 차량 신호와 merge 해서 넣는다 — 에뮬레이터 신호 주입이 막혀도 시연이 된다.
 */
class ManualSignalSource : SignalSource {
    private val flow = MutableSharedFlow<SignalEvent>(extraBufferCapacity = 16)
    override val events: Flow<SignalEvent> = flow.asSharedFlow()

    fun emit(event: SignalEvent) {
        flow.tryEmit(event)
    }
}
