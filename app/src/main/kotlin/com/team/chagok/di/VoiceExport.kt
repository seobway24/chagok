package com.team.chagok.di

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.team.chagok.core.model.VoiceClip
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 보호자가 고른 폴더(USB)에 m4a 로 옮긴다. 옮긴 파일의 보관 책임은 보호자에게 있다. */
internal object VoiceExport {

    fun write(context: Context, tree: Uri, clip: VoiceClip, audio: ByteArray): Boolean = runCatching {
        val resolver = context.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Date(clip.recordedAtMillis))
        val file = DocumentsContract.createDocument(resolver, parent, MIME, "차곡_제${clip.episode}회_${clip.guestName}_$day.m4a")
        checkNotNull(file) { "파일을 만들지 못함" }
        checkNotNull(resolver.openOutputStream(file)) { "파일을 열지 못함" }.use { it.write(audio) }
    }.onFailure { Log.w(TAG, "옮기기 실패: ${it.javaClass.simpleName}") }.isSuccess

    private const val TAG = "ChagokExport"
    private const val MIME = "audio/mp4"
}
