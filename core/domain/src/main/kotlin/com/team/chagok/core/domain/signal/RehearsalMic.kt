package com.team.chagok.core.domain.signal

import com.team.chagok.core.domain.port.Heard
import com.team.chagok.core.domain.port.MicInput
import kotlin.time.Duration

/** 음성 입력이 없는 에뮬레이터 리허설용. 정해 둔 대답을 차례로 돌려준다. 출시 빌드에는 켜지지 않는다. */
class RehearsalMic(private val answers: List<String>) : MicInput {
    private var next = 0

    override suspend fun listen(window: Duration): Heard = Heard.Text(answers[next++ % answers.size])
}
