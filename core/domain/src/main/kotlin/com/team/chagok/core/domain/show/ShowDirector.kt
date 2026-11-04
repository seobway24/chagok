package com.team.chagok.core.domain.show

import com.team.chagok.core.domain.memory.DigestRules
import com.team.chagok.core.domain.port.Heard
import com.team.chagok.core.domain.port.HostVoice
import com.team.chagok.core.domain.port.MicInput
import com.team.chagok.core.model.Answer
import com.team.chagok.core.model.Guest
import com.team.chagok.core.model.HostSkin
import com.team.chagok.core.model.Seat
import com.team.chagok.core.quip.QuipEngine
import com.team.chagok.core.quip.QuipRequest
import com.team.chagok.core.quip.QuipResult
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * 진행자 대본. 형식 팩처럼 데이터로 바꿔 끼운다.
 * `{episode}` `{guest}` `{guests}` `{count}` 가 채워진다.
 */
data class HostScript(
    val opening: String = "차곡 팟캐스트 제{episode}회! 오늘은 {count}명이 함께하고 있네요. {guests}!",
    /** 내비 안내 중일 때 오프닝 뒤에 붙는 한 줄. `{destination}` = 목적지 이름. */
    val route: String = "오늘 방송은 {destination}까지 함께 갑니다!",
    val recall: String = "{guest}, 마이크 켜져 있어요~",
    val best: String = "오늘의 한마디, {guest}! {line}",
    val farewell: String = "{guest}, 오늘 운전 수고했어요. 내리기 전에 소감 한마디?",
    val ending: String = "오늘 방송은 여기까지! 제{episode}회 출연진 {guests}, 수고하셨습니다.",
    /** 내비 목적지에 왔을 때 엔딩 맨 앞 한 줄. 오프닝의 [route] 와 짝이다. */
    val arrived: String = "{destination} 도착! 여기까지 함께 와 줘서 고마워요.",
    /** 벨트 = 출석. 꾸짖지 않고 출석을 부르듯 말한다. 매 달라는 말은 빼지 않는다. */
    val belt: String = "{names}, 벨트를 매면 출석 완료! 안전벨트 매 주세요.",
    val whoAreYou: String = "{seat}, 누구세요? 이름을 말해 주세요.",
)

/**
 * 한 장면을 실제로 소리 내어 진행한다. 상태는 갖지 않는다.
 *
 * 취소(SafetyPause)되면 suspend 지점에서 바로 멈춘다 — 끊는 쪽은 [ShowEngine].
 */
class ShowDirector(
    private val voice: HostVoice,
    private val mic: MicInput,
    private val quip: QuipEngine,
    private val skin: HostSkin,
    private val script: HostScript = HostScript(),
    private val listenWindow: Duration = DEFAULT_LISTEN_WINDOW,
) {

    suspend fun opening(episode: Int, roster: List<Guest>, destination: String? = null) {
        say(script.opening.fill(episode = episode, guests = roster))
        destination?.takeIf { it.isNotBlank() }?.let { say(script.route.replace("{destination}", it)) }
    }

    /** @return 들은 대답. 끝내 못 들었으면 null. */
    suspend fun corner(live: LiveCorner, roster: List<Guest>, episode: Int = 0): Answer? {
        val guest = live.guest
        val question = live.corner.callOutFor(episode).fill(guest = guest)
        say(question)

        var heard = mic.listen(listenWindow)
        if (heard !is Heard.Text) {
            say(script.recall.fill(guest = guest))
            heard = mic.listen(listenWindow)
        }

        val request = QuipRequest(
            cornerId = live.corner.id,
            skinId = skin.id,
            speaker = guest.nickname,
            utterance = (heard as? Heard.Text)?.text.orEmpty(),
            signal = live.corner.trigger,
            kidSafe = roster.any { it.isChild },
            question = question,
        )
        (quip.quip(request) as? QuipResult.Line)?.let { say(it.text) }
        return request.utterance.takeIf { it.isNotBlank() }?.let { Answer(guest.id, live.corner.id, question, it) }
    }

    /**
     * 명장면 하나 → 운전자 하차 한마디 → 마무리.
     * 플랫폼은 P 에서만 주행 제한을 푼다. 운전자의 말은 여기서 받는다.
     * @return 운전자의 한마디. 못 들었으면 null.
     */
    suspend fun ending(onAir: ShowState.OnAir): Answer? {
        onAir.destination?.takeIf { onAir.arrived && it.isNotBlank() }?.let { say(script.arrived.replace("{destination}", it)) }
        DigestRules.highlights(onAir.answers).firstOrNull()?.let { best ->
            onAir.roster.firstOrNull { it.id == best.guestId }?.let { guest ->
                say(script.best.fill(guest = guest).replace("{line}", best.text))
            }
        }
        val farewell = onAir.roster.firstOrNull { it.seat == Seat.DRIVER }?.let { driver -> askFarewell(driver) }
        val appeared = onAir.roster.filter { it.id in onAir.appeared }
        say(script.ending.fill(episode = onAir.episode, guests = appeared.ifEmpty { onAir.roster }))
        return farewell
    }

    private suspend fun askFarewell(driver: Guest): Answer? {
        val question = script.farewell.fill(guest = driver)
        say(question)
        val heard = mic.listen(listenWindow) as? Heard.Text ?: return null
        return Answer(driver.id, FAREWELL_CORNER, question, heard.text)
    }

    fun interrupt() = voice.stop()

    suspend fun belt(names: List<String>) = say(script.belt.replace("{names}", names.joinToString()))

    /** 카드가 없는 자리에 이름을 묻는다. 끝내 못 들었으면 null. */
    suspend fun askName(seat: Seat): String? {
        say(script.whoAreYou.replace("{seat}", seat.label))
        return (mic.listen(listenWindow) as? Heard.Text)?.text
    }

    private suspend fun say(text: String) {
        voice.speak(text, skin.ttsSpeaker)
    }

    private fun String.fill(
        episode: Int? = null,
        guest: Guest? = null,
        guests: List<Guest> = emptyList(),
    ): String = this
        .replace("{episode}", episode?.toString().orEmpty())
        .replace("{guest}", guest?.nickname.orEmpty())
        .replace("{guests}", guests.joinToString { it.nickname })
        .replace("{count}", guests.size.toString())

    companion object {
        /** 호명 뒤 듣기 창 (architecture §4.2). */
        val DEFAULT_LISTEN_WINDOW: Duration = 8.seconds

        /** 운전자 하차 한마디의 코너 id. */
        const val FAREWELL_CORNER = "farewell"
    }
}
