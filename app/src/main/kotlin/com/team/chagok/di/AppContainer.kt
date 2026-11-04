package com.team.chagok.di

import android.content.Context
import android.net.Uri
import com.team.chagok.core.domain.voice.VoiceDesk
import com.team.chagok.data.studio.ConsentChoice
import com.team.chagok.data.studio.ConsentStore
import com.team.chagok.data.studio.EncryptedVoiceVault
import com.team.chagok.feature.onair.ConsentUi
import com.team.chagok.feature.onair.VoiceLibrary
import com.team.chagok.feature.onair.VoiceUi
import com.team.chagok.feature.onair.toUi
import com.team.chagok.platform.voice.MediaVoicePlayer
import com.team.chagok.platform.voice.MediaVoiceRecorder
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import android.util.Log
import com.team.chagok.BuildConfig
import com.team.chagok.content.BuiltinShow
import com.team.chagok.core.domain.port.MicInput
import com.team.chagok.core.domain.port.SignalSource
import com.team.chagok.core.domain.show.ShowDirector
import com.team.chagok.core.domain.show.ShowEngine
import com.team.chagok.core.domain.show.ShowStateMachine
import com.team.chagok.core.domain.signal.ManualSignalSource
import com.team.chagok.core.model.SignalEvent
import com.team.chagok.core.privacy.EgressLog
import com.team.chagok.core.privacy.GuardedQuip
import com.team.chagok.core.privacy.Masker
import com.team.chagok.core.privacy.SafetyFilter
import com.team.chagok.core.quip.ChainQuip
import com.team.chagok.core.quip.QuipEngine
import com.team.chagok.core.quip.TemplateQuip
import com.team.chagok.data.studio.InMemoryStudioRepository
import com.team.chagok.platform.llm.PleosCredentials
import com.team.chagok.platform.llm.PleosLlmProbe
import com.team.chagok.platform.llm.PleosLlmQuip
import com.team.chagok.core.model.AiSettings
import com.team.chagok.data.studio.AiSettingsStore
import com.team.chagok.feature.onair.AiStatusUi
import com.team.chagok.platform.llm.RemoteClient
import com.team.chagok.platform.llm.RemoteConfig
import com.team.chagok.platform.llm.RemoteQuip
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import com.team.chagok.core.model.SignalCategory
import com.team.chagok.core.privacy.PrivacyGate
import com.team.chagok.core.quip.QuipRequest
import kotlin.time.Duration.Companion.milliseconds
import com.team.chagok.platform.navi.PleosNaviProbe
import com.team.chagok.platform.navi.PleosRouteSource
import com.team.chagok.platform.vehicle.PleosSignalSource
import com.team.chagok.platform.voice.PleosHostVoice
import com.team.chagok.core.domain.show.CaptionBoard
import com.team.chagok.platform.voice.PleosMicInput
import com.team.chagok.core.domain.memory.CallbackPlanner
import com.team.chagok.core.domain.memory.ChainFactExtractor
import com.team.chagok.core.domain.memory.FamilyMemoryBook
import com.team.chagok.core.domain.memory.ProfileCards
import com.team.chagok.core.domain.memory.RuleFactExtractor
import com.team.chagok.data.studio.FamilyMemoryStore
import com.team.chagok.core.model.Guest
import com.team.chagok.core.model.GuestId
import com.team.chagok.feature.onair.EpisodeRecapUi
import com.team.chagok.feature.onair.FamilyProfileUi
import com.team.chagok.feature.onair.PersonCard
import com.team.chagok.platform.llm.RemoteDigestWriter
import com.team.chagok.platform.llm.RemoteFactExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/**
 * 수동 DI. 모듈을 조립하는 유일한 곳이다.
 *
 * 구현을 바꿀 때(예: 저장소를 Room 으로, 외부 LLM 카트리지 추가) 이 파일만 고친다.
 * Hilt 를 쓰지 않은 이유: 그래프가 작고, 빌드 시간·주석 처리 부담 대비 얻는 것이 적다.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val manualSignals = ManualSignalSource()
    val egressLog = EgressLog()

    private val studio: InMemoryStudioRepository = InMemoryStudioRepository(
        // 명부 = 사람 카드 저장소. 가족이 정차 화면에서 직접 관리한다
        people = { familyMemory.people() },
        // 프로세스를 다시 띄워도 회차 번호가 이어진다(가족 기억에 같이 저장)
        previousEpisodes = { familyMemory.lastEpisode() },
        onSaved = { familyMemory.countAppearances(it.guestIds) },
    )
    private val voice by lazy { PleosHostVoice(appContext) }
    /** 지금 방송 중인 엔진. 리허설 마이크가 열린 코너를 보고 대답을 고른다. */
    @Volatile
    private var currentEngine: ShowEngine? = null

    private val mic: MicInput by lazy {
        DemoMic.orNull { currentEngine?.state?.value } ?: PleosMicInput(appContext)
    }

    /** 화면 자막: 진행자가 읽는 말 · 방금 들은 대답. */
    val captions = CaptionBoard()

    /** 실제 차량 신호 + 시연 버튼. 에뮬레이터 주입이 막혀도 같은 길로 들어간다. */
    private val signals = object : SignalSource {
        override val events: Flow<SignalEvent> =
            merge(PleosSignalSource(appContext).events, PleosRouteSource(appContext).events, manualSignals.events)
    }

    fun newShowEngine(): ShowEngine {
        val skin = BuiltinShow.skins.first()
        val director = ShowDirector(voice = captions.voice(voice), mic = captions.mic(mic), quip = quipEngine(), skin = skin)
        return ShowEngine(
            machine = ShowStateMachine(BuiltinShow.basicPack),
            director = director,
            signals = signals,
            studio = studio,
            memory = familyMemory,
            voices = voiceDesk,
        ).also { currentEngine = it }
    }

    // ---- 목소리: 앱 안에만 저장. 서버로 보내지 않는다 ----

    private val vault by lazy { EncryptedVoiceVault(appContext) }
    private val consentStore by lazy { ConsentStore(appContext) }
    private val player by lazy { MediaVoicePlayer() }
    private val voiceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var listening: Job? = null

    private suspend fun consent() = withContext(Dispatchers.IO) { consentStore.choice() }

    private val voiceDesk by lazy {
        VoiceDesk(
            vault = vault,
            recorder = MediaVoiceRecorder(appContext),
            player = player,
            consented = { consent() == ConsentChoice.AGREED },
            newId = { UUID.randomUUID().toString() },
        )
    }

    private val voiceState = MutableStateFlow(VoiceUi())

    val voiceLibrary = object : VoiceLibrary {
        override val state: Flow<VoiceUi> = voiceState

        override suspend fun refresh() {
            val shelf = vault.shelf()
            voiceState.value = VoiceUi(
                consent = when (consent()) {
                    ConsentChoice.AGREED -> ConsentUi.AGREED
                    ConsentChoice.DECLINED -> ConsentUi.DECLINED
                    null -> ConsentUi.UNKNOWN
                },
                clips = shelf.clips.map { it.toUi() },
                muted = shelf.muted.map { it.value }.toSet(),
                unreadable = shelf.unreadable,
                usedBytes = shelf.clips.sumOf { it.durationMillis } * AAC_BYTES_PER_SECOND / 1000,
            )
        }

        override suspend fun reloadBroken() {
            vault.reload()
            refresh()
        }

        override suspend fun agree(pin: String) {
            withContext(Dispatchers.IO) { consentStore.agree(pin, System.currentTimeMillis()) }
            refresh()
        }

        override suspend fun decline() {
            withContext(Dispatchers.IO) { consentStore.decline() }
            refresh()
        }

        override suspend fun checkPin(pin: String) =
            withContext(Dispatchers.Default) { consentStore.checkPin(pin, System.currentTimeMillis()) }

        override suspend fun listen(id: String) {
            listening?.cancel()
            listening = voiceScope.launch { vault.read(id)?.let { player.play(it) } }
        }

        override fun stopListening() {
            listening?.cancel()
            player.stop()
        }

        override suspend fun delete(id: String) {
            vault.delete(id)
            refresh()
        }

        override suspend fun deleteAll() {
            vault.deleteAll()
            refresh()
        }

        override suspend fun setMuted(whoId: String, muted: Boolean) {
            vault.setMuted(GuestId(whoId), muted)
            refresh()
        }

        override suspend fun exportTo(tree: Uri): Int = withContext(Dispatchers.IO) {
            vault.shelf().clips.count { clip ->
                val audio = vault.read(clip.id) ?: return@count false
                VoiceExport.write(appContext, tree, clip, audio)
            }
        }
    }

    // ---- 가족 기억: 회차 끝 → 정보 → 다음 회 이어 묻기 ----

    private val memoryStore by lazy { FamilyMemoryStore(appContext) }

    /**
     * 정보 뽑기: 외부 LLM(설정 있을 때만) → 규칙. 출시 빌드는 AI 키가 비어 규칙만 돈다.
     * 시연 모드(DEMO_MEMORY)는 12회 기억으로 시작하고 저장하지 않는다 — 진짜 가족 기억을 덮지 않게.
     * lazy: remoteConfig 등 아래 선언이 먼저 초기화돼야 한다.
     */
    private val familyMemory: FamilyMemoryBook by lazy {
        FamilyMemoryBook(
            extractor = ChainFactExtractor(
                listOf(
                    RemoteFactExtractor({ remoteConfig }, PrivacyGate(egressLog)) { studio.roster() },
                    RuleFactExtractor(),
                ),
            ),
            planner = CallbackPlanner(BuiltinShow.callbackTemplates, accept = ::safeText),
            digestWriter = RemoteDigestWriter({ remoteConfig }, PrivacyGate(egressLog), ::safeText),
            // 시연 모드만 시연 가족 카드로 시작한다. 실제 빌드는 빈 명부 → 정차 화면에서 사람을 추가
            load = {
                if (BuildConfig.DEMO_MEMORY) {
                    BuiltinShow.demoMemory.copy(people = BuiltinShow.demoRoster)
                } else {
                    withContext(Dispatchers.IO) { memoryStore.load() }
                }
            },
            onChange = { book ->
                if (!BuildConfig.DEMO_MEMORY) withContext(Dispatchers.IO) { memoryStore.save(book) }
            },
        )
    }

    private fun safeText(text: String) = safety.accept(text, maxChars = 200, kidSafe = true) != null

    /** 앱 시작 때 백그라운드에서 저장된 기억·AI 설정을 읽어 둔다(정차 화면 카드가 바로 보이게). */
    suspend fun restoreMemory() {
        savedAi.value = withContext(Dispatchers.IO) { aiStore.load() }
        familyMemory.restore()
        voiceLibrary.refresh()
    }

    /** 보호자 "가족 기억 지우기". */
    suspend fun forgetFamily() = familyMemory.forget()

    /** 정차 화면의 사람 카드(기억이 없어도 모두) + QR 글(기억이 있는 사람만). */
    val familyProfile: Flow<FamilyProfileUi> get() = familyMemory.book.map { book ->
        val withFacts = ProfileCards.of(book, book.people, BuiltinShow.cornerTitles)
        val linesOf = withFacts.associate { it.nickname to it.lines }
        val latest = book.episodes.lastOrNull()
        val nameOf = book.people.associate { it.id to it.nickname }
        val recaps = book.episodes.map { e ->
            EpisodeRecapUi(e.number, e.oneLine, e.highlights.map { nameOf[it.guestId].orEmpty() to it.text }, e.insights)
        }
        FamilyProfileUi(
            people = book.people.map { PersonCard(it.id.value, it.nickname, it.seat, it.isChild, linesOf[it.nickname].orEmpty()) },
            qrText = withFacts.takeIf { it.isNotEmpty() }?.let { ProfileCards.qrText(it, book.lastEpisode, summary = latest) },
            lastEpisode = book.lastEpisode,
            recap = recaps.lastOrNull(),
            recaps = recaps,
        )
    }

    /** 사람 카드 추가·수정. id 가 비면 새 사람. */
    suspend fun savePerson(card: PersonCard) {
        val id = card.id.ifBlank { "g-${System.currentTimeMillis().toString(36)}" }
        val old = familyMemory.people().firstOrNull { it.id.value == id }
        familyMemory.savePerson(
            Guest(GuestId(id), card.nickname, card.seat, appearances = old?.appearances ?: 0, isChild = card.isChild),
        )
    }

    suspend fun removePerson(id: String) = familyMemory.removePerson(GuestId(id))

    // ---- 받아치기: 블랙박스 조립 ----

    private val template = TemplateQuip(BuiltinShow.templates)
    private val safety = SafetyFilter(BuiltinShow.bannedWords, BuiltinShow.kidBannedWords, BuiltinShow.hostAvoidWords)

    private fun quipEngine(): QuipEngine = GuardedQuip(
        delegate = ChainQuip(
            primary = primaryEngines(),
            fallback = template,
            budget = BuildConfig.QUIP_BUDGET_MS.milliseconds,
        ),
        fallback = template,
        masker = Masker(),
        filter = safety,
        // 요청마다 명부를 읽어 마스킹 대응표를 만든다(대응표는 저장하지 않는다)
        nicknames = { studio.roster().map { it.nickname } },
    )

    /** BuildConfig.QUIP_CHAIN 순서대로, 아직 없는 카트리지는 건너뛴다. */
    private fun primaryEngines(): List<QuipEngine> =
        BuildConfig.QUIP_CHAIN.split(',')
            .map(String::trim)
            .filter { it != TemplateQuip.ID }
            .mapNotNull { id ->
                val make = availableEngines[id] ?: return@mapNotNull null.also { Log.i(TAG, "받아치기 카트리지 '$id' 없음 → 건너뜀") }
                // SDK 가 없는 기기에서 생성자가 던져도 방송은 템플릿으로 계속 간다
                runCatching(make).onFailure { Log.w(TAG, "받아치기 카트리지 '$id' 생성 실패 → 건너뜀", it) }.getOrNull()
            }

    /** chagok/.env 에서 빌드 때 들어온 값. 비어 있으면 null. */
    private val pleosCredentials: PleosCredentials? =
        PleosCredentials(BuildConfig.PLEOS_CLIENT_ID, BuildConfig.PLEOS_CLIENT_SECRET)
            .takeIf { it.clientId.isNotBlank() && it.clientSecret.isNotBlank() }

    /** debug 빌드의 .env AI_* 값. release 는 비어 있어 null. */
    private val buildAi: RemoteConfig? = RemoteConfig.of(
        provider = BuildConfig.AI_PROVIDER,
        apiKey = BuildConfig.AI_API_KEY,
        model = BuildConfig.AI_MODEL,
        baseUrl = BuildConfig.AI_BASE_URL,
        timeoutMs = BuildConfig.AI_TIMEOUT_MS,
    )

    private val aiStore by lazy { AiSettingsStore(appContext) }
    private val savedAi = MutableStateFlow<AiSettings?>(null)

    /** .env 값이 먼저. release 는 .env 가 비어 있어 정차 화면 설정을 쓴다. 둘 다 없으면 null → 템플릿. */
    private val remoteConfig: RemoteConfig? get() = buildAi ?: savedAi.value?.toRemote()

    private fun AiSettings.toRemote(timeoutMs: Long = BuildConfig.AI_TIMEOUT_MS) =
        RemoteConfig.of(provider, apiKey, model, baseUrl, timeoutMs)

    /** 화면에는 키를 주지 않는다. 저장돼 있다는 사실만. */
    val aiStatus: Flow<AiStatusUi> get() = savedAi.map { saved ->
        AiStatusUi(
            on = saved?.toRemote() != null || buildAi != null,
            provider = saved?.provider.orEmpty(),
            model = saved?.model.orEmpty(),
            baseUrl = saved?.baseUrl.orEmpty(),
            hasSavedKey = saved != null,
        )
    }

    /** 키 칸을 비워 두면 저장된 키를 그대로 쓴다. */
    private fun AiSettings.withSavedKey() = if (apiKey.isBlank()) copy(apiKey = savedAi.value?.apiKey.orEmpty()) else this

    suspend fun saveAi(settings: AiSettings) {
        val complete = settings.withSavedKey()
        withContext(Dispatchers.IO) { aiStore.save(complete) }
        savedAi.value = complete
    }

    suspend fun clearAi() {
        withContext(Dispatchers.IO) { aiStore.clear() }
        savedAi.value = null
    }

    /** "연결 확인": 가족 정보가 없는 짧은 글 하나를 보내 응답을 받아 본다. */
    suspend fun testAi(settings: AiSettings): Boolean {
        val config = settings.withSavedKey().toRemote(AI_TEST_TIMEOUT_MS) ?: return false
        return runCatching {
            PrivacyGate(egressLog).egress(AI_TEST_ID, AI_TEST_PROMPT) { RemoteClient(config).complete(it, AI_TEST_TOKENS) }
        }.onFailure {
            if (it is CancellationException) throw it
            Log.w(TAG, "AI 연결 확인 실패: ${it.javaClass.simpleName}")
        }.isSuccess
    }

    /**
     * 체인(QUIP_CHAIN)에 이름이 있는 것만 실제로 만든다. remote 는 설정이 없으면 바로 양보해 템플릿으로 넘어간다.
     * TODO(quip-pleos-mode): Pleos LLM 이 열리면 프로브 결과로 ON_DEVICE / SERVER 확정
     */
    private val availableEngines: Map<String, () -> QuipEngine> = buildMap {
        put(RemoteQuip.ID) { RemoteQuip({ remoteConfig }, PrivacyGate(egressLog)) }
        put(PleosLlmQuip.ID) { PleosLlmQuip(appContext, server = false, credentials = pleosCredentials) }
    }

    /** 디버그: 이 기기에서 Pleos LLM 이 답하는지 logcat(ChagokLlmProbe)에 남긴다. setprop debug.chagok.llm_probe 1 일 때만. */
    suspend fun probePleosLlm() {
        if (PleosLlmProbe.enabled()) PleosLlmProbe.run(appContext, pleosCredentials)
    }

    /** 디버그: 내비 원값 실측. setprop debug.chagok.navi_probe 1 일 때만 경로를 요청한다. */
    fun probeNavi() {
        if (PleosNaviProbe.enabled()) PleosNaviProbe.run(appContext)
    }

    /** 디버그 빌드: 외부 LLM 한 번 호출해 결과를 logcat(ChagokRemote)에 남긴다. 키는 찍지 않는다. */
    suspend fun probeRemote() {
        val config = remoteConfig ?: return Unit.also {
            // 키는 길이만 찍는다
            Log.i(
                "ChagokRemote",
                "probe skip: AI_* 설정 없음 (provider='${BuildConfig.AI_PROVIDER}', " +
                    "model='${BuildConfig.AI_MODEL}', keyLen=${BuildConfig.AI_API_KEY.length})",
            )
        }
        val result = RemoteQuip({ config }, PrivacyGate(egressLog)).quip(
            QuipRequest("weather", "basic", "게스트1", "비 한 7점?", SignalCategory.RAIN, kidSafe = true),
        )
        Log.i("ChagokRemote", "probe $config -> $result")
    }

    private companion object {
        const val TAG = "ChagokDI"
        /** 32kbps AAC. 화면 표시용 어림값이다. */
        const val AAC_BYTES_PER_SECOND = 4_000L
        const val AI_TEST_ID = "remote-test"
        const val AI_TEST_PROMPT = "연결 확인입니다. 네 라고만 답하세요."
        const val AI_TEST_TOKENS = 8
        const val AI_TEST_TIMEOUT_MS = 10_000L
    }
}
