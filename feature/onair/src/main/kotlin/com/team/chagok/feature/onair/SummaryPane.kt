package com.team.chagok.feature.onair

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.team.chagok.core.model.Seat
import com.team.chagok.feature.onair.component.QrCode
import com.team.chagok.feature.onair.theme.ChagokShapes
import com.team.chagok.feature.onair.theme.ChagokTheme
import com.team.chagok.feature.onair.theme.LocalChagokColors

// 대기 화면: 시작 문구 + 사람 줄 + 지난 방송 표지. 사람을 누르면 편집, 표지를 누르면 그 회의 정리.

@Composable
internal fun SummaryPane(state: OnAirUiState, room: Room, onStartClick: () -> Unit, actions: PeopleActions, ai: AiActions, voice: VoiceBundle, onVoices: () -> Unit, onDebug: (() -> Unit)?) {
    val people = state.profile?.people.orEmpty()
    val recaps = state.profile?.recaps.orEmpty()
    var editing by remember { mutableStateOf<PersonCard?>(null) }
    var aiOpen by remember { mutableStateOf(false) }
    var qrOpen by remember { mutableStateOf(false) }
    var opened by remember { mutableStateOf<EpisodeRecapUi?>(null) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.padding(horizontal = room.edge)) {
            SummaryBar(state, room, onVoices, onAiSettings = { aiOpen = true }, onQr = { qrOpen = true }, onDebug)
        }
        if (state.driving) {
            Gate(state.headline, room, Modifier.weight(1f))
        } else {
            Hero(state, room, people, onStartClick, { editing = it }, Modifier.weight(1f))
            if (recaps.isNotEmpty()) {
                Box(Modifier.padding(bottom = room.edge)) {
                    EpisodeShelf(recaps, voice.ui.clips, room, onOpen = { opened = it })
                }
            }
        }
    }
    // 주행이 시작되면 입력 창을 닫는다(주행 중 입력 금지)
    editing?.takeUnless { state.driving }?.let { card ->
        PersonEditor(
            card = card,
            onSave = { actions.save(it); editing = null },
            onDelete = { actions.remove(card.id); editing = null },
            onDismiss = { editing = null },
        )
    }
    if (aiOpen && !state.driving) AiSettingsDialog(state.ai, ai, onDismiss = { aiOpen = false })
    opened?.takeUnless { state.driving }?.let { recap ->
        val clip = voice.ui.clips.clipOf(recap.number)
        EpisodeRecapDialog(
            recap, people.any { it.lines.isNotEmpty() }, actions.forget,
            clipName = clip?.who,
            onListen = { clip?.let { voice.library { listen(it.id) } } },
            onDismiss = {
                voice.library { stopListening() }
                opened = null
            },
        )
    }
    if (qrOpen && !state.driving) state.profile?.qrText?.let { QrDialog(it) { qrOpen = false } }
}

/** 차를 세워야 시작할 수 있다는 안내만 크게 보여 준다. */
@Composable
private fun Gate(text: String, room: Room, modifier: Modifier) {
    val c = LocalChagokColors.current
    val sizeSp = if (room.fit == Fit.WIDE) 66 else 44
    Box(modifier.fillMaxWidth().padding(horizontal = room.edge), contentAlignment = Alignment.CenterStart) {
        Text(text, color = c.ink, fontSize = sizeSp.sp, lineHeight = (sizeSp * 1.18f).sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
    }
}

@Composable
private fun Hero(state: OnAirUiState, room: Room, people: List<PersonCard>, onStartClick: () -> Unit, onEdit: (PersonCard) -> Unit, modifier: Modifier) {
    val c = LocalChagokColors.current
    val canStart = people.isNotEmpty()
    val sizeSp = if (room.fit == Fit.WIDE) 58 else 40
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.Center) {
        Text(
            if (canStart) state.caption ?: state.headline else "누가 타나요?",
            color = c.ink, fontSize = sizeSp.sp, lineHeight = (sizeSp * 1.16f).sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp,
            modifier = Modifier.padding(horizontal = room.edge),
        )
        PeopleRow(
            people, room, onEdit,
            onAdd = { onEdit(PersonCard(id = "", nickname = "", seat = nextFreeSeat(people))) },
        )
        if (state.phase == OnAirUiState.Phase.IDLE && canStart) {
            PillButton(
                "방송 시작", c.go, filled = true, onClick = onStartClick, fontSp = 30, label = c.goInk,
                modifier = Modifier.padding(start = room.edge, top = 20.dp),
                leading = { PlayGlyph(it) },
            )
        }
    }
}

/** 새 사람의 기본 자리: 좌석 순서대로 아직 아무도 없는 자리. 다 찼으면 뒷자리 가운데(편집에서 바꾼다). */
private fun nextFreeSeat(people: List<PersonCard>): Seat =
    Seat.entries.firstOrNull { seat -> people.none { it.seat == seat } } ?: Seat.REAR_CENTER

