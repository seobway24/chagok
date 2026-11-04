package com.team.chagok.content

import com.team.chagok.core.domain.memory.CallbackPlanner
import com.team.chagok.core.domain.memory.ChainFactExtractor
import com.team.chagok.core.domain.memory.FactCheck
import com.team.chagok.core.domain.memory.FactExtractor
import com.team.chagok.core.domain.memory.FactPrompt
import com.team.chagok.core.domain.memory.FamilyMemoryBook
import com.team.chagok.core.domain.memory.ProfileCards
import com.team.chagok.core.domain.memory.RuleFactExtractor
import com.team.chagok.core.domain.port.AudioZone
import com.team.chagok.core.domain.port.Heard
import com.team.chagok.core.domain.port.HostVoice
import com.team.chagok.core.domain.port.MicInput
import com.team.chagok.core.domain.port.SignalSource
import com.team.chagok.core.domain.port.StudioRepository
import com.team.chagok.core.domain.show.LiveCorner
import com.team.chagok.core.domain.show.ShowDirector
import com.team.chagok.core.domain.show.ShowEngine
import com.team.chagok.core.domain.show.ShowState
import com.team.chagok.core.domain.show.ShowStateMachine
import com.team.chagok.core.model.Answer
import com.team.chagok.core.model.EpisodeRecord
import com.team.chagok.core.model.FamilyFact
import com.team.chagok.core.model.Guest
import com.team.chagok.core.model.GuestId
import com.team.chagok.core.model.Seat
import com.team.chagok.core.model.SignalEvent
import com.team.chagok.core.privacy.GuardedQuip
import com.team.chagok.core.privacy.Masker
import com.team.chagok.core.privacy.SafetyFilter
import com.team.chagok.core.quip.ChainQuip
import com.team.chagok.core.quip.QuipEngine
import com.team.chagok.core.quip.QuipRequest
import com.team.chagok.core.quip.QuipResult
import com.team.chagok.core.quip.QuipText
import com.team.chagok.core.quip.TemplateQuip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration as JDuration
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * 4인 가족(아빠·엄마·누나 12세·막내 7세)이 두 회차를 연달아 탄다 — 실제 엔진 + 가족 기억.
 *
 * - 제12회: 차량 신호·가족 대답은 대본, 진행자 받아치기는 실제 체인(GuardedQuip → ChainQuip → 템플릿)
 * - 회차가 끝나면 대답에서 가족 정보를 뽑아(중계 LLM → 규칙) 사실 확인 뒤 프로필에 쌓는다
 * - 제13회: 조용할 때 "지난 회 이어서" 코너가 먼저 열리고, 그 질문은 12회에 실제로 한 말로 만들어진다
 * - 로컬 중계(tools/claude-relay, 127.0.0.1:8787)가 없으면 템플릿·규칙만으로 돈다 — 규칙 검증은 그대로 통과해야 한다
 * - 대본과 결과는 build/sim/episode.md 에 남긴다
 */
class FamilyEpisodeSimulationTest {

    private val roster = BuiltinShow.demoRoster
    private val dad = roster.first { it.seat == Seat.DRIVER }
    private val transcript = mutableListOf<String>()
    private fun log(line: String) = synchronized(transcript) { transcript += line }.also { println(line) }
    private fun nickname(id: GuestId) = roster.first { it.id == id }.nickname

