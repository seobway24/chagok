package com.team.chagok.feature.onair

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.team.chagok.feature.onair.theme.coverColors
import com.team.chagok.feature.onair.theme.LocalChagokColors

// 회차 표지와 지난 방송 선반. 표지 한 장 = 방송 한 회.

/** 색 면, 위에 내용, 모서리로 넘치는 큰 회차 숫자. [onClick] 이 없으면 그림으로만 쓴다. */
@Composable
internal fun EpisodeCover(number: Int, title: String, extent: Dp, modifier: Modifier = Modifier, sub: String? = null, onClick: (() -> Unit)? = null) {
    val tone = coverColors(number)
    val press = rememberPress()
    val base = modifier.pressScale(press).size(extent).clip(RoundedCornerShape(extent * CORNER)).background(tone.fill)
    Box(if (onClick != null) base.pressable(press, onClick = onClick) else base) {
        val titleSp = (extent.value * TITLE).coerceAtLeast(MIN_TITLE_SP)
        Column(Modifier.fillMaxWidth().padding(horizontal = extent * PAD, vertical = extent * PAD)) {
            Text(title, color = tone.ink, fontSize = titleSp.sp, lineHeight = (titleSp * 1.25f).sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, style = TextStyle(lineBreak = LineBreak.Heading))
            sub?.let { Text(it, color = tone.ink, fontSize = (titleSp * 0.92f).sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        CoverNumber(number, extent, tone.ink)
    }
}

@Composable
private fun BoxScope.CoverNumber(number: Int, extent: Dp, ink: Color) {
    val sizeSp = extent.value * NUMBER
    Text(
        "$number", color = ink, fontSize = sizeSp.sp, lineHeight = sizeSp.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-sizeSp * 0.05f).sp, maxLines = 1,
        modifier = Modifier.align(Alignment.BottomEnd).offset(x = -(extent * 0.04f), y = extent * NUMBER_DROP),
    )
}

/** 회차 요약은 "제3회, ..." 로 시작하므로 표지에서는 숫자와 겹치는 앞부분을 뗀다. */
internal fun EpisodeRecapUi.coverTitle(): String = oneLine.removePrefix("제${number}회, ")

/** 지난 방송 선반. 최신 회가 맨 앞이고, 표지를 누르면 그 회의 정리가 열린다. */
@Composable
internal fun EpisodeShelf(recaps: List<EpisodeRecapUi>, clips: List<VoiceClipUi>, room: Room, onOpen: (EpisodeRecapUi) -> Unit) {
    val c = LocalChagokColors.current
    Column {
        Text("지난 방송", color = c.ink, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = room.edge, bottom = 12.dp))
        LazyRow(
            Modifier.fillMaxWidth().fadeEnd(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = room.edge),
            horizontalArrangement = Arrangement.spacedBy(SHELF_GAP),
        ) {
            items(recaps.asReversed(), key = { it.number }) { recap ->
                EpisodeCover(recap.number, recap.coverTitle(), room.shelfCover, sub = clips.clipOf(recap.number)?.let { "${it.who}의 한마디" }, onClick = { onOpen(recap) })
            }
        }
    }
}

internal fun List<VoiceClipUi>.clipOf(episode: Int): VoiceClipUi? = lastOrNull { it.episode == episode }

/** 오른쪽 끝을 흐려 뒤에 표지가 더 있다는 걸 보여 준다. */
private fun Modifier.fadeEnd(): Modifier = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
    drawContent()
    val from = (size.width - 80.dp.toPx()) / size.width
    drawRect(
        Brush.horizontalGradient(from to Color.Black, 1f to Color.Transparent, startX = 0f, endX = size.width),
        blendMode = BlendMode.DstIn,
    )
}

/** 위쪽을 흐려 지난 대사가 자연스럽게 사라지게 한다. */
internal fun Modifier.fadeTop(): Modifier = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
    drawContent()
    drawRect(
        Brush.verticalGradient(0f to Color.Transparent, 0.2f to Color.Black, 0.9f to Color.Black, 1f to Color.Transparent, startY = 0f, endY = size.height),
        topLeft = Offset.Zero,
        blendMode = BlendMode.DstIn,
    )
}

/** 탑승한 사람 이름표. 말하는 사람만 자기 색으로 채운다. */
@Composable
internal fun PeopleGrid(seats: List<SeatUi>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        seats.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { PersonTag(it, Modifier.weight(1f)) }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

private const val CORNER = 0.075f
private const val PAD = 0.08f
private const val TITLE = 0.1f
private const val MIN_TITLE_SP = 18f
private const val NUMBER = 0.56f
private const val NUMBER_DROP = 0.05f
internal val SHELF_GAP = 14.dp
