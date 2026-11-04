package com.team.chagok.content

import com.team.chagok.core.model.SignalCategory
import com.team.chagok.core.quip.QuipRequest
import com.team.chagok.core.quip.QuipResult
import com.team.chagok.core.quip.TemplateQuip
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 진행자 받아치기가 가족 대답을 어색하게 되읽지 않는지 (시연 녹화 11/03):
 * "누나 덕분에 차 안이 환해졌어요. 떡볶이요!!", "벨트 잘 매 줘서 고마워요, 그 대답 기다렸어요."
 */
class ReactionLineTest {

    private val corners = BuiltinShow.basicPack.corners.map { it.id }
    private val skins = BuiltinShow.skins.map { it.id }

    private suspend fun reactions(corner: String, skin: String, answer: String): List<String> {
        val quip = TemplateQuip(BuiltinShow.templates, Random(corner.hashCode() + skin.hashCode()))
        return (1..12).map {
            val request = QuipRequest(corner, skin, "누나", answer, SignalCategory.QUIET, kidSafe = true)
            (quip.quip(request) as QuipResult.Line).text
        }
    }

    @Test
    fun `a sentence answer is never glued onto a stock line`() = runTest {
        val sentence = "벨트 잘 매 줘서 고마워요"
        corners.forEach { corner ->
            skins.forEach { skin ->
                reactions(corner, skin, sentence).forEach { line ->
                    assertFalse("$corner/$skin: $line", "고마워요" in line)
                }
            }
        }
    }

    @Test
    fun `a short answer is echoed only up front and without doubled punctuation`() = runTest {
        corners.forEach { corner ->
            skins.forEach { skin ->
                reactions(corner, skin, "떡볶이요!").forEach { line ->
                    assertFalse("$corner/$skin: $line", "!!" in line)
                    if ("떡볶이요" in line) {
                        assertTrue("$corner/$skin: $line", line.startsWith("떡볶이요") || line.startsWith("오, 떡볶이요"))
                    }
                }
            }
        }
    }

    /** 제14회 녹화: "역시 막내, 이번 회 하이라이트 확정!" 이 터널 토크와 도착 예고에서 두 번 나왔다. */
    @Test
    fun `one episode of sentence answers never repeats a reaction even after a rehearsal`() = runTest {
        val quip = TemplateQuip(BuiltinShow.templates, Random(14))
        suspend fun react(corner: String, answer: String) =
            (quip.quip(QuipRequest(corner, "basic", "막내", answer, SignalCategory.QUIET, kidSafe = true)) as QuipResult.Line).text
        // 같은 앱 프로세스에서 리허설 한 회를 먼저 돌렸다
        repeat(5) { react("callback:x", "벨트 잘 매 줘서 고마워요") }
        val corners = listOf("callback:a", "tunnel", "door", "milestone", "callback:b", "arrival", "best-moment", "callback:c")
        val episode = corners.map { react(it, "음 그러니까 그날 다 같이 웃었어요") }
        assertEquals(episode.toString(), episode.size, episode.toSet().size)
    }

    @Test
    fun `reaction lines keep the brand rules`() = runTest {
        corners.forEach { corner ->
            skins.forEach { skin ->
                (reactions(corner, skin, "떡볶이요") + reactions(corner, skin, "음 그러니까 그날 다 같이 웃었어요")).forEach { line ->
                    listOf("이야기", "라디오", "게임", "퀴즈").forEach { word -> assertFalse("$corner/$skin: $line", word in line) }
                }
            }
        }
    }
}
