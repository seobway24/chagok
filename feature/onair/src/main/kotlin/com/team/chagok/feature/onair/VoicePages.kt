package com.team.chagok.feature.onair

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.team.chagok.feature.onair.theme.ChagokShapes
import com.team.chagok.feature.onair.theme.LocalChagokColors
import java.util.Locale

// 정차 중 목소리 관리: 목소리 길 · 저장 공간 · 사람별 끄기 · 보호자 확인. 주행이 시작되면 Body 가 닫는다.

private enum class VoicePage { ROAD, STORE, MUTE }

/**
 * 차량 OS 는 폴더 선택 요청을 바로 닫는 빈 화면으로 보낸다. 에뮬레이터에서 확인했다.
 * 그래서 실제 선택 화면 앱을 직접 지정한다.
 */
private class PickFolder : ActivityResultContracts.OpenDocumentTree() {
    override fun createIntent(context: Context, input: Uri?): Intent =
        super.createIntent(context, input).setPackage(FILE_PICKER)
}

/** 방송에는 마이크가 필요하다. 없으면 시작할 때 묻고, 거절해도 방송은 그대로 시작한다. */
@Composable
internal fun rememberStartWithMic(onStart: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    return {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!granted) ask.launch(Manifest.permission.RECORD_AUDIO)
        onStart()
    }
}

private fun Context.hasFilePicker(): Boolean =
    Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).setPackage(FILE_PICKER).resolveActivity(packageManager) != null

@Composable
internal fun VoicePages(voice: VoiceBundle, room: Room, onClose: () -> Unit) {
    var page by remember { mutableStateOf(VoicePage.ROAD) }
    var guarded by remember { mutableStateOf<(() -> Unit)?>(null) }
    var askTitle by remember { mutableStateOf("") }
    val guard = { title: String, action: () -> Unit ->
        askTitle = title
        guarded = action
    }
    // 비밀번호 화면이 뜨는 동안에도 선택기 연결이 살아 있도록 여기서 만든다
    var notice by remember { mutableStateOf<String?>(null) }
    val pick = rememberLauncherForActivityResult(PickFolder()) { tree ->
        if (tree != null) {
            voice.library {
                val moved = exportTo(tree)
                notice = if (moved > 0) "${moved}개를 옮겼어요" else "옮기지 못했어요"
            }
        }
    }
    val context = LocalContext.current
    val export = {
        if (context.hasFilePicker()) pick.launch(null) else notice = "이 차에서는 파일 선택 화면을 열 수 없어요"
    }
    val ui = voice.ui
    // 출발하면 Body 가 이 화면을 닫는다. 듣던 소리도 함께 멈춘다
    DisposableEffect(Unit) { onDispose { voice.library { stopListening() } } }
    guarded?.let { action ->
        PinCheck(askTitle, room, voice.library, onPassed = { guarded = null; action() }, onCancel = { guarded = null })
        return
    }
    when {
        ui.consent != ConsentUi.AGREED -> ConsentPane(room, voice.library, onLater = onClose)
        ui.unreadable -> BrokenPlate(room, voice.library, onClose)
        page == VoicePage.ROAD -> RoadPage(voice, room, guard, onStore = { page = VoicePage.STORE }, onClose)
        page == VoicePage.STORE -> StorePage(voice, room, notice, guard, export, onMute = { page = VoicePage.MUTE }, onBack = { page = VoicePage.ROAD })
        else -> MutePage(voice, room, onBack = { page = VoicePage.STORE })
    }
}

