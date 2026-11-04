package com.team.chagok.data.studio

import android.content.Context
import android.util.Log
import com.team.chagok.core.domain.memory.ProfileBookCodec
import com.team.chagok.core.model.ProfileBook

/**
 * 가족 기억을 차 안에만 암호화해 저장한다.
 *
 * Room 대신 파일 하나: 기억은 수 KB 이고 통째로 읽고 쓴다 — 쿼리가 필요 없다.
 * 메인 스레드에서 부르지 않는다(파일 I/O). 읽기에 실패하면 방송은 빈 기억으로 이어 가되,
 * 파일을 덮어쓰지 않도록 쓰기를 잠근다 — 다음 실행 때 읽힐 수 있는 기억을 지키기 위해서다.
 */
class FamilyMemoryStore(context: Context) {

    private val file = KeystoreFile(context, FILE_NAME, ALIAS)

    @Volatile
    private var writeLocked = false

    fun load(): ProfileBook = runCatching {
        val book = file.read()?.let(ProfileBookCodec::decode) ?: ProfileBook()
        writeLocked = false
        book
    }.getOrElse {
        Log.w(TAG, "가족 기억 읽기 실패 → 쓰기 잠금: ${it.javaClass.simpleName}")
        writeLocked = true
        ProfileBook()
    }

    fun save(book: ProfileBook) {
        if (writeLocked) return
        runCatching { file.write(ProfileBookCodec.encode(book)) }
            .onFailure { Log.w(TAG, "가족 기억 저장 실패: ${it.javaClass.simpleName}") }
    }

    private companion object {
        const val TAG = "ChagokMemory"
        const val FILE_NAME = "family-memory.bin"
        const val ALIAS = "chagok-family-memory"
    }
}
