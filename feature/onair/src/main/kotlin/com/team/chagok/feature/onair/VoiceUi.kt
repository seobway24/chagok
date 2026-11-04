package com.team.chagok.feature.onair

import android.net.Uri
import androidx.compose.runtime.Immutable
import com.team.chagok.core.model.VoiceClip
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 목소리 한 개. 소리는 앱 안 보관함에만 있고 화면은 목록 정보만 안다. */
@Immutable
data class VoiceClipUi(val id: String, val episode: Int, val who: String, val whoId: String, val date: String, val seconds: Int)

/** UNKNOWN = 아직 묻지 않음. 시연·미리보기는 [AGREED] 로 시작하지 않고 묻지도 않게 [DECLINED] 를 쓴다. */
enum class ConsentUi { UNKNOWN, AGREED, DECLINED }

@Immutable
data class VoiceUi(
    val consent: ConsentUi = ConsentUi.DECLINED,
    val clips: List<VoiceClipUi> = emptyList(),
    val muted: Set<String> = emptySet(),
    val unreadable: Boolean = false,
    val usedBytes: Long = 0,
)

@Immutable
data class OfferUi(val clip: VoiceClipUi, val rainy: Boolean, val playing: Boolean)

@Immutable
data class KeepUi(
    /** id to 이름. */
    val people: List<Pair<String, String>>,
    val recordingBy: String? = null,
    val saved: VoiceClipUi? = null,
    val full: Boolean = false,
    val failed: Boolean = false,
)

fun VoiceClip.toUi(): VoiceClipUi = VoiceClipUi(
    id, episode, guestName, guestId.value,
    SimpleDateFormat("M월 d일", Locale.KOREA).format(Date(recordedAtMillis)),
    (durationMillis / 1000).toInt().coerceAtLeast(1),
)

/** 방송 흐름 안의 목소리 버튼. 모두 상태 머신을 거친다. */
@Immutable
class VoiceActions(
    val playOffer: () -> Unit = {},
    val skipOffer: () -> Unit = {},
    val keepBy: (String) -> Unit = {},
    val keepCancel: () -> Unit = {},
    val keepListen: () -> Unit = {},
    val keepDelete: () -> Unit = {},
    val keepClose: () -> Unit = {},
)

/** 방송 밖에서 보관함을 보는 길. 구현: app 의 AppContainer. */
interface VoiceLibrary {
    val state: Flow<VoiceUi>
    suspend fun refresh()
    suspend fun reloadBroken()
    suspend fun agree(pin: String)
    suspend fun decline()
    /** 맞으면 true. 여러 번 틀리면 잠시 막힌다. */
    suspend fun checkPin(pin: String): Boolean
    suspend fun listen(id: String)
    fun stopListening()
    suspend fun delete(id: String)
    suspend fun deleteAll()
    suspend fun setMuted(whoId: String, muted: Boolean)
    /** 고른 폴더에 m4a 로 옮긴다. 옮긴 개수를 돌려준다. */
    suspend fun exportTo(tree: Uri): Int
}

object NoVoices : VoiceLibrary {
    override val state: Flow<VoiceUi> = flowOf(VoiceUi())
    override suspend fun refresh() = Unit
    override suspend fun reloadBroken() = Unit
    override suspend fun agree(pin: String) = Unit
    override suspend fun decline() = Unit
    override suspend fun checkPin(pin: String) = false
    override suspend fun listen(id: String) = Unit
    override fun stopListening() = Unit
    override suspend fun delete(id: String) = Unit
    override suspend fun deleteAll() = Unit
    override suspend fun setMuted(whoId: String, muted: Boolean) = Unit
    override suspend fun exportTo(tree: Uri) = 0
}

/** 화면 깊은 곳까지 함께 내려보내는 묶음. */
@Immutable
internal class VoiceBundle(
    val ui: VoiceUi,
    val actions: VoiceActions,
    val library: (suspend VoiceLibrary.() -> Unit) -> Unit,
)
