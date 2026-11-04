package com.team.chagok.feature.onair

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.team.chagok.feature.onair.theme.ChagokColors
import com.team.chagok.feature.onair.theme.ChagokMotion
import com.team.chagok.feature.onair.theme.ChagokShapes
import com.team.chagok.feature.onair.theme.LocalChagokColors

// 공용 부품: 면 · 알약 버튼 · 상단 줄 · 누름 · 상태 불빛.

/** 면 한 장의 바탕색과 글자색. */
@Immutable
internal data class PlateTone(val fill: Color, val ink: Color)

internal val ChagokColors.plainPlate get() = PlateTone(Color.Transparent, ink)
internal val ChagokColors.pausePlate get() = PlateTone(Color.Transparent, ink)

private const val MIN_BUTTON_DP = 56f
private const val PRESSED_SCALE = 0.97f

/** 내용을 가운데에 놓는 면. 색은 넣지 않아도 된다. */
@Composable
internal fun Plate(
    tone: PlateTone,
    modifier: Modifier = Modifier,
    contentPadding: Dp = 44.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.background(tone.fill).padding(vertical = contentPadding),
        verticalArrangement = Arrangement.Center,
        content = content,
    )
}

/** 알약 버튼. [filled] 는 [ink] 색 면, 아니면 [ink] 를 옅게 깐 면. [label] 은 면이 쨍한 색일 때 글자색을 정한다. */
@Composable
internal fun PillButton(
    text: String,
    ink: Color,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fontSp: Int = 26,
    label: Color? = null,
    pad: Dp = 32.dp,
    leading: (@Composable (Color) -> Unit)? = null,
) {
    val press = rememberPress()
    val base = modifier.pressScale(press).heightIn(min = (fontSp * 2.8f).coerceAtLeast(MIN_BUTTON_DP).dp).clip(ChagokShapes.Pill)
    val painted = base.background(if (filled) ink else ink.copy(alpha = OUTLINE_ALPHA))
    val shown = label ?: if (!filled) ink else if (ink.luminance() > 0.5f) ON_LIGHT else Color.White
    Row(
        painted.pressable(press, onClick = onClick).padding(horizontal = pad),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke(shown)
        Text(text, color = shown, fontSize = fontSp.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

private const val OUTLINE_ALPHA = 0.1f
private val ON_LIGHT = Color(0xFF0C0E0D)

/** 상단 줄: 앱 이름, 회차, 방송 표시, 오른쪽 조작. */
@Composable
internal fun AppBar(
    title: String,
    room: Room,
    onAir: Boolean = false,
    ink: Color = LocalChagokColors.current.ink,
    sub: Color = LocalChagokColors.current.sub,
    trailing: @Composable () -> Unit = {},
) {
    val c = LocalChagokColors.current
    Row(
        Modifier.fillMaxWidth().height(BAR_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("차곡", color = ink, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp)
        if (!room.compact) Text(title, color = sub, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        if (onAir) StatusLed("방송 중", c.accent, c.accent, sizeSp = 24)
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

@Composable
internal fun BarButton(text: String, onClick: () -> Unit) {
    PillButton(text, LocalChagokColors.current.ink, filled = false, onClick = onClick, fontSp = 18)
}

private val BAR_HEIGHT = 88.dp

/** 재생 삼각형. 글자 ▶ 는 글꼴마다 달라 직접 그린다. */
@Composable
internal fun PlayGlyph(color: Color, extent: Dp = 26.dp) {
    Canvas(Modifier.size(extent)) {
        val path = Path().apply {
            moveTo(size.width * 0.2f, 0f)
            lineTo(size.width, size.height / 2)
            lineTo(size.width * 0.2f, size.height)
            close()
        }
        drawPath(path, color)
    }
}

/** 누름 상태. 판 전체가 줄어야 하므로 [pressScale] 은 체인 맨 앞, [pressable] 은 clip 뒤에 건다. */
@Stable
internal class Press(val source: MutableInteractionSource, val scale: State<Float>)

@Composable
internal fun rememberPress(): Press {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale = animateFloatAsState(
        if (pressed) PRESSED_SCALE else 1f,
        tween(ChagokMotion.PRESS_MS, easing = ChagokMotion.EaseOut),
        label = "press",
    )
    return remember(source) { Press(source, scale) }
}

internal fun Modifier.pressScale(press: Press): Modifier = graphicsLayer {
    scaleX = press.scale.value
    scaleY = press.scale.value
}

@Composable
internal fun Modifier.pressable(press: Press, enabled: Boolean = true, onClick: () -> Unit): Modifier =
    clickable(press.source, LocalIndication.current, enabled = enabled, role = Role.Button, onClick = onClick)

/** 상태 불빛 + 글. */
@Composable
internal fun StatusLed(text: String, color: Color, textColor: Color, sizeSp: Int = 16) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(text, color = textColor, fontSize = sizeSp.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** 더하기 그림. 글자 "+" 는 글꼴마다 굵기와 위치가 달라 직접 그린다. */
@Composable
internal fun PlusGlyph(extent: Dp, color: Color) {
    Canvas(Modifier.size(extent)) {
        val stroke = 2.5.dp.toPx()
        val mid = size.width / 2
        drawLine(color, Offset(mid, 0f), Offset(mid, size.height), stroke, StrokeCap.Round)
        drawLine(color, Offset(0f, mid), Offset(size.width, mid), stroke, StrokeCap.Round)
    }
}

/** 점선 테두리(새 사람 추가). */
internal fun Modifier.dashedBorder(color: Color, radius: Dp): Modifier = drawBehind {
    drawRoundRect(
        color,
        style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx()))),
        cornerRadius = CornerRadius(radius.toPx()),
    )
}
