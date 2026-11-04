package com.team.chagok.data.studio

import android.content.Context
import android.util.Log
import com.team.chagok.core.domain.port.VoiceVault
import com.team.chagok.core.domain.voice.VoiceIndexCodec
import com.team.chagok.core.model.GuestId
import com.team.chagok.core.model.VoiceClip
import com.team.chagok.core.model.VoiceShelfState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 목소리는 앱 전용 저장소 안에만 둔다. 서버로 보내지 않고 백업에도 나가지 않는다.
 *
 * ```
 * noBackupFilesDir/voices/
 *   index.bin            색인 (암호화)
 *   clips/{id}.m4a.enc   머리글 한 줄 + 소리 (파일마다 AES-GCM)
 * ```
 * 색인을 읽지 못하면 파일을 지우지 않고 쓰기를 잠근다. 색인이 정상일 때만 빠진 파일을 머리글로 되살린다.
 * 가족 기억과 키가 달라서 기억이 깨지거나 지워져도 목소리는 남는다.
 */
class EncryptedVoiceVault(context: Context) : VoiceVault {

    private val root = File(context.applicationContext.noBackupFilesDir, "voices")
    private val clips = File(root, "clips")
    private val index = File(root, "index.bin")
    private val cipher = KeystoreCipher(ALIAS)
    private val lock = Mutex()

    private var loaded: VoiceShelfState? = null

    override suspend fun shelf(): VoiceShelfState = io { current() }

    override suspend fun add(clip: VoiceClip, audio: ByteArray): Boolean = io {
        val shelf = current()
        if (shelf.unreadable) return@io false
        runCatching {
            clips.mkdirs()
            val header = (VoiceIndexCodec.encodeClip(clip) + "\n").toByteArray(Charsets.UTF_8)
            fileOf(clip.id).replaceAtomically(cipher.encrypt(header + audio))
            persist(shelf.copy(clips = shelf.clips + clip))
        }.onFailure { warn("저장 실패", it) }.isSuccess
    }

    override suspend fun read(id: String): ByteArray? = io {
        runCatching {
            val plain = cipher.decrypt(fileOf(id).readBytes())
            plain.copyOfRange(plain.indexOf(NEW_LINE) + 1, plain.size)
        }.onFailure { warn("읽기 실패", it) }.getOrNull()
    }

    override suspend fun markPlayed(id: String) = update { shelf ->
        shelf.copy(clips = shelf.clips.map { if (it.id == id) it.copy(playCount = it.playCount + 1) else it })
    }

    /**
     * 파일에 삭제 표시 → 색인에서 뺌 → 파일 삭제 순서다. 어디서 끊겨도 다음 실행의 [settleDeletes] 가 마무리해서
     * 지운 목소리가 되살아나지 않는다. 색인을 못 읽을 때는 아무것도 지우지 않는다.
     */
    override suspend fun delete(id: String) = io {
        val shelf = current()
        if (shelf.unreadable) return@io
        val file = fileOf(id)
        val doomed = doomedOf(id)
        runCatching {
            check(!file.exists() || file.renameTo(doomed)) { "삭제 표시 실패" }
            persist(shelf.copy(clips = shelf.clips.filterNot { it.id == id }))
            doomed.delete()
        }.onFailure {
            doomed.renameTo(file)
            warn("삭제 실패", it)
        }
        Unit
    }

    /** 폴더와 키를 함께 지운다. 키가 없으면 남은 조각도 열 수 없다. */
    override suspend fun deleteAll() = io {
        root.deleteRecursively()
        cipher.deleteKey()
        loaded = VoiceShelfState()
    }

    override suspend fun setMuted(guest: GuestId, muted: Boolean) = update { shelf ->
        shelf.copy(muted = if (muted) shelf.muted + guest else shelf.muted - guest)
    }

    override suspend fun freeBytes(): Long = io { root.apply { mkdirs() }.usableSpace }

    /** 잠겼던 목록을 다시 읽는다. 화면의 "다시 읽기". */
    suspend fun reload(): VoiceShelfState = io {
        loaded = null
        current()
    }

    private fun current(): VoiceShelfState = loaded ?: load().also { loaded = it }

    private fun load(): VoiceShelfState {
        clips.listFiles { f -> f.name.endsWith(".part") }?.forEach(File::delete)
        val shelf = runCatching {
            if (index.exists()) VoiceIndexCodec.decode(cipher.decrypt(index.readBytes()).toString(Charsets.UTF_8)) else VoiceShelfState()
        }.getOrElse {
            warn("색인 읽기 실패, 쓰기 잠금", it)
            return VoiceShelfState(unreadable = true)
        }
        return recover(shelf)
    }

    /** 파일은 저장됐는데 색인 전에 끊긴 목소리를 머리글로 되살린다. */
    private fun recover(shelf: VoiceShelfState): VoiceShelfState {
        val known = shelf.clips.map { it.id }.toSet()
        settleDeletes(known)
        val found = clips.listFiles { f -> f.name.endsWith(SUFFIX) }.orEmpty()
            .filter { it.name.removeSuffix(SUFFIX) !in known }
            .mapNotNull { file ->
                runCatching {
                    val plain = cipher.decrypt(file.readBytes())
                    VoiceIndexCodec.decodeClip(plain.copyOfRange(0, plain.indexOf(NEW_LINE)).toString(Charsets.UTF_8))
                }.onFailure { warn("복구 실패", it) }.getOrNull()
            }
        if (found.isEmpty()) return shelf
        val merged = shelf.copy(clips = (shelf.clips + found).sortedBy { it.recordedAtMillis })
        runCatching { writeIndex(merged) }.onFailure { warn("복구 저장 실패", it) }
        return merged
    }

    /** 삭제 표시만 남은 파일을 정리한다. 색인에 그대로 있으면 삭제가 확정되기 전에 끊긴 것이라 되돌린다. */
    private fun settleDeletes(known: Set<String>) {
        clips.listFiles { f -> f.name.endsWith(DOOMED_SUFFIX) }.orEmpty().forEach { file ->
            val id = file.name.removeSuffix(DOOMED_SUFFIX)
            if (id in known) file.renameTo(fileOf(id)) else file.delete()
        }
    }

    private suspend fun update(change: (VoiceShelfState) -> VoiceShelfState) = io {
        val shelf = current()
        if (!shelf.unreadable) runCatching { persist(change(shelf)) }.onFailure { warn("색인 저장 실패", it) }
    }

    private fun persist(shelf: VoiceShelfState) {
        writeIndex(shelf)
        loaded = shelf
    }

    private fun writeIndex(shelf: VoiceShelfState) {
        root.mkdirs()
        index.replaceAtomically(cipher.encrypt(VoiceIndexCodec.encode(shelf).toByteArray(Charsets.UTF_8)))
    }

    private fun fileOf(id: String) = File(clips, id + SUFFIX)

    private fun doomedOf(id: String) = File(clips, id + DOOMED_SUFFIX)

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { lock.withLock { block() } }

    /** 소리·이름을 남기지 않고 예외 종류만 적는다. */
    private fun warn(what: String, e: Throwable) = Log.w(TAG, "$what: ${e.javaClass.simpleName}")

    private companion object {
        const val TAG = "ChagokVoice"
        const val ALIAS = "chagok-voices"
        const val SUFFIX = ".m4a.enc"
        const val DOOMED_SUFFIX = ".doomed"
        const val NEW_LINE: Byte = 10
    }
}
