package com.team.chagok.core.domain.memory

import com.team.chagok.core.model.Answer
import com.team.chagok.core.model.EpisodeDigest
import com.team.chagok.core.model.EpisodeHighlight
import com.team.chagok.core.model.EpisodeRecord
import com.team.chagok.core.model.EpisodeSummary
import com.team.chagok.core.model.Guest
import com.team.chagok.core.model.PortraitLine
import com.team.chagok.core.model.ProfileBook

/** 한 회차를 정리한다. 구현: AI 카트리지. 없거나 실패하면 [DigestRules] 결과가 남는다. */
fun interface DigestWriter {
    suspend fun write(record: EpisodeRecord, roster: List<Guest>, book: ProfileBook): EpisodeDigest?
}

/** AI 없이 하는 정리. 지어내는 일이 없고, 숫자는 앱이 센다. */
object DigestRules {

    private const val MAX_HIGHLIGHTS = 3
    private const val MIN_CHARS = 4
    private const val MAX_CHARS = 60

    fun write(record: EpisodeRecord, roster: List<Guest>): EpisodeDigest {
        val names = roster.filter { it.id in record.guestIds }.ifEmpty { roster }.joinToString { it.nickname }
        val corners = record.cornerIds.size
        return EpisodeDigest(
            EpisodeSummary(
                number = record.number,
                oneLine = "제${record.number}회, $names 함께 코너 ${corners}개를 했어요",
                highlights = highlights(record.answers),
                insights = insights(record, roster),
            ),
        )
    }

    /** 긴 대답부터 한 사람에 하나씩. 너무 짧거나 긴 말은 뺀다. 원문을 고치지 않는다. */
    fun highlights(answers: List<Answer>): List<EpisodeHighlight> = answers
        .filter { it.text.trim().length in MIN_CHARS..MAX_CHARS }
        .sortedByDescending { it.text.length }
        .distinctBy { it.guestId }
        .take(MAX_HIGHLIGHTS)
        .map { EpisodeHighlight(it.guestId, it.cornerId, it.text.trim()) }

    /** 앱이 센 값. 아이가 타면 사람끼리 비교하지 않는다. */
    fun insights(record: EpisodeRecord, roster: List<Guest>): List<String> {
        val total = "이번 회 대답은 모두 ${record.answers.size}개였어요"
        if (roster.any { it.isChild }) return listOf(total)
        val top = record.answers.groupingBy { it.guestId }.eachCount().entries.maxByOrNull { it.value }
        val name = roster.firstOrNull { it.id == top?.key }?.nickname ?: return listOf(total)
        return listOf(total, "$name 님이 ${top?.value}번으로 가장 많이 말했어요")
    }
}

/**
 * AI 회차 정리 지시문과 답 읽기·검사. 어느 LLM 이든 같은 형식을 쓴다.
 *
 * 호칭 가리기는 붙이는 쪽 몫이다. AI 는 명장면의 번호만 고르고 화면에는 원문이 나간다.
 * 사람 카드 줄은 근거 대답 번호가 있어야 하고, 내용이 그 대답 안에 있어야 저장한다.
 */
object DigestPrompt {

    val LABELS = setOf("좋아하는 것", "싫어하는 것", "꿈", "추억", "요즘", "계획", "습관")
    private const val MAX_ONE_LINE = 60
    private const val MAX_VALUE = 20
    private const val MAX_HIGHLIGHTS = 3
    private val COMPARISON = Regex("""가장|제일|보다|1등|꼴찌""")
    private val NOISE = Regex("""[\s.,!?~…'"]""")

    fun build(lines: List<String>, known: List<String>): String = """
        |차 안 팟캐스트 한 회의 대답 목록이다. 한 줄 = 번호|사람|코너|대답.
        |회차를 정리해서 아래 네 종류의 줄만 출력하라. 설명·머리말·마크다운 없이.
        |요약|이번 회를 친근하게 줄인 한 문장 (${MAX_ONE_LINE}자 이내, 존댓말)
        |명장면|번호 (재미있거나 그 사람다운 대답 번호, 최대 ${MAX_HIGHLIGHTS}줄)
        |인사이트|대답에서 보이는 공통점 한 문장 (없으면 이 줄을 쓰지 마라)
        |카드|번호|항목|내용
        |규칙:
        |- 카드의 내용은 그 번호의 대답 안에 있는 말을 글자 그대로 2~${MAX_VALUE}자로 쓴다. 바꿔 말하거나 지어내지 마라.
        |- 카드의 항목은 다음 중 하나: ${LABELS.joinToString()}.
        |- 카드는 말한 사람 자신에 대한 것만. 다른 사람의 습관·건강·일 얘기는 쓰지 마라. 진행자 말을 따라 한 대답, 농담, 게임 속 이름은 쓰지 마라.
        |- 대답에 없는 사실을 지어내지 마라. 사람의 성격을 판단하지 마라. 사람을 놀리지 마라.
        |- 이름은 목록에 나온 그대로 쓴다.
        |${if (known.isEmpty()) "" else "이미 아는 카드(같은 내용은 쓰지 마라):\n" + known.joinToString("\n")}
        |
        |${lines.joinToString("\n")}
    """.trimMargin()

    /** 요약이 없거나 쓸 수 없으면 null. 나머지는 줄마다 검사해 통과한 것만 남긴다. */
    fun parse(
        output: String,
        record: EpisodeRecord,
        roster: List<Guest>,
        unmask: (String) -> String,
        accept: (String) -> Boolean,
    ): EpisodeDigest? {
        val rows = output.lines().map { line -> line.split('|').map(String::trim) }.filter { it.size >= 2 }
        val kids = roster.any { it.isChild }
        fun sentence(text: String) = unmask(text).takeIf { it.length in 2..MAX_ONE_LINE && accept(it) }
        val oneLine = rows.firstOrNull { it[0] == "요약" }?.let { sentence(it[1]) } ?: return null
        val picked = rows.filter { it[0] == "명장면" }
            .mapNotNull { record.answers.getOrNull((it[1].toIntOrNull() ?: 0) - 1) }
            .distinct().take(MAX_HIGHLIGHTS)
            .map { EpisodeHighlight(it.guestId, it.cornerId, it.text.trim()) }
        val insight = rows.firstOrNull { it[0] == "인사이트" }?.let { sentence(it[1]) }
            ?.takeUnless { kids && COMPARISON.containsMatchIn(it) }
        return EpisodeDigest(
            EpisodeSummary(
                number = record.number,
                oneLine = oneLine,
                highlights = picked.ifEmpty { DigestRules.highlights(record.answers) },
                insights = DigestRules.insights(record, roster) + listOfNotNull(insight),
            ),
            portraits = rows.filter { it[0] == "카드" && it.size >= 4 }
                .mapNotNull { portrait(it, record, unmask, accept) }
                .distinctBy { it.guestId to it.label },
        )
    }

    private fun portrait(row: List<String>, record: EpisodeRecord, unmask: (String) -> String, accept: (String) -> Boolean): PortraitLine? {
        val answer = record.answers.getOrNull((row[1].toIntOrNull() ?: 0) - 1) ?: return null
        val value = unmask(row[3]).trim('"', '\'', ' ')
        val fits = row[2] in LABELS && value.length in 2..MAX_VALUE && accept(value)
        val grounded = value.replace(NOISE, "") in answer.text.replace(NOISE, "")
        return PortraitLine(answer.guestId, row[2], value, record.number).takeIf { fits && grounded }
    }
}
