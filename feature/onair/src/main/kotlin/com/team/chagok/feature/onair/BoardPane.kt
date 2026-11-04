package com.team.chagok.feature.onair

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.team.chagok.core.model.Seat
import com.team.chagok.feature.onair.theme.ChagokShapes
import com.team.chagok.feature.onair.theme.ChagokTheme
import com.team.chagok.feature.onair.theme.LocalChagokColors
import com.team.chagok.feature.onair.theme.personColors
import kotlinx.coroutines.delay

// 앱 시작, 소개, 탑승 확인. 벨트를 매면 출석이고, 안 맨 사람이 있으면 방송을 시작하지 않는다.

/** 탑승 화면 버튼 묶음. */
@Immutable
class BoardActions(
    val go: () -> Unit,
    val later: () -> Unit,
    val confirmName: () -> Unit,
    val askAgain: () -> Unit,
    val plainGuest: () -> Unit,
    val togglePick: (String) -> Unit,
    val savePerson: (PersonCard) -> Unit,
)

@Composable
internal fun SplashPane(line: String) {
    val c = LocalChagokColors.current
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("차곡", color = c.ink, fontSize = 112.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-4).sp)
        Text(line, color = c.sub, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
internal fun WelcomePane(room: Room, onGo: () -> Unit) {
    val c = LocalChagokColors.current
    Column(Modifier.fillMaxSize().padding(horizontal = room.edge), verticalArrangement = Arrangement.Center) {
        Headline("오늘 차 안이 우리 가족 방송국이 됩니다", c.ink, room)
        Lead("진행자가 탄 사람에게 말을 걸고, 그날 나눈 대화를 회차로 쌓아 둡니다.", c.sub)
        PillButton("시작하기", c.ink, filled = true, onClick = onGo, modifier = Modifier.padding(top = 28.dp))
    }
}

@Composable
internal fun BoardPane(board: BoardingUi, room: Room, actions: BoardActions, onStart: () -> Unit, onDebug: (() -> Unit)?) {
    val c = LocalChagokColors.current
    val alarm = board.alarm
    val ink = if (alarm) c.stopInk else c.ink
    var typing by remember { mutableStateOf<Seat?>(null) }
    Column(Modifier.fillMaxSize().background(if (alarm) c.stopFill else c.background).padding(horizontal = room.edge)) {
        AppBar("제${board.episode}회 준비", room, ink = ink, sub = if (alarm) c.stopInk else c.sub) {
            if (!board.driving && board.mode != BoardMode.READY) BarButton("나중에", actions.later)
            onDebug?.takeUnless { board.driving }?.let { BarButton("신호", it) }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(bottom = 20.dp)) {
            val tallCabin = min(TALL_CABIN, maxHeight * 0.48f)
            if (room.fit == Fit.TALL) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
                    CabinMap(board, Modifier.fillMaxWidth().height(tallCabin), TALL_NOSE)
                    BoardSide(board, room, actions, onStart) { typing = it }
                }
            } else {
                val high = min(WIDE_CABIN, maxHeight)
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(56.dp), verticalAlignment = Alignment.CenterVertically) {
                    CabinMap(board, Modifier.width(high * CABIN_RATIO).fillMaxHeight(), WIDE_NOSE)
                    Column(Modifier.weight(1f)) { BoardSide(board, room, actions, onStart) { typing = it } }
                }
            }
        }
    }
    typing?.takeUnless { board.driving }?.let { seat ->
        PersonEditor(
            card = PersonCard(id = "", nickname = "", seat = seat),
            onSave = { actions.savePerson(it); typing = null },
            onDelete = { typing = null },
            onDismiss = { typing = null },
        )
    }
}

/** 위에서 본 차 안. 앞 2자리, 뒤 3자리. 운전석은 앞 왼쪽. */
@Composable
private fun CabinMap(board: BoardingUi, modifier: Modifier, nose: Dp) {
    val c = LocalChagokColors.current
    val alarm = board.alarm
    val at = { seat: Seat -> board.seats.first { it.seat == seat } }
    val shell = RoundedCornerShape(topStart = nose, topEnd = nose, bottomStart = nose / 2, bottomEnd = nose / 2)
    Column(
        modifier.clip(shell).background(if (alarm) c.stopInk.copy(alpha = 0.12f) else c.panel).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("앞", color = if (alarm) c.stopInk else c.sub, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(Seat.DRIVER, Seat.FRONT_PASSENGER).forEach { SeatCell(at(it), alarm, Modifier.weight(1f)) }
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(Seat.REAR_LEFT, Seat.REAR_CENTER, Seat.REAR_RIGHT).forEach { SeatCell(at(it), alarm, Modifier.weight(1f)) }
        }
    }
}

