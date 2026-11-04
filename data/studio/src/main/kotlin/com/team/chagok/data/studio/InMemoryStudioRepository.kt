package com.team.chagok.data.studio

import com.team.chagok.core.domain.port.StudioRepository
import com.team.chagok.core.model.EpisodeRecord
import com.team.chagok.core.model.Guest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 회차 기록은 프로세스 메모리, 명부는 바깥(사람 카드 저장소)에서 읽는다.
 * 명부를 여기 박아 두지 않는다 — 가족이 정차 화면에서 사람을 추가·삭제하면 다음 방송에 바로 반영된다.
 *
 * TODO(studio-room): 회차 기록도 저장. 인터페이스가 같아서 app 의 조립 한 줄만 바뀐다.
 */
class InMemoryStudioRepository(
    private val people: suspend () -> List<Guest>,
    /** 이 프로세스 전에 지난 회차 수(저장된 가족 기억·시연용). 다음 회차는 이 다음 번호로 시작한다. */
    private val previousEpisodes: suspend () -> Int = { 0 },
    /** 회차가 저장된 뒤(출연 횟수 올리기 등). */
    private val onSaved: suspend (EpisodeRecord) -> Unit = {},
) : StudioRepository {

    private val lock = Mutex()
    private val episodes = mutableListOf<EpisodeRecord>()

    override suspend fun roster(): List<Guest> = people()

    override suspend fun lastEpisodeNumber(): Int {
        val before = previousEpisodes()
        return lock.withLock { maxOf(before, episodes.maxOfOrNull { it.number } ?: 0) }
    }

    override suspend fun saveEpisode(record: EpisodeRecord) {
        lock.withLock { episodes += record }
        onSaved(record)
    }
}
