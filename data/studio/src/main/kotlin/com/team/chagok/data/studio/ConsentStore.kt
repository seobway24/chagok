package com.team.chagok.data.studio

import android.content.Context
import android.util.Log
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** 보호자 선택. null 은 아직 묻지 않음. */
enum class ConsentChoice { AGREED, DECLINED }

/**
 * 보호자 동의 날짜·문구 버전과 비밀번호 검증값을 앱 안에 암호화해 둔다.
 * 비밀번호 자체는 저장하지 않는다. 메인 스레드에서 부르지 않는다(파일 I/O).
 */
class ConsentStore(context: Context) {

    private val file = KeystoreFile(context, "consent.bin", "chagok-consent")
    private val random = SecureRandom()

    private var wrongTries = 0
    private var lockedUntil = 0L

    fun choice(): ConsentChoice? = runCatching {
        file.read()?.lines()?.firstOrNull()?.let(ConsentChoice::valueOf)
    }.onFailure { Log.w(TAG, "동의 읽기 실패: ${it.javaClass.simpleName}") }.getOrNull()

    fun agree(pin: String, atMillis: Long) {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        file.write(listOf(ConsentChoice.AGREED.name, atMillis, TEXT_VERSION, encode(salt), encode(hash(pin, salt))).joinToString("\n"))
    }

    fun decline() = file.write(ConsentChoice.DECLINED.name)

    /** 틀린 횟수가 쌓이면 잠시 막는다. 잠겨 있으면 맞는 번호도 false. */
    fun checkPin(pin: String, now: Long): Boolean {
        if (now < lockedUntil) return false
        val saved = file.read()?.lines()?.takeIf { it.size == FIELDS } ?: return false
        val ok = MessageDigest.isEqual(hash(pin, decode(saved[3])), decode(saved[4]))
        if (ok) {
            wrongTries = 0
        } else if (++wrongTries >= MAX_TRIES) {
            wrongTries = 0
            lockedUntil = now + LOCK_MILLIS
        }
        return ok
    }

    private fun hash(pin: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance(ALGORITHM)
            .generateSecret(PBEKeySpec(pin.toCharArray(), salt, ROUNDS, KEY_BITS)).encoded

    private fun encode(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)
    private fun decode(text: String) = Base64.getDecoder().decode(text)

    private companion object {
        const val TAG = "ChagokConsent"
        const val TEXT_VERSION = 1
        const val FIELDS = 5
        const val SALT_BYTES = 16
        const val KEY_BITS = 256
        const val ROUNDS = 50_000
        const val ALGORITHM = "PBKDF2WithHmacSHA256"
        const val MAX_TRIES = 5
        const val LOCK_MILLIS = 30_000L
    }
}
