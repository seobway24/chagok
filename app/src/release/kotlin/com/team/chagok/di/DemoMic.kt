package com.team.chagok.di

import com.team.chagok.core.domain.port.MicInput
import com.team.chagok.core.domain.show.ShowState

/** 출시 빌드: 리허설 대본이 없다. 늘 실제 마이크를 쓴다. */
object DemoMic {
    @Suppress("UNUSED_PARAMETER")
    fun orNull(now: () -> ShowState?): MicInput? = null
}
