package com.team.chagok.feature.onair

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.team.chagok.feature.onair.theme.ChagokMotion
import com.team.chagok.feature.onair.theme.ChagokShapes
import com.team.chagok.feature.onair.theme.LocalChagokColors
import com.team.chagok.feature.onair.theme.personColors
import kotlinx.coroutines.delay

// 방송 중 화면. 정차 = 표지와 대사, 주행 = 이름 한 줄(터치할 것 없음), 멈춤 = 전환 없이 바로.

/** 지나간 말 한 줄. 자막은 최신 한 줄만 오므로 화면에서 쌓는다. [tone] 이 없으면 진행자다. */
@Immutable
internal data class SpokenLine(val who: String, val text: String, val tone: Color?)

@Composable
internal fun Stage(state: OnAirUiState, room: Room, onDebug: (() -> Unit)?, coverSpot: MutableState<Rect?>) {
    val lines = rememberSpokenLines(state)
    when {
        // 주행 중 멈춤은 색 전환 없이 한 줄만 바꾼다 — 깜빡이를 켠 순간(차선 변경)에 시선을 끌지 않게 (DESIGN.md §5)
        state.phase == OnAirUiState.Phase.PAUSED && state.driving -> DriveFrame(state, room, line = state.headline, paused = true)
        state.phase == OnAirUiState.Phase.PAUSED -> PausedFrame(state, room)
        state.driving -> DriveFrame(state, room)
        else -> AirFrame(state, room, lines, onDebug, coverSpot)
    }
}

@Composable
private fun rememberSpokenLines(state: OnAirUiState): SnapshotStateList<SpokenLine> {
    val lines = remember(state.episode) { mutableStateListOf<SpokenLine>() }
    LaunchedEffect(state.caption) {
        val text = state.caption ?: return@LaunchedEffect
        if (lines.lastOrNull()?.text != text) {
            lines += SpokenLine(state.captionBy ?: HOST_NAME, text, state.speakerTone())
        }
    }
    return lines
}

private fun OnAirUiState.speakerSeat(): SeatUi? = seats.firstOrNull { it.name == captionBy }

private fun OnAirUiState.speakerTone(): Color? = speakerSeat()?.let { personColors(it.tone).fill }

@Composable
private fun ShowBar(state: OnAirUiState, room: Room, onDebug: (() -> Unit)?) {
    AppBar(state.episodeTitle, room, onAir =state.phase == OnAirUiState.Phase.ON_AIR || state.phase == OnAirUiState.Phase.OPENING) {
        onDebug?.let { BarButton("신호", it) }
    }
}

