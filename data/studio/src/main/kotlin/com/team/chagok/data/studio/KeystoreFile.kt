package com.team.chagok.data.studio

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-GCM 암호화. 키는 Android Keystore 밖으로 나오지 않는다. 앞 12바이트가 IV 다.
 * 실패는 예외로 알린다 — 대체 동작은 쓰는 쪽이 정한다.
 */
internal class KeystoreCipher(private val alias: String) {

    fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        return cipher.iv + cipher.doFinal(plain)
    }

    fun decrypt(bytes: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
        }
        return cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES)
    }

    /** 키를 지우면 이 키로 잠근 파일은 누구도 열 수 없다. */
    fun deleteKey() {
        KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(alias)
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}

/** 임시 파일에 쓰고 디스크까지 내린 뒤 교체한다. 어디서 끊겨도 앞 파일이 남는다. */
internal fun File.replaceAtomically(bytes: ByteArray) {
    val tmp = File(parentFile, "$name.part")
    FileOutputStream(tmp).use {
        it.write(bytes)
        it.fd.sync()
    }
    Files.move(tmp.toPath(), toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
}

/**
 * 글 하나를 암호화해 파일 하나에 둔다.
 *
 * noBackupFilesDir 라 클라우드 백업으로도 나가지 않는다. 메인 스레드에서 부르지 않는다(파일 I/O).
 */
internal class KeystoreFile(context: Context, fileName: String, alias: String) {

    private val file = File(context.applicationContext.noBackupFilesDir, fileName)
    private val cipher = KeystoreCipher(alias)

    fun read(): String? = if (file.exists()) cipher.decrypt(file.readBytes()).toString(Charsets.UTF_8) else null

    fun write(text: String) = file.replaceAtomically(cipher.encrypt(text.toByteArray(Charsets.UTF_8)))

    fun delete() {
        file.delete()
    }
}
