package com.team.chagok.feature.onair

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.team.chagok.feature.onair.theme.ChagokShapes
import com.team.chagok.feature.onair.theme.LocalChagokColors
import com.team.chagok.feature.onair.theme.personColors
import kotlinx.coroutines.delay

// 방송 앞뒤 목소리 화면. 전부 기어 P 에서만 나온다. 상태 머신이 출발·깜빡이에서 바로 끝낸다.

/** 위쪽 회차 줄과 본문. */
@Composable
private fun VoiceFrame(title: String, room: Room, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().padding(start = room.edge, end = room.edge, bottom = room.edge)) {
        AppBar(title, room)
        content()
    }
}

/** 표지를 곁들인 문장 화면. 좁은 창에서는 위아래로 놓는다. */
@Composable
private fun CoverStatement(room: Room, cover: @Composable () -> Unit, content: @Composable ColumnScope.() -> Unit, modifier: Modifier = Modifier) {
    if (room.fit == Fit.TALL) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically)) {
            cover()
            Column(content = content)
        }
    } else {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(48.dp), verticalAlignment = Alignment.CenterVertically) {
            cover()
            Column(Modifier.weight(1f), content = content)
        }
    }
}

@Composable
private fun Statement(text: String, room: Room) {
    val sizeSp = bigSp(room)
    Text(text, color = LocalChagokColors.current.ink, fontSize = sizeSp.sp, fontWeight = FontWeight.ExtraBold, lineHeight = (sizeSp * 1.18f).sp, letterSpacing = (-0.8).sp, style = TextStyle(lineBreak = LineBreak.Heading))
}

@Composable
private fun Detail(text: String, modifier: Modifier = Modifier.padding(top = 12.dp)) {
    Text(text, color = LocalChagokColors.current.sub, fontSize = 23.sp, fontWeight = FontWeight.SemiBold, lineHeight = 31.sp, modifier = modifier)
}

private fun bigSp(room: Room) = if (room.fit == Fit.TALL) 38 else 52

private fun Room.bigCover() = if (fit == Fit.TALL) 280.dp else 300.dp

@Composable
internal fun KeepPane(state: OnAirUiState, room: Room, actions: VoiceActions) {
    val episode = state.episode ?: 0
    val offer = state.offer
    val keep = state.keep
    when {
        offer != null -> OfferPlate(episode, offer, room, actions)
        keep?.recordingBy != null -> RecordingPlate(episode, keep.recordingBy, room, actions)
        keep?.saved != null -> SavedPlate(episode, keep.saved, room, actions)
        keep?.full == true -> FullPlate(episode, room, actions)
        keep != null -> AskPlate(episode, keep, room, actions)
    }
}

@Composable
private fun OfferPlate(episode: Int, offer: OfferUi, room: Room, actions: VoiceActions) {
    val c = LocalChagokColors.current
    val clip = offer.clip
    VoiceFrame("제${episode}회", room) {
        CoverStatement(
            room,
            cover = { EpisodeCover(clip.episode, clip.date, room.bigCover(), sub = "${clip.who}의 한마디") },
            modifier = Modifier.weight(1f),
            content = {
                Statement(
                    when {
                        offer.playing -> "${clip.who} 목소리를 틀고 있어요"
                        offer.rainy -> "비 오는 날 남긴 ${clip.who} 목소리, 지금 틀까요?"
                        else -> "제${clip.episode}회에 남긴 ${clip.who} 목소리, 지금 틀까요?"
                    },
                    room,
                )
                Detail("제${clip.episode}회, ${clip.date}, ${clip.seconds}초" + if (offer.rainy) ". 오늘도 비가 와서 골랐어요." else "")
                if (offer.playing) {
                    CountBar(clip.seconds, c.ink, Modifier.padding(top = 24.dp))
                }
                Row(Modifier.padding(top = 26.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!offer.playing) PillButton("틀기", c.ink, filled = true, onClick = actions.playOffer, leading = { PlayGlyph(it) })
                    PillButton("건너뛰기", c.ink, filled = false, onClick = actions.skipOffer)
                }
            },
        )
    }
}

@Composable
private fun AskPlate(episode: Int, keep: KeepUi, room: Room, actions: VoiceActions) {
    val c = LocalChagokColors.current
    VoiceFrame("제${episode}회 마무리", room) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Statement("다음 회에 틀 한마디, 누가 남길까요?", room)
            Detail(if (keep.failed) "녹음하지 못했어요. 마이크 권한이 꺼져 있으면 설정에서 켜 주세요." else "버튼을 누르면 8초 동안 녹음돼요. 옆 사람 목소리도 함께 담길 수 있어요.")
            val perRow = if (room.fit == Fit.TALL) 2 else 4
            Column(Modifier.padding(top = 24.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                keep.people.withIndex().chunked(perRow).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { (i, person) -> PersonPick(person.second, i, Modifier.weight(1f)) { actions.keepBy(person.first) } }
                        repeat(perRow - row.size) { Box(Modifier.weight(1f)) }
                    }
                }
            }
            PillButton("오늘은 패스", c.ink, filled = false, onClick = actions.keepClose)
        }
    }
}