    @Test
    fun `family of four grows a memory over two episodes`() = runBlocking {
        val relay = Relay.takeIf { it.alive() }
        log("> 받아치기·정보 뽑기: ${if (relay != null) "중계 LLM(claude haiku) → 템플릿/규칙" else "템플릿/규칙만 (중계 없음)"}")

        val signals = MutableSharedFlow<SignalEvent>(extraBufferCapacity = 16)
        val studio = FakeStudio(roster)
        lateinit var engine: ShowEngine
        val voice = TranscriptVoice()
        val mic = FamilyMic { (engine.state.value as? ShowState.OnAir)?.live }
        val template = TemplateQuip(BuiltinShow.templates)
        val filter = SafetyFilter(BuiltinShow.bannedWords, BuiltinShow.kidBannedWords)
        val quip = GuardedQuip(
            delegate = ChainQuip(listOfNotNull(relay?.let(::RelayQuip)), template, budget = 15.seconds),
            fallback = template,
            masker = Masker(),
            filter = filter,
            nicknames = { roster.map { it.nickname } },
        )
        val aiFacts = relay?.let { RelayFactExtractor(it) }
        val memory = FamilyMemoryBook(
            extractor = ChainFactExtractor(listOfNotNull(aiFacts, RuleFactExtractor())),
            planner = CallbackPlanner(
                BuiltinShow.callbackTemplates,
                accept = { filter.accept(it, maxChars = 200, kidSafe = true) != null },
            ),
        )
        engine = ShowEngine(
            machine = ShowStateMachine(BuiltinShow.basicPack, resumeDelay = 300.milliseconds, quietAfter = 1.seconds),
            director = ShowDirector(voice, mic, quip, BuiltinShow.skins.first()),
            signals = object : SignalSource { override val events = signals },
            studio = studio,
            memory = memory,
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        engine.launchIn(scope)

        suspend fun send(label: String, event: SignalEvent) {
            log("\n[차량] $label")
            signals.emit(event)
        }
        suspend fun until(what: String, limit: Duration = 60.seconds, check: (ShowState) -> Boolean) {
            withTimeoutOrNull(limit) { while (!check(engine.state.value)) delay(20) }
                ?: fail("$what 대기 시간 초과 — 상태: ${engine.state.value}")
        }
        suspend fun waitFor(what: String, limit: Duration = 90.seconds, check: () -> Boolean) {
            withTimeoutOrNull(limit) { while (!check()) delay(50) } ?: fail("$what 대기 시간 초과")
        }
        suspend fun startParkedThenDrive() {
            send("시동 (정차)", SignalEvent.DrivingChanged(driving = false))
            until("parked") { it is ShowState.Idle && !it.driving }
            engine.startEpisode()
            until("opening") { it is ShowState.OnAir }
            send("주행 시작", SignalEvent.DrivingChanged(driving = true))
        }
        val idleOnAir = { s: ShowState -> s is ShowState.OnAir && s.live == null }
        val liveCorner = { s: ShowState -> (s as? ShowState.OnAir)?.live?.corner?.id }

        // ================= 제12회 =================
        log("\n========== 제12회 · 할머니 댁 가는 길 ==========")
        startParkedThenDrive()

        // 비 → 기상 특보. 두 번째 비는 같은 코너를 다시 열지 않아야 한다
        send("와이퍼 작동 (비 시작)", SignalEvent.RainStarted)
        until("weather open") { liveCorner(it) == "weather" }
        until("weather done", check = idleOnAir)
        send("와이퍼 다시 작동", SignalEvent.RainStarted)
        delay(200)
        assertTrue("비가 또 와도 기상 특보는 한 번만", liveCorner(engine.state.value) != "weather")

        // 터널 → 터널 토크, 중간에 깜빡이 → 즉시 끊김 → 끄면 재개
        until("idle", check = idleOnAir)
        send("전조등 자동 점등 (터널 진입)", SignalEvent.LightsOn)
        until("tunnel open") { liveCorner(it) == "tunnel" }
        delay(150)
        send("깜빡이 ON (차선 변경)", SignalEvent.TurnSignalChanged(on = true))
        until("paused") { it is ShowState.SafetyPaused }
        log("  (방송 멈춤 — 운전 집중)")
        delay(500)
        send("깜빡이 OFF", SignalEvent.TurnSignalChanged(on = false))
        // 끊긴 코너는 신호가 다시 오지 않아도 재개할 때 다시 열린다
        until("tunnel reopen") { liveCorner(it) == "tunnel" }
        log("  (방송 재개)")
        until("tunnel done", check = idleOnAir)

        // 조용함 → 채움 코너. 주행 중이라 운전자(캡틴) 코너는 건너뛴다
        log("\n[차량] (한동안 신호 없음)")
        until("quiet corner") { liveCorner(it) != null }
        assertTrue("주행 중엔 운전자 코너 금지", liveCorner(engine.state.value) != "captain")
        until("quiet done", check = idleOnAir)

        // 신호 대기(정차) → 이번엔 아빠에게도 마이크
        send("신호 대기 (정차)", SignalEvent.DrivingChanged(driving = false))
        until("captain") { liveCorner(it) == "captain" }
        assertEquals(dad, (engine.state.value as ShowState.OnAir).live!!.guest)
        until("captain done", check = idleOnAir)
        send("출발", SignalEvent.DrivingChanged(driving = true))

        // 주행 거리 이정표 → 주행 특집, 그다음 조용함 코너 몇 개
        send("누적 100km 돌파", SignalEvent.MilestoneKm(100))
        until("milestone") { liveCorner(it) == "milestone" }
        until("milestone done", check = idleOnAir)
        repeat(3) {
            until("quiet open") { liveCorner(it) != null }
            until("quiet done", check = idleOnAir)
        }

        send("도착 · 기어 P", SignalEvent.GearParked)
        until("saved 12", limit = 30.seconds) { it is ShowState.Idle && studio.saved.size == 1 }
        val first = studio.saved[0]
        log("\n> 저장: 제${first.number}회 · 코너 ${first.cornerIds} · 출연 ${first.guestIds.map { it.value }} · 대답 ${first.answers.size}개")
        assertEquals("코너는 중복 없이", first.cornerIds.distinct(), first.cornerIds)
        assertTrue("4명 모두 출연", first.guestIds.size == roster.size)

        // ---- 회차 끝: 가족 기억 ----
        waitFor("12회 기억") { memory.book.value.facts.isNotEmpty() }
        val afterFirst = memory.book.value
        log("\n> 12회 뒤 가족 기억 ${afterFirst.facts.size}개")
        afterFirst.facts.forEach { log("   · ${nickname(it.guestId)} [${it.cornerId}] ${it.keyword}") }
        assertTrue("기억은 전부 실제로 한 말", afterFirst.facts.all { FactCheck.grounded(it, first.answers) })

        val planned = memory.callbacksFor(roster, 13)
        log("\n> 13회 이어 묻기 ${planned.size}개")
        planned.forEach { log("   · ${nickname(it.forGuest!!)}: ${it.callOut.replace("{guest}", nickname(it.forGuest!!))}") }
        assertTrue("이어 물을 거리가 있어야 한다", planned.isNotEmpty())
        planned.forEach { corner ->
            val fact = afterFirst.facts.first { CallbackPlanner.PREFIX + it.key == corner.id }
            assertTrue("질문 속 말은 그 사람의 12회 대답에서", FactCheck.grounded(fact, first.answers))
        }

        // ================= 제13회 =================
        val ep13From = synchronized(transcript) { transcript.size }
        log("\n========== 제13회 · 할머니 댁에서 돌아오는 길 ==========")
        startParkedThenDrive()

        send("와이퍼 작동 (비 시작)", SignalEvent.RainStarted)
        until("weather open 13") { liveCorner(it) == "weather" }
        until("weather done 13", check = idleOnAir)

        // 조용함 → 지난 회 이어 묻기가 일반 채움 코너보다 먼저
        log("\n[차량] (한동안 신호 없음)")
        val passengerCallbacks = planned.filterNot { it.needsDriver }
        repeat(passengerCallbacks.size) {
            until("callback open") { liveCorner(it) != null }
            assertTrue("이어 묻기 먼저", liveCorner(engine.state.value)!!.startsWith(CallbackPlanner.PREFIX))
            until("callback done", check = idleOnAir)
        }
        until("filler open") { liveCorner(it) != null }
        until("filler done", check = idleOnAir)

        send("도착 · 기어 P", SignalEvent.GearParked)
        until("saved 13", limit = 30.seconds) { it is ShowState.Idle && studio.saved.size == 2 }
        val second = studio.saved[1]
        log("\n> 저장: 제${second.number}회 · 코너 ${second.cornerIds}")
        assertTrue("이어 묻기가 실제로 나갔다", passengerCallbacks.all { it.id in second.cornerIds })

        waitFor("13회 기억") { memory.book.value.calledBack.isNotEmpty() }
        val afterSecond = memory.book.value
        val asked = passengerCallbacks.mapNotNull { CallbackPlanner.factKeyOf(it.id) }
        assertTrue("물어본 건 표시", afterSecond.calledBack.containsAll(asked))
        assertTrue("같은 얘기 두 번 안 꺼냄", memory.callbacksFor(roster, 14).none { CallbackPlanner.factKeyOf(it.id) in asked })
        scope.cancel()

        val cards = ProfileCards.of(afterSecond, roster, BuiltinShow.cornerTitles)
        val qr = ProfileCards.qrText(cards, 13)
        assertTrue("QR 크기", qr.toByteArray(Charsets.UTF_8).size <= ProfileCards.QR_MAX_BYTES)

        val recap = relay?.let { recapOf(it, synchronized(transcript) { transcript.drop(ep13From) }) }
        writeReport(
            buildString {
                appendLine("## 가족 기억 → 13회 이어 묻기")
                appendLine()
                planned.forEach { appendLine("- ${nickname(it.forGuest!!)}: ${it.callOut.replace("{guest}", nickname(it.forGuest!!))}") }
                aiFacts?.let { ai ->
                    appendLine()
                    appendLine("## AI 정보 뽑기 검사 (대답에 없는 말은 버림)")
                    appendLine()
                    appendLine("- 통과 ${ai.accepted.size}개 · 버림 ${ai.rejected.size}개")
                    ai.rejected.forEach { appendLine("  - 버림: $it") }
                }
                appendLine()
                appendLine("## 가족 프로필 카드 (13회까지)")
                appendLine()
                cards.forEach { card ->
                    appendLine("**${card.nickname}**")
                    card.lines.forEach { appendLine("- $it") }
                    appendLine()
                }
                appendLine("## QR 에 담기는 글 (${qr.toByteArray(Charsets.UTF_8).size}바이트)")
                appendLine()
                appendLine("```")
                appendLine(qr)
                appendLine("```")
            },
            recap,
        )
    }

    // ---- 가짜 부품 ----

    private inner class TranscriptVoice : HostVoice {
        override suspend fun speak(text: String, speaker: String, zone: AudioZone): Boolean {
            log("🎙 진행자: $text")
            delay(120) // 읽는 시간 흉내 (깜빡이로 끊을 틈)
            return true
        }
        override fun stop() = log("  ⏹ (진행자 음성 끊김)")
    }

    /** 지금 마이크를 받은 사람이 대본대로 답한다. 막내는 처음 한 번 수줍어서 말을 안 한다. */
    private inner class FamilyMic(private val live: () -> LiveCorner?) : MicInput {
        private val shyOnce = mutableSetOf<String>()
        override suspend fun listen(window: Duration): Heard {
            delay(100)
            val now = live() ?: return Heard.Silence
            val who = now.guest
            if (who.nickname == "막내" && shyOnce.add(now.corner.id) && now.corner.id == "best-moment") {
                log("   …(${who.nickname} 수줍어서 침묵)")
                return Heard.Silence
            }
            val answer = if (now.corner.id.startsWith(CallbackPlanner.PREFIX)) {
                CALLBACK_ANSWERS[who.nickname]
            } else {
                ANSWERS[who.nickname to now.corner.id]
            } ?: return Heard.Silence
            log("🗣 ${who.nickname}: $answer")
            return Heard.Text(answer)
        }
    }

    private class FakeStudio(private val roster: List<Guest>) : StudioRepository {
        val saved: MutableList<EpisodeRecord> = java.util.Collections.synchronizedList(mutableListOf())
        override suspend fun roster() = roster
        override suspend fun lastEpisodeNumber() = 11 + saved.size
        override suspend fun saveEpisode(record: EpisodeRecord) { saved += record }
    }

    // ---- 로컬 중계 LLM (테스트 전용, 앱 PromptBook 과 같은 말투 지시) ----

    private object Relay {
        private val http = HttpClient.newBuilder().connectTimeout(JDuration.ofMillis(800)).build()

        fun alive(): Boolean = try {
            http.send(HttpRequest.newBuilder(URI("http://127.0.0.1:8787/")).GET().build(), HttpResponse.BodyHandlers.discarding())
            true
        } catch (e: Exception) { false }

        suspend fun ask(prompt: String): String = withContext(Dispatchers.IO) {
            val body = """{"model":"haiku","messages":[{"role":"user","content":${json(prompt)}}]}"""
            val res = http.send(
                HttpRequest.newBuilder(URI("http://127.0.0.1:8787/v1/chat/completions"))
                    .timeout(JDuration.ofSeconds(60))
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            check(res.statusCode() == 200) { "relay ${res.statusCode()}" }
            unjson(Regex(""""content":("(?:[^"\\]|\\.)*")""").find(res.body())!!.groupValues[1])
        }

        private fun json(s: String) = buildString {
            append('"')
            s.forEach { c -> when (c) { '"' -> append("\\\""); '\\' -> append("\\\\"); '\n' -> append("\\n"); else -> append(c) } }
            append('"')
        }

        private fun unjson(q: String): String {
            val s = q.substring(1, q.length - 1)
            val out = StringBuilder()
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '\\' && i + 1 < s.length) {
                    when (val n = s[i + 1]) {
                        'n' -> out.append('\n'); 't' -> out.append('\t')
                        'u' -> { out.append(s.substring(i + 2, i + 6).toInt(16).toChar()); i += 4 }
                        else -> out.append(n)
                    }
                    i += 2
                } else { out.append(c); i++ }
            }
            return out.toString()
        }
    }