/** 색만으로 구분하지 않는다. 칸마다 글자로 상태를 쓴다. */
@Composable
private fun SeatCell(seat: BoardSeat, alarm: Boolean, modifier: Modifier) {
    val c = LocalChagokColors.current
    val tone = personColors(seat.tone)
    val shape = RoundedCornerShape(20.dp)
    val line = if (alarm) c.stopInk else c.ink
    val look = when (seat.state) {
        BoardSeatState.EMPTY -> SeatLook(line.copy(alpha = 0.06f), line.copy(alpha = 0.6f), "빈자리")
        BoardSeatState.BUCKLED -> SeatLook(tone.fill, tone.ink, "맸어요")
        BoardSeatState.UNKNOWN -> SeatLook(c.ink, c.background, "누구세요?")
        BoardSeatState.UNBUCKLED -> if (alarm) SeatLook(c.stopInk, c.stopFill, "안 맸어요") else SeatLook(Color.Transparent, c.ink, "안 맸어요")
    }
    val outlined = seat.state == BoardSeatState.UNBUCKLED && !alarm
    Column(
        modifier.fillMaxHeight().clip(shape).background(look.fill)
            .then(if (outlined) Modifier.border(2.dp, c.sub, shape) else Modifier).padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val name = seat.name ?: "?".takeIf { seat.state != BoardSeatState.EMPTY }
        name?.let { Text(it, color = look.ink, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, letterSpacing = (-0.3).sp) }
        Text(look.label, color = look.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** 화면 전체를 경고색으로 바꾸는 건 정차 중에만. 주행 중엔 글자로만 알리고 경고음은 차에 맡긴다 (DESIGN.md §5). */
private val BoardingUi.alarm: Boolean get() = mode == BoardMode.BELT && !driving

@Immutable
private data class SeatLook(val fill: Color, val ink: Color, val label: String)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoardSide(board: BoardingUi, room: Room, actions: BoardActions, onStart: () -> Unit, onType: (Seat) -> Unit) {
    val c = LocalChagokColors.current
    when (board.mode) {
        BoardMode.BELT -> {
            val ink = if (board.alarm) c.stopInk else c.ink
            Headline("${board.unbuckled.joinToString()}, 안전벨트를 매 주세요", ink, room)
            Lead("모두 매야 방송을 시작해요.", if (board.alarm) c.stopInk else c.sub)
        }
        BoardMode.READY -> {
            // 그 사이 누가 앉거나 내리면 처음부터 다시 센다
            LaunchedEffect(board.readyKey) {
                delay(READY_MS)
                onStart()
            }
            Headline(if (board.riders == 1) "혼자 타셨네요" else "${board.riders}명 모두 맸어요", c.ink, room)
            Lead("이제 제${board.episode}회를 시작합니다.", c.sub)
            CountBar((READY_MS / 1_000).toInt(), c.ink, Modifier.padding(top = 20.dp).width(BAR_WIDTH))
            Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton("지금 시작", c.ink, filled = true, onClick = onStart)
                PillButton("나중에", c.ink, filled = false, onClick = actions.later)
            }
        }
        BoardMode.GUEST -> GuestSide(board, room, actions, onType)
        BoardMode.MANUAL -> {
            Headline("오늘 누가 탔나요?", c.ink, room)
            Lead("이 차에서는 자리 정보를 받지 못해요. 탄 사람을 고르고 벨트를 맸는지 확인해 주세요.", c.sub)
            FlowRow(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                board.manual.forEachIndexed { i, person ->
                    val tone = personColors(i)
                    PillButton(
                        person.name, if (person.picked) tone.fill else c.ink, filled = person.picked, fontSp = 22,
                        label = if (person.picked) tone.ink else c.ink, onClick = { actions.togglePick(person.id) },
                    )
                }
            }
            if (board.riders > 0) PillButton("모두 맸어요", c.ink, filled = true, onClick = onStart, modifier = Modifier.padding(top = 20.dp))
        }
        BoardMode.WATCH -> if (board.riders == 0) {
            Headline("아직 아무도 없어요", c.ink, room)
            Lead("자리에 앉으면 여기에 나타나요.", c.sub)
        } else if (board.unbuckled.isEmpty() && board.doorOpen) {
            // 모두 맸지만 문이 열려 있다. 문 센서가 열림에 멈춰도 갇히지 않게 바로 시작할 수 있다
            Headline("${board.riders}명이 탔어요", c.ink, room)
            Lead("문이 닫히면 시작해요.", c.sub)
            PillButton("지금 시작", c.ink, filled = true, onClick = onStart, modifier = Modifier.padding(top = 20.dp))
        } else {
            Headline("${board.riders}명이 탔어요", c.ink, room)
            Lead("안전벨트를 매면 출석이에요." + board.unbuckled.takeIf { it.isNotEmpty() }?.let { " 아직 ${it.joinToString()} 남았어요." }.orEmpty(), c.sub)
        }
    }
}

@Composable
private fun GuestSide(board: BoardingUi, room: Room, actions: BoardActions, onType: (Seat) -> Unit) {
    val c = LocalChagokColors.current
    val seat = board.askSeat ?: return
    val heard = board.heard
    if (heard != null) {
        Headline("$heard, 맞나요?", c.ink, room)
        Lead("${seat.label}에 새 사람 카드를 만들어요.", c.sub)
        Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton("맞아요", c.ink, filled = true, onClick = actions.confirmName)
            PillButton("다시 말하기", c.ink, filled = false, onClick = actions.askAgain)
        }
        return
    }
    Headline("${seat.label}, 누구세요?", c.ink, room)
    Lead("이름을 말해 주세요. 앞사람이 대신 소개해도 돼요.", c.sub)
    if (board.listening) {
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(c.accent))
            Text("듣고 있어요", color = c.accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
    Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PillButton("이름 넣기", c.ink, filled = false, fontSp = 22, pad = 24.dp, onClick = { onType(seat) })
        PillButton("손님으로 둘게요", c.ink, filled = false, fontSp = 22, pad = 24.dp, onClick = actions.plainGuest)
    }
}

@Composable
private fun Headline(text: String, color: Color, room: Room) {
    val sizeSp = if (room.fit == Fit.WIDE) 54 else 36
    Text(
        text, color = color, fontSize = sizeSp.sp, lineHeight = (sizeSp * 1.18f).sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp,
        style = TextStyle(lineBreak = LineBreak.Heading),
    )
}

@Composable
private fun Lead(text: String, color: Color) {
    Text(text, color = color, fontSize = 22.sp, lineHeight = 31.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
}

private const val READY_MS = 4_000L
private const val CABIN_RATIO = 0.72f
private val WIDE_CABIN = 560.dp
private val TALL_CABIN = 320.dp
private val WIDE_NOSE = 110.dp
private val TALL_NOSE = 70.dp
private val BAR_WIDTH = 420.dp

// ---- Preview ----

private val previewBoard = BoardingUi(
    mode = BoardMode.BELT,
    seats = listOf(
        BoardSeat(Seat.DRIVER, "아빠", BoardSeatState.BUCKLED, 0),
        BoardSeat(Seat.FRONT_PASSENGER, "엄마", BoardSeatState.BUCKLED, 1),
        BoardSeat(Seat.REAR_LEFT, "지호", BoardSeatState.UNBUCKLED, 2),
        BoardSeat(Seat.REAR_CENTER, null, BoardSeatState.EMPTY, 6),
        BoardSeat(Seat.REAR_RIGHT, "민준", BoardSeatState.UNBUCKLED, 3),
    ),
    riders = 4,
    unbuckled = listOf("지호", "민준"),
    episode = 14,
)

@Preview(name = "벨트 안내 · 넓은 창", widthDp = 1120, heightDp = 800)
@Composable
private fun BoardWidePreview() {
    ChagokTheme(dark = true) { OnAirScreen(OnAirUiState(), onStartClick = {}, onInject = null, boarding = previewBoard) }
}