@Composable
private fun PageBar(title: String, room: Room, onBack: () -> Unit, trailing: @Composable () -> Unit = {}) {
    val c = LocalChagokColors.current
    val edge = room.edge
    Row(
        Modifier.fillMaxWidth().padding(horizontal = edge, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PillButton("뒤로", c.ink, filled = false, onClick = onBack, fontSp = 18)
        Text(title, color = c.ink, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

// ---- 목소리 길 ----

@Composable
private fun RoadPage(voice: VoiceBundle, room: Room, guard: (String, () -> Unit) -> Unit, onStore: () -> Unit, onClose: () -> Unit) {
    val c = LocalChagokColors.current
    val clips = voice.ui.clips.asReversed()
    var chosenId by remember { mutableStateOf<String?>(null) }
    val chosen = clips.firstOrNull { it.id == chosenId } ?: clips.firstOrNull()
    val edge = room.edge
    Column(Modifier.fillMaxSize()) {
        PageBar("목소리 길", room, onBack = { voice.library { stopListening() }; onClose() }) {
            Text("${clips.size}개", color = c.sub, fontSize = 20.sp)
            PillButton("저장 공간", c.ink, filled = false, onClick = onStore, fontSp = 18)
        }
        if (chosen == null) {
            val tone = c.plainPlate
            Plate(tone, Modifier.weight(1f).fillMaxWidth().padding(start = edge, end = edge, bottom = edge), contentPadding = 40.dp) {
                Text("아직 남긴 목소리가 없어요", color = tone.ink, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold)
                Text("방송을 마치고 차를 세우면 한마디를 남길 수 있어요.", color = tone.ink, fontSize = 24.sp, modifier = Modifier.padding(top = 12.dp))
            }
            return@Column
        }
        val list = @Composable { m: Modifier ->
            LazyColumn(m, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(clips, key = { it.id }) { clip -> ClipRow(clip, clip.id == chosen.id) { chosenId = clip.id } }
            }
        }
        val detail = @Composable { m: Modifier -> ClipDetail(chosen, voice, guard, m) }
        if (room.fit == Fit.TALL) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(start = edge, end = edge, bottom = edge), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                detail(Modifier.fillMaxWidth())
                list(Modifier.weight(1f).fillMaxWidth())
            }
        } else {
            Row(Modifier.weight(1f).fillMaxWidth().padding(start = edge, end = edge, bottom = edge), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                list(Modifier.weight(1f).fillMaxHeight())
                detail(Modifier.width(DETAIL_WIDTH).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun ClipRow(clip: VoiceClipUi, chosen: Boolean, onClick: () -> Unit) {
    val c = LocalChagokColors.current
    val press = rememberPress()
    Row(
        Modifier.pressScale(press).fillMaxWidth().heightIn(min = ROW_HEIGHT).clip(ChagokShapes.Control)
            .background(if (chosen) c.ink else c.panel)
            .pressable(press, onClick = onClick).padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val ink = if (chosen) c.background else c.ink
        Text("제${clip.episode}회", color = ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(clip.who, color = ink, fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(clip.date, color = if (chosen) c.background else c.sub, fontSize = 18.sp)
        Text("${clip.seconds}초", color = if (chosen) c.background else c.sub, fontSize = 18.sp)
    }
}

@Composable
private fun ClipDetail(clip: VoiceClipUi, voice: VoiceBundle, guard: (String, () -> Unit) -> Unit, modifier: Modifier) {
    val tone = LocalChagokColors.current.plainPlate
    Plate(tone, modifier, contentPadding = 26.dp) {
        Text("제${clip.episode}회", color = tone.ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(clip.who, color = tone.ink, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${clip.date}, ${clip.seconds}초", color = tone.ink, fontSize = 22.sp)
        Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("듣기", tone.ink, filled = true, onClick = { voice.library { listen(clip.id) } }, fontSp = 22, leading = { PlayGlyph(it, 20.dp) })
            PillButton("지우기", tone.ink, filled = false, fontSp = 22, onClick = {
                guard("이 목소리를 지우려면 보호자 비밀번호를 눌러 주세요") { voice.library { delete(clip.id) } }
            })
        }
    }
}

// ---- 저장 공간 ----

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StorePage(voice: VoiceBundle, room: Room, notice: String?, guard: (String, () -> Unit) -> Unit, onExport: () -> Unit, onMute: () -> Unit, onBack: () -> Unit) {
    val tone = LocalChagokColors.current.plainPlate
    val edge = room.edge
    var confirmAll by remember { mutableStateOf(false) }
    val used = voice.ui.usedBytes.let {
        if (it < BYTES_PER_MB) "${it / 1024}KB" else String.format(Locale.KOREA, "%.1fMB", it / BYTES_PER_MB)
    }
    Column(Modifier.fillMaxSize()) {
        PageBar("저장 공간", room, onBack)
        Plate(tone, Modifier.weight(1f).fillMaxWidth().padding(start = edge, end = edge, bottom = edge), contentPadding = 32.dp) {
            Text("목소리 ${voice.ui.clips.size}개, $used", color = tone.ink, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold)
            Text("5,000개까지 저장할 수 있어요. 오래된 목소리를 앱이 혼자 지우지 않아요.", color = tone.ink, fontSize = 22.sp, modifier = Modifier.padding(top = 10.dp))
            Text("이 차의 앱 안에만 있어요. 앱을 지우거나 차를 바꾸면 사라지니 미리 옮겨 두세요.", color = tone.ink, fontSize = 22.sp, modifier = Modifier.padding(top = 6.dp))
            Text("USB로 옮길 때는 맨 위가 아니라 USB 안의 폴더를 골라 주세요.", color = tone.ink, fontSize = 22.sp, modifier = Modifier.padding(top = 6.dp))
            notice?.let { Text(it, color = tone.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp)) }
            FlowRow(Modifier.padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton("USB로 옮기기", tone.ink, filled = true, fontSp = 22, onClick = {
                    guard("옮기려면 보호자 비밀번호를 눌러 주세요", onExport)
                })
                PillButton("사람별 재생 끄기", tone.ink, filled = false, onClick = onMute, fontSp = 22)
                PillButton(if (confirmAll) "한 번 더 누르면 전체 삭제" else "전체 지우기", tone.ink, filled = false, fontSp = 22, onClick = {
                    if (confirmAll) {
                        confirmAll = false
                        guard("전체를 지우려면 보호자 비밀번호를 눌러 주세요") { voice.library { deleteAll() } }
                    } else {
                        confirmAll = true
                    }
                })
            }
        }
    }
}

// ---- 사람별 끄기 ----

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MutePage(voice: VoiceBundle, room: Room, onBack: () -> Unit) {
    val tone = LocalChagokColors.current.plainPlate
    val edge = room.edge
    val people = voice.ui.clips.distinctBy { it.whoId }
    Column(Modifier.fillMaxSize()) {
        PageBar("사람별 재생 끄기", room, onBack)
        Plate(tone, Modifier.weight(1f).fillMaxWidth().padding(start = edge, end = edge, bottom = edge), contentPadding = 32.dp) {
            Text("방송에서 틀 목소리를 고르세요", color = tone.ink, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
            FlowRow(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                people.forEach { clip ->
                    val off = clip.whoId in voice.ui.muted
                    PillButton(clip.who + if (off) " 끔" else "", tone.ink, filled = !off, onClick = { voice.library { setMuted(clip.whoId, !off) } })
                }
            }
            Text("끈 사람의 목소리는 목소리 길에 남고, 방송 전에 제안하지 않아요.", color = tone.ink, fontSize = 22.sp, modifier = Modifier.padding(top = 18.dp))
        }
    }
}

@Composable
private fun BrokenPlate(room: Room, library: (suspend VoiceLibrary.() -> Unit) -> Unit, onClose: () -> Unit) {
    val tone = LocalChagokColors.current.pausePlate
    val edge = room.edge
    Column(Modifier.fillMaxSize()) {
        PageBar("목소리 길", room, onClose)
        Plate(tone, Modifier.weight(1f).fillMaxWidth().padding(start = edge, end = edge, bottom = edge), contentPadding = 40.dp) {
            Text("목소리 목록을 지금 읽지 못했어요", color = tone.ink, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold)
            Text("파일은 지우지 않았어요. 앱을 다시 켜 주세요. 방송은 그대로 할 수 있어요.", color = tone.ink, fontSize = 24.sp, modifier = Modifier.padding(top = 12.dp))
            PillButton("다시 읽기", tone.ink, filled = false, onClick = { library { reloadBroken() } }, modifier = Modifier.padding(top = 24.dp))
        }
    }
}

// ---- 보호자 동의 · 비밀번호 ----

/** 처음 한 번 묻는다. 무엇을, 어디에 저장하는지와 앱을 지우면 사라진다는 점을 먼저 알린다. */
@Composable
internal fun ConsentPane(room: Room, library: (suspend VoiceLibrary.() -> Unit) -> Unit, onLater: (() -> Unit)? = null) {
    val c = LocalChagokColors.current
    val tone = c.plainPlate
    val edge = room.edge
    var guardian by remember { mutableStateOf(false) }
    var settingPin by remember { mutableStateOf(false) }
    var firstPin by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var welcomed by remember { mutableStateOf(false) }
    if (!welcomed) {
        WelcomePane(room, onGo = { welcomed = true })
        return
    }
    if (settingPin) {
        val first = firstPin
        PinPad(
            title = message ?: if (first == null) "삭제와 옮기기에 쓸 보호자 비밀번호를 정해 주세요" else "한 번 더 눌러 주세요",
            room = room,
            onCancel = { settingPin = false; firstPin = null; message = null },
            onDone = { pin ->
                when {
                    first == null -> { firstPin = pin; message = null }
                    first == pin -> library { agree(pin) }
                    else -> { firstPin = null; message = "번호가 달라요. 처음부터 눌러 주세요" }
                }
            },
        )
        return
    }
    Column(Modifier.fillMaxSize().padding(start = edge, end = edge, bottom = edge)) {
        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("처음 설정", color = c.ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Plate(tone, Modifier.weight(1f).fillMaxWidth(), contentPadding = 32.dp) {
            Text("가족의 한마디를 이 차에 남겨도 될까요?", color = tone.ink, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 50.sp)
            Text(
                "차가 멈춘 뒤, 누른 사람의 말 8초만 앱 안에 저장합니다. 차 밖으로 자동으로 나가지 않고, 앱을 지우면 함께 사라집니다.",
                color = tone.ink, fontSize = 22.sp, lineHeight = 30.sp, modifier = Modifier.padding(top = 12.dp),
            )
            PillButton(
                (if (guardian) "확인함: " else "") + "만 14세 미만 아이의 법정대리인이며 녹음에 동의합니다",
                tone.ink, filled = guardian, fontSp = 20, onClick = { guardian = !guardian },
                modifier = Modifier.padding(top = 18.dp).fillMaxWidth(),
            )
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (guardian) PillButton("동의하고 비밀번호 정하기", tone.ink, filled = true, fontSp = 22, onClick = { settingPin = true })
                PillButton("녹음 없이 쓰기", tone.ink, filled = false, fontSp = 22, onClick = {
                    library { decline() }
                    onLater?.invoke()
                })
            }
        }
    }
}

@Composable
private fun PinCheck(title: String, room: Room, library: (suspend VoiceLibrary.() -> Unit) -> Unit, onPassed: () -> Unit, onCancel: () -> Unit) {
    var wrong by remember { mutableStateOf(false) }
    PinPad(if (wrong) "번호가 달라요. 다시 눌러 주세요" else title, room, onCancel) { pin ->
        library {
            if (checkPin(pin)) onPassed() else wrong = true
        }
    }
}

/** 숫자 네 자리. 정차 중에만 나온다. 입력한 번호는 화면에 보이지 않는다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PinPad(title: String, room: Room, onCancel: () -> Unit, onDone: (String) -> Unit) {
    val tone = LocalChagokColors.current.plainPlate
    val edge = room.edge
    var pin by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(edge)) {
        Plate(tone, Modifier.fillMaxSize(), contentPadding = 28.dp) {
            Text(title, color = tone.ink, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 40.sp)
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(PIN_LENGTH) { i ->
                    Box(Modifier.size(22.dp).clip(ChagokShapes.Pill).background(if (i < pin.length) tone.ink else tone.fill).border(2.dp, tone.ink, ChagokShapes.Pill))
                }
            }
            FlowRow(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DIGITS.forEach { digit ->
                    PillButton(digit, tone.ink, filled = false, fontSp = 28, modifier = Modifier.width(DIGIT_WIDTH), onClick = {
                        val next = pin + digit
                        if (next.length == PIN_LENGTH) {
                            pin = ""
                            onDone(next)
                        } else {
                            pin = next
                        }
                    })
                }
                PillButton("취소", tone.ink, filled = false, fontSp = 28, onClick = onCancel)
            }
        }
    }
}

private const val FILE_PICKER = "com.android.documentsui"
private val DIGITS = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
private const val PIN_LENGTH = 4
private const val BYTES_PER_MB = 1_048_576f
private val DIGIT_WIDTH = 96.dp
private val DETAIL_WIDTH = 380.dp
private val ROW_HEIGHT = 72.dp