@Composable
private fun AirFrame(state: OnAirUiState, room: Room, lines: List<SpokenLine>, onDebug: (() -> Unit)?, coverSpot: MutableState<Rect?>) {
    val c = LocalChagokColors.current
    Column(Modifier.fillMaxSize().padding(start = room.edge, end = room.edge)) {
        ShowBar(state, room, onDebug)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val label = if (state.caption == null) state.headline else state.corner.orEmpty()
            val cover = @Composable { extent: Dp ->
                EpisodeCover(
                    state.episode ?: 0, "오늘의 방송", extent,
                    Modifier.onGloballyPositioned { coverSpot.value = it.boundsInRoot() },
                )
            }
            val talk = @Composable { m: Modifier ->
                Column(m) {
                    Text(label, color = c.sub, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.height(34.dp))
                    Lyrics(lines, live = state.caption != null, room, Modifier.weight(1f).fillMaxWidth())
                }
            }
            if (room.fit == Fit.TALL) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        cover(TALL_COVER)
                        PeopleGrid(state.seats, Modifier.weight(1f))
                    }
                    talk(Modifier.weight(1f).fillMaxWidth())
                }
            } else {
                val extent = min(WIDE_COVER, maxHeight - PEOPLE_ROOM)
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                    Column(Modifier.width(extent), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        cover(extent)
                        PeopleGrid(state.seats)
                    }
                    talk(Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

/** 말이 길어도 줄을 자르지 않는다. 줄이 늘어나면 목록이 위로 밀린다. */
@Composable
private fun Lyrics(lines: List<SpokenLine>, live: Boolean, room: Room, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val list = rememberLazyListState()
        LaunchedEffect(lines.size) {
            if (lines.isNotEmpty()) list.animateScrollToItem(lines.lastIndex)
        }
        LazyColumn(
            Modifier.fillMaxSize().fadeTop(),
            state = list,
            contentPadding = PaddingValues(top = maxHeight * 0.2f, bottom = maxHeight * 0.75f),
            verticalArrangement = Arrangement.spacedBy(30.dp),
        ) {
            itemsIndexed(lines) { i, line -> LineRow(line, now = live && i == lines.lastIndex, room) }
        }
    }
}

@Composable
private fun LineRow(line: SpokenLine, now: Boolean, room: Room) {
    val c = LocalChagokColors.current
    val fade by animateFloatAsState(if (now) 1f else PAST_ALPHA, tween(ChagokMotion.STATE_MS), label = "line")
    val base = if (room.fit == Fit.WIDE) 42 else 30
    val sizeSp = if (line.text.length > LONG_LINE) base - 8 else base
    Column(Modifier.alpha(fade)) {
        Text(line.who, color = line.tone ?: c.sub, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
        RevealText(line.text, sizeSp, animate = now)
    }
}

/** 지금 말하는 줄만 낱말이 차례로 밝아진다. */
@Composable
private fun RevealText(text: String, sizeSp: Int, animate: Boolean) {
    val c = LocalChagokColors.current
    val words = remember(text) { text.split(' ') }
    var shown by remember(text) { mutableIntStateOf(if (animate) 0 else words.size) }
    LaunchedEffect(text) {
        while (shown < words.size) {
            delay(WORD_MS)
            shown++
        }
    }
    val styled = buildAnnotatedString {
        words.forEachIndexed { i, word ->
            val piece = if (i < words.lastIndex) "$word " else word
            if (i < shown) append(piece) else withStyle(SpanStyle(color = c.ink.copy(alpha = UNREAD_ALPHA))) { append(piece) }
        }
    }
    Text(styled, color = c.ink, fontSize = sizeSp.sp, lineHeight = (sizeSp * 1.26f).sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
}

/** 주행 중: 이름 한 줄. 누를 것이 없다 — 시연 "신호" 버튼도 정차 중에만 보인다. */
@Composable
private fun DriveFrame(state: OnAirUiState, room: Room, line: String = state.driveLine, paused: Boolean = false) {
    val c = LocalChagokColors.current
    Column(Modifier.fillMaxSize().padding(start = room.edge, end = room.edge)) {
        ShowBar(state, room, onDebug = null)
        Row(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 멈춤 노랑은 점 하나에만. 낮에는 stopFill, 밤에는 stopInk 가 노랑이다
            val stop = if (c.dark) c.stopInk else c.stopFill
            val dot = if (paused) stop else state.speakerTone() ?: c.sub
            Box(Modifier.size(30.dp).clip(CircleShape).background(dot))
            Text(line, color = c.ink, fontSize = (if (room.fit == Fit.WIDE) 76 else 52).sp, lineHeight = (if (room.fit == Fit.WIDE) 88 else 62).sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp, style = TextStyle(lineBreak = LineBreak.Heading))
        }
    }
}

@Composable
private fun PausedFrame(state: OnAirUiState, room: Room) {
    val c = LocalChagokColors.current
    Column(Modifier.fillMaxSize().background(c.stopFill).padding(start = room.edge, end = room.edge)) {
        AppBar(state.episode?.let { "제${it}회" }.orEmpty(), room, ink = c.stopInk, sub = c.stopInk)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            val long = state.headline.length > SHORT_PAUSE
            val sizeSp = if (room.fit == Fit.WIDE) (if (long) 76 else 100) else (if (long) 46 else 64)
            Text(state.headline, color = c.stopInk, fontSize = sizeSp.sp, lineHeight = (sizeSp * 1.16f).sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-2).sp, style = TextStyle(lineBreak = LineBreak.Heading))
        }
    }
}

/** 탑승한 사람 이름표 한 개. */
@Composable
internal fun PersonTag(seat: SeatUi, modifier: Modifier = Modifier) {
    val c = LocalChagokColors.current
    val tone = personColors(seat.tone)
    val live = seat.status == SeatStatus.SPEAKING
    Box(
        modifier.height(58.dp).clip(ChagokShapes.Pill).background(if (live) tone.fill else c.panel),
        contentAlignment = Alignment.Center,
    ) {
        Text(seat.name, color = if (live) tone.ink else c.sub, fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** 한 회가 끝나는 장면: 이번 회 표지가 지난 방송 맨 앞으로 들어간다. */
@Composable
internal fun LandingPane(episode: Int, from: Rect?, recaps: List<EpisodeRecapUi>, room: Room, onDone: () -> Unit) {
    val density = LocalDensity.current
    val t = remember { Animatable(0f) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(Unit) {
        t.animateTo(1f, tween(ChagokMotion.LANDING_MS, easing = ChagokMotion.EaseInOut))
        delay(LANDING_HOLD_MS)
        onDone()
    }
    val title = recaps.firstOrNull { it.number == episode }?.coverTitle() ?: "오늘의 방송"
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
        val slide = with(density) { (room.shelfCover + SHELF_GAP).toPx() }
        val older = recaps.filter { it.number != episode }.asReversed().take(SHELF_PREVIEW)
        Row(
            Modifier.align(Alignment.BottomStart).padding(start = room.edge, bottom = room.edge)
                .graphicsLayer { translationX = -slide * (1 - t.value) },
            horizontalArrangement = Arrangement.spacedBy(SHELF_GAP),
        ) {
            Box(Modifier.size(room.shelfCover))
            older.forEach { EpisodeCover(it.number, it.coverTitle(), room.shelfCover) }
        }
        val start = from ?: return@BoxWithConstraints
        val extent = with(density) { start.width.toDp() }
        val toX = with(density) { room.edge.toPx() }
        val toY = with(density) { (maxHeight - room.edge - room.shelfCover).toPx() }
        val shrink = room.shelfCover / extent
        EpisodeCover(
            episode, title, extent,
            Modifier.graphicsLayer {
                transformOrigin = TransformOrigin(0f, 0f)
                val p = t.value
                translationX = (start.left - origin.x) + (toX - (start.left - origin.x)) * p
                translationY = (start.top - origin.y) + (toY - (start.top - origin.y)) * p
                val s = 1f + (shrink - 1f) * p
                scaleX = s
                scaleY = s
            },
        )
    }
}

/** 진행자 말일 때 나오는 이름. 사람이 말하면 그 사람 이름이 대신 나온다. */
private const val HOST_NAME = "진행자"

private const val LONG_LINE = 50
private const val SHORT_PAUSE = 8
private const val PAST_ALPHA = 0.38f
private const val UNREAD_ALPHA = 0.32f
private const val WORD_MS = 120L
private const val LANDING_HOLD_MS = 420L
private const val SHELF_PREVIEW = 6
private val WIDE_COVER = 300.dp
private val TALL_COVER = 170.dp
private val PEOPLE_ROOM = 146.dp