    private class RelayQuip(private val relay: Relay) : QuipEngine {
        override val id = "relay"
        override suspend fun quip(request: QuipRequest): QuipResult {
            val prompt = buildString {
                appendLine("너는 가족이 함께 타는 차 안 팟캐스트의 진행자다. 말투: 다정하고 밝은 진행자.")
                appendLine("지금 코너: ${request.cornerId.substringBefore(':')}.")
                if (request.question.isNotBlank()) appendLine("진행자가 던진 질문: \"${request.question}\"")
                if (request.utterance.isBlank()) {
                    appendLine("${request.speaker}이(가) 대답하지 않았다. 부담 주지 말고 다음을 기약하는 말로 넘겨라.")
                } else {
                    appendLine("${request.speaker}의 대답: \"${request.utterance}\"")
                    appendLine("대답을 살려 재치 있게 받아쳐라.")
                }
                if (request.kidSafe) appendLine("아이가 함께 듣는다. 순한 말만 쓴다.")
                append("한국어 한 문장, ${request.maxChars}자 이내. 따옴표·이모지·설명 없이 문장만.")
            }
            val started = System.currentTimeMillis()
            val text = QuipText.fit(relay.ask(prompt), request.maxChars)
            return if (text.isEmpty()) QuipResult.Unavailable(QuipResult.Reason.ERROR)
            else QuipResult.Line(text, id, System.currentTimeMillis() - started)
        }
    }

