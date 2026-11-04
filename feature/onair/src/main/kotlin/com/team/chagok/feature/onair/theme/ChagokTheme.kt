package com.team.chagok.feature.onair.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.team.chagok.feature.onair.R

/**
 * 바탕은 무채색, 쨍한 색은 회차 표지와 말하는 사람에만 쓴다.
 * 시안: notes/demo/ui_fix/v3.html. 기준: DESIGN.md.
 */
@Immutable
data class ChagokColors(
    val background: Color,
    val panel: Color,
    val line: Color,
    val ink: Color,
    val sub: Color,
    /** 입력 칸 밑줄·커서, 방송 중 표시. */
    val accent: Color,
    val active: Color,
    /** 삭제·경고·녹음. */
    val warn: Color,
    val stopFill: Color,
    val stopInk: Color,
    val dark: Boolean,
    val go: Color = Color(0xFF2BE08F),
    val goInk: Color = Color(0xFF04211A),
)

val NightColors = ChagokColors(
    background = Color(0xFF0C0E0D),
    panel = Color(0xFF181B1A),
    line = Color(0xFF2C312F),
    ink = Color(0xFFFFFFFF),
    sub = Color(0xFF9BA3A0),
    accent = Color(0xFF2BE08F),
    active = Color(0xFF2BE08F),
    warn = Color(0xFFFF5A52),
    stopFill = Color(0xFF0C0E0D),
    stopInk = Color(0xFFFFD400),
    dark = true,
)

val DayColors = ChagokColors(
    background = Color(0xFFF6F6F3),
    panel = Color(0xFFFFFFFF),
    line = Color(0xFFDEDFDA),
    ink = Color(0xFF111312),
    sub = Color(0xFF5D6562),
    accent = Color(0xFF0A7A54),
    active = Color(0xFF0A7A54),
    warn = Color(0xFFD92D20),
    stopFill = Color(0xFFFFD400),
    stopInk = Color(0xFF1F1500),
    dark = false,
)

/** 표지·사람 색 한 쌍. 글자색은 면 대비 4.5:1 이상으로 계산해 골랐다. */
@Immutable
data class ColorPair(val fill: Color, val ink: Color)

private val Orange = ColorPair(Color(0xFFFF6B3D), Color(0xFF1A0B05))
private val Yellow = ColorPair(Color(0xFFFFC93C), Color(0xFF1F1500))
private val Blue = ColorPair(Color(0xFF2D6BFF), Color.White)
private val Green = ColorPair(Color(0xFF1FCF8E), Color(0xFF04211A))
private val Pink = ColorPair(Color(0xFFFF5FA8), Color(0xFF2A0416))
private val Violet = ColorPair(Color(0xFF7B4DFF), Color.White)

private val CoverOrder = listOf(Orange, Yellow, Blue, Green, Pink, Violet)
private val PersonOrder = listOf(Blue, Pink, Violet, Green, Orange, Yellow)

/** 회차 번호로 표지 색이 정해진다. 같은 회는 어디서나 같은 색이다. */
fun coverColors(episode: Int): ColorPair = CoverOrder[(episode - 1).mod(CoverOrder.size)]

/** 탑승 순서로 사람 색이 정해진다. */
fun personColors(order: Int): ColorPair = PersonOrder[order.mod(PersonOrder.size)]

private val Pretendard = FontFamily(
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
    Font(R.font.pretendard_extrabold, FontWeight.ExtraBold),
)

private val ChagokType = Typography(bodyLarge = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Medium))

val LocalChagokColors = staticCompositionLocalOf { NightColors }

/** 낮/밤은 차량 야간 모드(시스템 다크 모드)를 그대로 따른다. */
@Composable
fun ChagokTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (dark) NightColors else DayColors
    val scheme = if (dark) {
        darkColorScheme(primary = colors.go, background = colors.background, surface = colors.panel, onSurface = colors.ink)
    } else {
        lightColorScheme(primary = colors.go, background = colors.background, surface = colors.panel, onSurface = colors.ink)
    }
    CompositionLocalProvider(LocalChagokColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = ChagokType, content = content)
    }
}
