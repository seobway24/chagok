package com.team.chagok.core.model

/** 좌석. 오디오 존·도어 영역과 1:1 로 매핑된다. 뒷자리 가운데는 5인 탑승 때만 쓴다. */
enum class Seat {
    DRIVER,
    FRONT_PASSENGER,
    REAR_LEFT,
    REAR_CENTER,
    REAR_RIGHT,
    ;

    val isRear: Boolean get() = this == REAR_LEFT || this == REAR_CENTER || this == REAR_RIGHT

    /** 화면과 진행자가 쓰는 자리 이름. */
    val label: String get() = when (this) {
        DRIVER -> "운전석"
        FRONT_PASSENGER -> "조수석"
        REAR_LEFT -> "뒷자리 왼쪽"
        REAR_CENTER -> "뒷자리 가운데"
        REAR_RIGHT -> "뒷자리 오른쪽"
    }
}