    /**
     * AI 정보 뽑기(테스트 전용 중계). 호칭은 게스트N 으로 가려서 보낸다.
     * 결과는 [FactCheck] 로 다시 걸러진다 — 여기서는 몇 개가 걸렸는지만 기록한다.
     */
    private inner class RelayFactExtractor(private val relay: Relay) : FactExtractor {
        val accepted = mutableListOf<String>()
        val rejected = mutableListOf<String>()

        override suspend fun extract(answers: List<Answer>, episode: Int): List<FamilyFact> {
            if (answers.isEmpty()) return emptyList()
            val session = Masker().open(roster.map { it.nickname })
            // 앱 RemoteFactExtractor 와 같은 지시·같은 읽기
            val lines = answers.mapIndexed { i, a ->
                FactPrompt.line(i, session.mask(nickname(a.guestId)), a.cornerId, session.mask(a.text))
            }
            val facts = FactPrompt.parse(relay.ask(FactPrompt.build(lines)), answers, episode, session::unmask)
            facts.forEach {
                val label = "${nickname(it.guestId)} [${it.cornerId.substringBefore(':')}] ${it.keyword}"
                if (FactCheck.grounded(it, answers)) accepted += label else rejected += label
            }
            return facts
        }
    }

    /** 회차 대본 → 한 줄 요약 · 새로 알게 된 것 · 다음 회 질문. 호칭은 게스트N 으로 가려서 보낸다. */
    private suspend fun recapOf(relay: Relay, lines: List<String>): String {
        val session = Masker().open(roster.map { it.nickname })
        val people = roster.joinToString { "${session.mask(it.nickname)}(${it.seat.name}${if (it.isChild) ", 아이" else ""})" }
        val body = lines.filter { it.startsWith("🎙") || it.startsWith("🗣") }
            .joinToString("\n") { session.mask(it.removePrefix("🎙 ").removePrefix("🗣 ")) }
        val prompt = """
            |다음은 가족이 차 안에서 진행한 팟캐스트 한 회의 대본이다. 출연: $people. 게스트1~4 는 가족 호칭을 가린 것이다.
            |아래 형식 그대로, 마크다운으로만 답하라. 대본에 없는 사실은 지어내지 마라. "진행자" 대사는 사실 근거로 쓰지 마라.
            |## 한 줄 요약
            |(이번 회만의 구체적인 내용으로 40자 이내. "웃음꽃" 같은 뻔한 말 금지)
            |## 새로 알게 된 것
            |(대답한 사람마다. "- 게스트N: ..." 한 줄씩)
            |## 오늘의 명장면
            |(가족이 한 대사를 글자 그대로 인용하고 누가 했는지)
            |
            |$body
        """.trimMargin()
        return session.unmask(relay.ask(prompt))
    }

