package com.team.chagok.core.domain.show

import com.team.chagok.core.model.Corner
import com.team.chagok.core.model.Guest
import com.team.chagok.core.model.GuestId
import com.team.chagok.core.model.Seat

/**
 * 이번 코너의 마이크를 누구에게 줄지 정한다.
 *
 * 0. 정해진 출연자 코너(지난 회 이어 묻기)면 그 사람. 운전 중인 운전자면 아무도 안 부른다
 * 1. 신호가 좌석을 가리키면(도어) 그 좌석 출연자. 그 자리에 출연자가 없으면 아무도 안 부른다
 * 2. 운전자 코너면 운전자 (정차 중일 때만 여기까지 온다)
 * 3. 아니면 동승자 중 이번 회차에 덜 나온 사람 → 마지막으로 말한 지 오래된 사람 → 통산 출연이 적은 사람.
 *    운전 중에는 운전자를 부르지 않는다
 */
class GuestPicker {

    fun pick(
        roster: List<Guest>,
        corner: Corner,
        seatHint: Seat?,
        turns: List<GuestId>,
        driving: Boolean,
    ): Guest? {
        corner.forGuest?.let { id ->
            return roster.firstOrNull { it.id == id }?.takeUnless { driving && it.seat == Seat.DRIVER }
        }
        // 좌석을 가리킨 신호는 그 자리 사람만 부른다. 비어 있으면 아무도 안 부른다 — 다른 사람에게 "○○ 님 입장!" 하면 안 된다
        seatHint?.let { seat -> return roster.firstOrNull { it.seat == seat } }
        if (corner.needsDriver) return roster.firstOrNull { it.seat == Seat.DRIVER }

        val candidates = roster.filter { !driving || it.seat != Seat.DRIVER }
        return candidates.minWithOrNull(
            compareBy<Guest> { guest -> turns.count { it == guest.id } }
                .thenBy { guest -> turns.lastIndexOf(guest.id) }
                .thenBy { it.appearances },
        )
    }
}
