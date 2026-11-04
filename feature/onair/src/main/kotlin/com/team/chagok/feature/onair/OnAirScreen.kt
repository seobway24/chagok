package com.team.chagok.feature.onair

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.team.chagok.core.model.AiSettings
import com.team.chagok.core.model.Seat
import com.team.chagok.core.model.SignalEvent
import com.team.chagok.feature.onair.component.DebugSignalPanel
import com.team.chagok.feature.onair.theme.ChagokMotion
import com.team.chagok.feature.onair.theme.ChagokTheme
import com.team.chagok.feature.onair.theme.LocalChagokColors
import kotlinx.coroutines.delay

@Composable
fun OnAirRoute(
    viewModel: OnAirViewModel,
    showDebugPanel: Boolean,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val voice by viewModel.voice.collectAsStateWithLifecycle()
    val boarding by viewModel.boarding.collectAsStateWithLifecycle()
    OnAirScreen(
        state = state,
        onStartClick = viewModel::onStartClick,
        onInject = viewModel::inject.takeIf { showDebugPanel },
        onForgetFamily = viewModel::onForgetFamilyConfirmed,
        onSavePerson = viewModel::onSavePerson,
        onRemovePerson = viewModel::onRemovePerson,
        onTestAi = viewModel::onTestAi,
        onSaveAi = viewModel::onSaveAi,
        onClearAi = viewModel::onClearAi,
        voice = voice,
        voiceActions = viewModel.voiceActions,
        library = viewModel::library,
        boarding = boarding,
        boardActions = viewModel.boardActions,
        modifier = modifier,
    )
}

/**
 * 방송 중 = 회차 표지와 대사. 대기 = 시작 문구, 사람 줄, 지난 방송 표지.
 *
 * Pleos 에서 앱 창은 두 가지다 — 넓은 창(화면 2/3, 가로)과 좁은 창(화면 1/3, 세로). 이 둘을 기준으로 그리고,
 * 그 밖의 크기(낮은 가로 등)는 같은 부품으로 유연하게만 맞춘다. 크기는 창 비율로 줄이지 않는다.
 */
@Composable
fun OnAirScreen(
    state: OnAirUiState,
    onStartClick: () -> Unit,
    onInject: ((SignalEvent) -> Unit)?,
    modifier: Modifier = Modifier,
    onForgetFamily: () -> Unit = {},
    onSavePerson: (PersonCard) -> Unit = {},
    onRemovePerson: (String) -> Unit = {},
    onTestAi: suspend (AiSettings) -> Boolean = { false },
    onSaveAi: (AiSettings) -> Unit = {},
    onClearAi: () -> Unit = {},
    voice: VoiceUi = VoiceUi(),
    voiceActions: VoiceActions = VoiceActions(),
    library: (suspend VoiceLibrary.() -> Unit) -> Unit = {},
    boarding: BoardingUi? = null,
    boardActions: BoardActions = NoBoardActions,
) {
    // 차 화면은 밀도를 낮게 잡아(2560×1440 에 160dpi) dp 가 휴대폰보다 작게 보인다.
    // 창이 아니라 "화면 전체" 크기로 한 번만 보정하므로 분할해도 글자는 줄지 않는다
    val base = LocalDensity.current
    val boost = (LocalConfiguration.current.smallestScreenWidthDp / REFERENCE_SW).coerceIn(1f, MAX_BOOST)
    CompositionLocalProvider(LocalDensity provides Density(base.density * boost, base.fontScale)) {
        val ai = remember(onTestAi, onSaveAi, onClearAi) { AiActions(onTestAi, onSaveAi, onClearAi) }
        Body(state, onStartClick, onInject, PeopleActions(onForgetFamily, onSavePerson, onRemovePerson), ai, VoiceBundle(voice, voiceActions, library), boarding, boardActions, modifier)
    }
}

/** 정리 화면에서 사람 카드·기억을 바꾸는 동작 묶음. */
@Immutable
internal class PeopleActions(
    val forget: () -> Unit,
    val save: (PersonCard) -> Unit,
    val remove: (String) -> Unit,
)