    private fun writeReport(memorySection: String, recap: String?) {
        val out = File("build/sim").apply { mkdirs() }.resolve("episode.md")
        out.writeText(
            buildString {
                appendLine("# 차곡 팟캐스트 시뮬레이션 — 4인 가족 두 회차")
                appendLine()
                appendLine(memorySection)
                if (recap != null) {
                    appendLine("## 제13회 요약 (LLM)")
                    appendLine()
                    appendLine(recap)
                    appendLine()
                }
                appendLine("## 대본")
                appendLine()
                appendLine("```")
                synchronized(transcript) { transcript.forEach(::appendLine) }
                appendLine("```")
            },
        )
        println("\n> 리포트: ${out.absolutePath}")
        println("\n$memorySection")
        recap?.let { println("\n$it") }
    }

    private companion object {
        /** (호칭, 코너) → 대답. 7세·12세 말투를 살린다. */
        val ANSWERS = mapOf(
            ("엄마" to "weather") to "음 6점? 빨래 널고 왔는데 비 와서 마이너스 4점",
            ("누나" to "weather") to "8점! 빗소리 들으면서 노래 듣는 거 좋아",
            ("막내" to "weather") to "100점! 웅덩이 첨벙 하고 싶어",
            ("엄마" to "tunnel") to "아빠가 어제 리모컨 냉장고에 넣어 둔 거",
            ("누나" to "tunnel") to "어제 아빠가 내 폰으로 셀카 50장 찍은 거",
            ("막내" to "tunnel") to "누나가 방귀 뀌고 내가 뀌었다고 한 거",
            ("엄마" to "best-moment") to "아침에 다 같이 김밥 싼 거",
            ("누나" to "best-moment") to "엄마가 머리 땋아 준 거",
            ("막내" to "best-moment") to "할머니 집 가는 거! 강아지 보러",
            ("아빠" to "captain") to "평화! 아직은 아무도 안 싸워서",
            ("엄마" to "milestone") to "작년 여름 강릉 바다",
            ("누나" to "milestone") to "제주도! 말 타 본 거",
            ("막내" to "milestone") to "공룡 박물관! 티라노 있었어",
            ("엄마" to "balance") to "짬뽕, 근데 국물만",
            ("누나" to "balance") to "짜장면이지 당연히. 짬뽕은 매워",
            ("막내" to "balance") to "짜장면! 입에 다 묻혀도 돼?",
            ("엄마" to "dream") to "혼자 일주일 여행. 농담이야",
            ("누나" to "dream") to "웹툰 작가. 우리 가족 얘기로 그릴 거야",
            ("막내" to "dream") to "공룡 박사! 아니면 소방관",
            ("엄마" to "family-quiz") to "막내. 어제도 차에서 바로 잤어",
            ("누나" to "family-quiz") to "아빠! 주말에 소파에서 맨날 자",
            ("막내" to "family-quiz") to "누나! 아침에 깨워도 안 일어나",
        )

        /** 13회 "지난 회 이어서" 에 대한 대답 — 지난번 얘기를 받아 한 걸음 더 간다. */
        val CALLBACK_ANSWERS = mapOf(
            "엄마" to "제주도로 갈 거야. 물론 다 같이",
            "누나" to "첫 화 제목은 차곡 가족이야",
            "막내" to "티라노 박사! 티라노가 제일 세",
            "아빠" to "오늘도 평화! 간식 덕분에",
        )
    }
}