@Composable
private fun SummaryBar(state: OnAirUiState, room: Room, onVoices: () -> Unit, onAiSettings: () -> Unit, onQr: () -> Unit, onDebug: (() -> Unit)?) {
    // 대기 중이면 다음에 시작할 회차
    val episode = state.episode ?: ((state.profile?.lastEpisode ?: 0) + 1)
    AppBar("제${episode}회 준비", room) {
        if (!state.driving) {
            BarButton("목소리", onVoices)
            if (state.profile?.qrText != null) BarButton("휴대폰으로", onQr)
            BarButton(if (state.ai.on) "AI 켜짐" else "AI 설정", onAiSettings)
        }
        // 시연 전용. 주행 신호를 수동으로 넣은 뒤 정차로 되돌릴 길이라 주행 중에도 둔다
        onDebug?.takeUnless { state.driving }?.let { BarButton("신호", it) }
    }
}

/**
 * 프로필 QR. 휴대폰 카메라로 읽는 크기로 띄운다.
 * QR 에는 글이 그대로 들어 있다(ProfileCards.qrText: 지난 회 한 줄 + 사람별 카드 줄). 주소가 아니라 어디로도 올라가지 않는다.
 */
@Composable
private fun QrDialog(qrText: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.clip(ChagokShapes.Control).background(Color.White).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            QrCode(qrText, Modifier.size(QR_SIZE))
            Text(
                QR_HINT,
                Modifier.padding(top = 12.dp).width(QR_SIZE),
                // QR 바탕이 늘 흰색이라 글자도 테마와 상관없이 짙은 색으로 둔다
                color = Color(0xFF1F1F1F),
                fontSize = 16.sp,
                lineHeight = 22.sp,
            )
        }
    }
}

/** QR 안내 한 줄. 무엇이 들었는지 · 어떻게 쓰는지 · 밖으로 안 나간다는 것. */
internal const val QR_HINT = "휴대폰 카메라로 비추면 가족 카드 글이 열려요. 인터넷으로 보내지 않아요."

// ---- 사람 줄 ----

@Composable
private fun PeopleRow(people: List<PersonCard>, room: Room, onEdit: (PersonCard) -> Unit, onAdd: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 16.dp).horizontalScroll(rememberScrollState()).padding(horizontal = room.edge),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        people.forEach { PersonChip(it) { onEdit(it) } }
        AddChip(onAdd)
    }
}

@Composable
private fun PersonChip(card: PersonCard, onClick: () -> Unit) {
    val c = LocalChagokColors.current
    val press = rememberPress()
    Row(
        Modifier.pressScale(press).heightIn(min = CHIP_HEIGHT).clip(ChagokShapes.Pill).background(c.panel)
            .pressable(press, onClick = onClick).padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(card.nickname, color = c.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(card.seat.label + if (card.isChild) ", 아이" else "", color = c.sub, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 새 사람 추가. 친구도 여기서 카드가 된다. */
@Composable
private fun AddChip(onClick: () -> Unit) {
    val c = LocalChagokColors.current
    val press = rememberPress()
    Row(
        Modifier.pressScale(press).heightIn(min = CHIP_HEIGHT).clip(ChagokShapes.Pill)
            .dashedBorder(c.sub, CHIP_HEIGHT / 2).pressable(press, onClick = onClick).padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlusGlyph(16.dp, c.ink)
        Text("사람 추가", color = c.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

private val CHIP_HEIGHT = 64.dp
private val QR_SIZE = 340.dp

// ---- Preview ----

private val previewRecaps = listOf(
    EpisodeRecapUi(12, "제12회, 엄마, 누나 함께 코너 2개를 했어요", listOf("엄마" to "다음엔 바다로 가자"), emptyList()),
    EpisodeRecapUi(13, "제13회, 엄마, 누나 함께 코너 3개를 했어요", listOf("누나" to "오늘은 100장 도전!"), emptyList()),
)

private val previewPeople = FamilyProfileUi(
    people = listOf(
        PersonCard("g-dad", "아빠", Seat.DRIVER, lines = listOf("캡틴의 한마디 · 평화")),
        PersonCard("g-mom", "엄마", Seat.FRONT_PASSENGER, lines = listOf("추억 여행지 · 강릉 바다")),
        PersonCard("g-sis", "누나", Seat.REAR_LEFT, lines = listOf("터널 미션 · 셀카 50장")),
        PersonCard("g-little", "막내", Seat.REAR_RIGHT, isChild = true, lines = listOf("꿈 · 공룡 박사")),
    ),
    qrText = "#차곡 프로필 v1 · 제13회까지",
    recap = previewRecaps.last(),
    recaps = previewRecaps,
)

@Preview(name = "대기 · 넓은 창 · 밤", widthDp = 1120, heightDp = 800)
@Composable
private fun SummaryWidePreview() {
    ChagokTheme(dark = true) { OnAirScreen(OnAirUiState(episode = 13, profile = previewPeople), onStartClick = {}, onInject = null) }
}

@Preview(name = "대기 · 좁은 창 · 낮", widthDp = 560, heightDp = 800)
@Composable
private fun SummaryTallPreview() {
    ChagokTheme(dark = false) { OnAirScreen(OnAirUiState(episode = 13, profile = previewPeople), onStartClick = {}, onInject = null) }
}
