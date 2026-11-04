package com.team.chagok.core.domain.memory

import com.team.chagok.core.domain.port.FamilyMemory
import com.team.chagok.core.model.Corner
import com.team.chagok.core.model.EpisodeDigest
import com.team.chagok.core.model.EpisodeRecord
import com.team.chagok.core.model.Guest
import com.team.chagok.core.model.GuestId
import com.team.chagok.core.model.ProfileBook
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 회차 끝 → 정보 뽑기 → 사실 확인 → 프로필에 쌓기 → 다음 회 이어 묻기.
 * 회차 정리는 규칙으로 먼저 저장하고, AI 정리가 근거 검사를 통과하면 바꾼다. AI 가 늦어도 화면이 기다리지 않는다.
 *
 * 저장은 바깥에서: 처음 쓸 때 [load] 로 한 번 읽고, 바뀔 때마다 [onChange] 로 넘긴다([ProfileBookCodec]).
 * 읽기를 생성자에서 하지 않는 이유: 앱 시작(메인 스레드)에 파일·키 저장소를 건드리지 않으려고.
 */
class FamilyMemoryBook(
    private val extractor: FactExtractor,
    private val planner: CallbackPlanner,
    private val digestWriter: DigestWriter? = null,
    private val load: suspend () -> ProfileBook = { ProfileBook() },
    private val onChange: suspend (ProfileBook) -> Unit = {},
) : FamilyMemory {

    private val _book = MutableStateFlow(ProfileBook())
    val book: StateFlow<ProfileBook> = _book.asStateFlow()

    private val loadLock = Mutex()

    @Volatile
    private var loaded = false

    /** 저장된 기억을 읽어 온다. 여러 번 불러도 한 번만 읽는다. */
    suspend fun restore() {
        if (loaded) return
        loadLock.withLock {
            if (!loaded) {
                _book.value = load()
                loaded = true
            }
        }
    }

    suspend fun lastEpisode(): Int {
        restore()
        return _book.value.lastEpisode
    }

    override suspend fun callbacksFor(roster: List<Guest>, episode: Int): List<Corner> {
        restore()
        return planner.plan(_book.value, roster, episode)
    }

    override suspend fun remember(record: EpisodeRecord, roster: List<Guest>) {
        restore()
        val extracted = extractor.extract(record.answers, record.number)
        val grounded = extracted.filter { FactCheck.grounded(it, record.answers) }
        val calledBack = record.cornerIds.mapNotNull(CallbackPlanner::factKeyOf)
        _book.update {
            it.plus(grounded).markCalledBack(calledBack)
                .copy(lastEpisode = maxOf(it.lastEpisode, record.number))
                .withDigest(DigestRules.write(record, roster))
        }
        onChange(_book.value)
        digest(record, roster)?.let { digest ->
            _book.update { it.withDigest(digest) }
            onChange(_book.value)
        }
    }

    private suspend fun digest(record: EpisodeRecord, roster: List<Guest>): EpisodeDigest? {
        val writer = digestWriter?.takeIf { record.answers.isNotEmpty() } ?: return null
        return try {
            writer.write(record, roster, _book.value)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /** 보호자가 "가족 기억 지우기" 를 눌렀다. 회차 번호와 사람 카드는 남긴다(쌓인 정보만 지운다). */
    suspend fun forget() {
        restore()
        _book.update { ProfileBook(lastEpisode = it.lastEpisode, people = it.people) }
        onChange(_book.value)
    }

    /** 지금 명부(사람 카드). 방송 출연자는 여기서 나온다 — 코드에 박힌 가족이 아니다. */
    suspend fun people(): List<Guest> {
        restore()
        return _book.value.people
    }

    /** 사람 카드 추가·수정. 같은 id 면 바꾼다. 호칭이 비면 무시한다. */
    suspend fun savePerson(guest: Guest) {
        if (guest.nickname.isBlank()) return
        restore()
        _book.update { it.withPerson(guest.copy(nickname = guest.nickname.trim())) }
        onChange(_book.value)
    }

    /** 사람 카드 삭제. 그 사람에 대해 쌓인 기억도 같이 지운다. */
    suspend fun removePerson(id: GuestId) {
        restore()
        _book.update { it.withoutPerson(id) }
        onChange(_book.value)
    }

    /** 방송이 끝나면 탄 사람의 출연 횟수를 올린다. */
    suspend fun countAppearances(ids: Collection<GuestId>) {
        restore()
        _book.update { book ->
            book.copy(people = book.people.map { if (it.id in ids) it.copy(appearances = it.appearances + 1) else it })
        }
        onChange(_book.value)
    }
}