@Composable
private fun Body(
    state: OnAirUiState,
    onStartClick: () -> Unit,
    onInject: ((SignalEvent) -> Unit)?,
    actions: PeopleActions,
    ai: AiActions,
    voice: VoiceBundle,
    boarding: BoardingUi?,
    boardActions: BoardActions,
    modifier: Modifier,
) {
    val c = LocalChagokColors.current
    var debugOpen by remember { mutableStateOf(false) }
    var voicesOpen by remember { mutableStateOf(false) }
    val start = rememberStartWithMic(boardActions.go)
    var splash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(SPLASH_MS)
        splash = false
    }
    LaunchedEffect(state.driving) { if (state.driving) voicesOpen = false }
    val coverSpot = remember { mutableStateOf<Rect?>(null) }
    var ended by remember { mutableStateOf<Int?>(null) }
    var landing by remember { mutableStateOf<Int?>(null) }
    // 엔딩이 끝나는 순간 한 번만 이번 회 표지를 지난 방송으로 보낸다. 출발했으면 건너뛴다
    LaunchedEffect(state.phase, state.driving) {
        when {
            state.driving -> { landing = null; ended = null }
            state.phase == OnAirUiState.Phase.ENDING -> ended = state.episode
            else -> ended?.let { landing = it; ended = null }
        }
    }
    val onDebug = onInject?.let { { debugOpen = !debugOpen } }
    BoxWithConstraints(modifier.fillMaxSize().background(c.background)) {
        val room = Room(fitOf(maxWidth, maxHeight), maxWidth)
        // 방송이 시작·끝날 때 화면 전체가 툭 바뀌지 않게
        val parkedIdle = state.phase == OnAirUiState.Phase.IDLE && !state.driving
        val surface = when {
            landing != null -> Surface.LANDING
            splash -> Surface.SPLASH
            parkedIdle && voice.ui.consent == ConsentUi.UNKNOWN && state.profile != null -> Surface.CONSENT
            state.phase == OnAirUiState.Phase.IDLE && boarding != null -> Surface.BOARD
            parkedIdle && voicesOpen -> Surface.VOICES
            state.phase == OnAirUiState.Phase.OFFER || state.phase == OnAirUiState.Phase.KEEP -> Surface.KEEP
            state.phase.isSummary -> Surface.SUMMARY
            else -> Surface.STAGE
        }
        Crossfade(surface, animationSpec = tween(ChagokMotion.SCREEN_MS), label = "phase") { now ->
            when (now) {
                Surface.SPLASH -> SplashPane(if (voice.ui.consent == ConsentUi.UNKNOWN) "처음 뵙겠습니다" else "누가 탔는지 볼게요")
                Surface.CONSENT -> ConsentPane(room, voice.library)
                Surface.BOARD -> boarding?.let { BoardPane(it, room, boardActions, start, onDebug) }
                Surface.VOICES -> VoicePages(voice, room, onClose = { voicesOpen = false })
                Surface.KEEP -> KeepPane(state, room, voice.actions)
                Surface.LANDING -> LandingPane(landing ?: 0, coverSpot.value, state.profile?.recaps.orEmpty(), room, onDone = { landing = null })
                Surface.SUMMARY -> SummaryPane(state, room, onStartClick, actions, ai, voice, { voicesOpen = true }, onDebug)
                Surface.STAGE -> Stage(state, room, onDebug, coverSpot)
            }
        }
        if (debugOpen && onInject != null) {
            DebugSignalPanel(
                onInject = onInject,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(c.panel).padding(16.dp),
            )
        }
    }
}

private enum class Surface { SPLASH, CONSENT, BOARD, VOICES, KEEP, LANDING, SUMMARY, STAGE }

internal val NoBoardActions = BoardActions({}, {}, {}, {}, {}, {}, {})

private val OnAirUiState.Phase.isSummary: Boolean
    get() = this == OnAirUiState.Phase.IDLE

/** 창 모양. WIDE = 넓은 창(기준 1), TALL = 좁은 세로 창(기준 2), SHORT = 그 밖의 낮은 가로 창(유연하게). */
internal enum class Fit { WIDE, TALL, SHORT }

private fun fitOf(w: Dp, h: Dp): Fit = when {
    h > w -> Fit.TALL
    h < SHORT_H -> Fit.SHORT
    else -> Fit.WIDE
}

@Immutable
internal data class Room(val fit: Fit, val width: Dp) {
    val compact: Boolean get() = width < COMPACT_W
    val edge: Dp get() = if (fit == Fit.TALL) 24.dp else 36.dp
    val shelfCover: Dp get() = when (fit) {
        Fit.WIDE -> 176.dp
        Fit.TALL -> 176.dp
        Fit.SHORT -> 120.dp
    }
}

/** 화면 짧은 변이 이 dp 보다 크면 그만큼 키운다(최대 MAX_BOOST). 2560×1440·160dpi 차 화면 → 1.5배. */
private const val REFERENCE_SW = 960f
private const val SPLASH_MS = 1_200L
private const val MAX_BOOST = 1.6f

/** 이보다 좁으면 상단 코너 이름을 숨긴다. */
internal val COMPACT_W = 760.dp

/** 이보다 낮은 가로 창은 글자를 줄인다. */
private val SHORT_H = 520.dp

// ---- Preview: 넓은 창(2/3) · 좁은 창(1/3) ----

internal val previewSeats = listOf(
    SeatUi(Seat.DRIVER, "아빠", SeatStatus.WAITING, 0),
    SeatUi(Seat.FRONT_PASSENGER, "엄마", SeatStatus.LISTENING, 1),
    SeatUi(Seat.REAR_LEFT, "누나", SeatStatus.SPEAKING, 2),
    SeatUi(Seat.REAR_RIGHT, "막내", SeatStatus.LISTENING, 4),
)

private val previewOnAir = OnAirUiState(
    phase = OnAirUiState.Phase.ON_AIR,
    episode = 13,
    corner = "터널 미션",
    driving = true,
    seats = previewSeats,
    caption = "막내, 터널에서 뭐가 제일 무서웠어?",
)

@Preview(name = "방송 중 · 넓은 창 · 밤", widthDp = 1120, heightDp = 800)
@Composable
private fun StageWidePreview() {
    ChagokTheme(dark = true) { OnAirScreen(previewOnAir, onStartClick = {}, onInject = null) }
}

@Preview(name = "방송 중 · 좁은 창 · 낮", widthDp = 560, heightDp = 800)
@Composable
private fun StageTallPreview() {
    ChagokTheme(dark = false) { OnAirScreen(previewOnAir, onStartClick = {}, onInject = null) }
}