@Composable
private fun PersonPick(name: String, order: Int, modifier: Modifier, onClick: () -> Unit) {
    val tone = personColors(order)
    val press = rememberPress()
    Box(
        modifier.pressScale(press).height(PICK_HEIGHT).clip(ChagokShapes.Control).background(tone.fill).pressable(press, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(name, color = tone.ink, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp, maxLines = 1)
    }
}

/** 남은 시간 막대. 처음부터 끝까지 일정하게 찬다. */
@Composable
internal fun CountBar(seconds: Int, color: Color, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(seconds) { progress.animateTo(1f, tween(seconds * 1_000, easing = LinearEasing)) }
    Box(modifier.fillMaxWidth().height(8.dp).clip(ChagokShapes.Pill).background(color.copy(alpha = 0.18f))) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                transformOrigin = TransformOrigin(0f, 0.5f)
                scaleX = progress.value
            }.background(color),
        )
    }
}

/** 녹음 중임을 숨기지 않는다. 빨간 점, 남은 초, 취소만 있다. */
@Composable
private fun RecordingPlate(episode: Int, who: String, room: Room, actions: VoiceActions) {
    val c = LocalChagokColors.current
    val tall = room.fit == Fit.TALL
    var left by remember { mutableIntStateOf(RECORD_SECONDS) }
    LaunchedEffect(Unit) {
        while (left > 0) {
            delay(1_000)
            left--
        }
    }
    VoiceFrame("제${episode}회 마무리", room) {
        val count = @Composable {
            Text(
                "$left", color = c.warn, fontSize = (if (tall) 170 else 300).sp, lineHeight = (if (tall) 180 else 310).sp, fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = if (tall) 0.dp else 230.dp),
            )
        }
        val side = @Composable {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(18.dp).clip(CircleShape).background(c.warn))
                Text("$who 녹음 중", color = c.ink, fontSize = (if (tall) 30 else 42).sp, fontWeight = FontWeight.ExtraBold)
            }
            Detail("지금 말해 주세요")
            CountBar(RECORD_SECONDS, c.warn, Modifier.padding(vertical = 24.dp))
            PillButton("취소하고 지우기", c.ink, filled = false, onClick = actions.keepCancel)
        }
        if (tall) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                count()
                side()
            }
        } else {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(56.dp), verticalAlignment = Alignment.CenterVertically) {
                count()
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) { side() }
            }
        }
    }
}

@Composable
private fun SavedPlate(episode: Int, clip: VoiceClipUi, room: Room, actions: VoiceActions) {
    val c = LocalChagokColors.current
    VoiceFrame("제${episode}회 마무리", room) {
        CoverStatement(
            room,
            cover = { EpisodeCover(clip.episode, clip.date, room.bigCover(), sub = "${clip.who}의 한마디") },
            modifier = Modifier.weight(1f),
            content = {
                Statement("${clip.who} 목소리를 이 차에 저장했어요", room)
                Detail("다음 방송을 시작할 때 틀 수 있어요.")
                val tall = room.fit == Fit.TALL
                val fontSp = if (tall) 21 else 26
                val pad = if (tall) 18.dp else 32.dp
                Row(Modifier.padding(top = 26.dp), horizontalArrangement = Arrangement.spacedBy(if (tall) 8.dp else 12.dp)) {
                    PillButton("들어 보기", c.ink, filled = true, onClick = actions.keepListen, fontSp = fontSp, pad = pad)
                    PillButton("지우기", c.ink, filled = false, onClick = actions.keepDelete, fontSp = fontSp, pad = pad)
                    PillButton("끝내기", c.ink, filled = false, onClick = actions.keepClose, fontSp = fontSp, pad = pad)
                }
            },
        )
    }
}

@Composable
private fun FullPlate(episode: Int, room: Room, actions: VoiceActions) {
    val c = LocalChagokColors.current
    VoiceFrame("제${episode}회 마무리", room) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Statement("공간이 부족해 이번엔 저장할 수 없어요", room)
            Detail("기존 목소리는 그대로 있어요. 옮기거나 몇 개를 지우면 다시 남길 수 있어요.")
            PillButton("끝내기", c.ink, filled = false, onClick = actions.keepClose, modifier = Modifier.padding(top = 26.dp))
        }
    }
}

private const val RECORD_SECONDS = 8
private val PICK_HEIGHT = 168.dp
