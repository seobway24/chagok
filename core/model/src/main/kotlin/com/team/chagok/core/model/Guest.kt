package com.team.chagok.core.model

@JvmInline
value class GuestId(val value: String)

/**
 * 출연자 명부의 한 줄. 차 안에만 저장되고 밖으로 나가지 않는다.
 *
 * 실명·연락처·음성 특징은 담지 않는다. [nickname] 은 가족이 정한 호칭이다.
 */
data class Guest(
    val id: GuestId,
    val nickname: String,
    val seat: Seat,
    val appearances: Int = 0,
    val isChild: Boolean = false,
)
