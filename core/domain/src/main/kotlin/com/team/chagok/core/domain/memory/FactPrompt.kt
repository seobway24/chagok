package com.team.chagok.core.domain.memory

import com.team.chagok.core.model.Answer
import com.team.chagok.core.model.FamilyFact

/**
 * AI 정보 뽑기 지시문과 답 읽기. 어느 LLM 카트리지든 같은 지시·같은 형식을 쓴다.
 *
 * 호칭 가리기는 붙이는 쪽 몫이다([speaker]·[text] 는 이미 가려져 들어오고, [parse] 의 unmask 로 되돌린다).
 */
object FactPrompt {

    private val LINE = Regex("""^\s*(\d+)\s*\|\s*(.+?)\s*$""")

    /** 대답 한 줄. 이어 묻기 코너 id 는 길어서 "callback" 으로 줄인다. */
    fun line(index: Int, speaker: String, cornerId: String, text: String) =
        "${index + 1}|$speaker|${cornerId.substringBefore(':')}|$text"

    fun build(lines: List<String>): String = """
        |가족이 차 안 팟캐스트에서 한 대답 목록이다. 한 줄 = 번호|사람|코너|대답.
        |각 대답에서 그 사람에 대해 다음에 다시 물어볼 만한 핵심 말(좋아하는 것·꿈·추억·별명거리)을 하나씩 뽑아라.
        |규칙: 키워드는 대답 안에 있는 말을 글자 그대로 2~16자로 잘라 쓴다. 바꿔 말하거나 지어내지 마라. 뽑을 게 없으면 그 줄은 건너뛴다.
        |출력: 한 줄에 하나, "번호|키워드" 만. 설명·머리말 없이.
        |
        |${lines.joinToString("\n")}
    """.trimMargin()

    /** "번호|키워드" 줄만 읽는다. 번호가 범위 밖이면 버린다. 사실 확인은 여기서 하지 않는다([FactCheck]). */
    fun parse(output: String, answers: List<Answer>, episode: Int, unmask: (String) -> String): List<FamilyFact> =
        output.lines().mapNotNull { row ->
            val m = LINE.find(row) ?: return@mapNotNull null
            val answer = answers.getOrNull(m.groupValues[1].toInt() - 1) ?: return@mapNotNull null
            val keyword = unmask(m.groupValues[2]).trim().trim('"', '\'', '「', '」')
            FamilyFact(answer.guestId, answer.cornerId, keyword, episode).takeIf { keyword.isNotEmpty() }
        }
}
