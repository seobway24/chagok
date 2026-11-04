package com.team.chagok.core.domain.show

import org.junit.Assert.assertEquals
import org.junit.Test

class FillGuestTest {

    @Test
    fun `named guest keeps the honorific`() {
        assertEquals("막내 님 입장!", "{guest} 님 입장!".fillGuest("막내"))
    }

    @Test
    fun `plain guest does not get a doubled honorific`() {
        assertEquals("손님 입장! 오늘 컨디션 한 단어로?", "{guest} 님 입장! 오늘 컨디션 한 단어로?".fillGuest(PLAIN_GUEST_NAME))
    }

    @Test
    fun `plain guest without honorific is filled as is`() {
        assertEquals("손님, 어서 오세요!", "{guest}, 어서 오세요!".fillGuest(PLAIN_GUEST_NAME))
    }
}
