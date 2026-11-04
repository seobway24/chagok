package com.team.chagok.feature.onair

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.team.chagok.core.model.AiSettings
import com.team.chagok.feature.onair.theme.LocalChagokColors
import kotlinx.coroutines.launch

/** AI 설정 대화상자가 부르는 동작 묶음. */
@Immutable
internal class AiActions(
    val test: suspend (AiSettings) -> Boolean,
    val save: (AiSettings) -> Unit,
    val clear: () -> Unit,
)

private enum class TestResult { IDLE, RUNNING, OK, FAILED }

private const val CLAUDE = "anthropic"
private const val OPENAI = "openai"
private const val CLAUDE_DEFAULT_MODEL = "claude-haiku-5-5"

/**
 * 외부 LLM 연결. 저장된 키는 화면으로 돌아오지 않는다 — 키 칸을 비워 두면 저장된 키를 그대로 쓴다.
 * 정리 화면에서만 열린다(주행 중 입력 금지).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AiSettingsDialog(status: AiStatusUi, actions: AiActions, onDismiss: () -> Unit) {
    val c = LocalChagokColors.current
    val scope = rememberCoroutineScope()
    var provider by remember { mutableStateOf(status.provider.ifBlank { CLAUDE }) }
    var key by remember { mutableStateOf("") }
    var model by remember { mutableStateOf(status.model.ifBlank { CLAUDE_DEFAULT_MODEL }) }
    var baseUrl by remember { mutableStateOf(status.baseUrl) }
    var result by remember { mutableStateOf(TestResult.IDLE) }
    var confirmClear by remember { mutableStateOf(false) }
    val draft = AiSettings(provider, key.trim(), model.trim(), baseUrl.trim())
    val ready = model.isNotBlank() && (key.isNotBlank() || status.hasSavedKey)

    EditorDialog(onDismiss) {
        Column {
            Text("AI 진행자 설정", color = c.ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("키를 넣으면 진행자가 대답에 맞춰 받아쳐요. 없으면 준비된 대사로 진행해요", color = c.sub, fontSize = 16.sp, lineHeight = 23.sp)
        }

        EditorLabel("서비스")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ChoiceChip("Claude", selected = provider == CLAUDE) {
                provider = CLAUDE
                if (model.isBlank()) model = CLAUDE_DEFAULT_MODEL
                result = TestResult.IDLE
            }
            ChoiceChip("OpenAI 호환", selected = provider == OPENAI) {
                provider = OPENAI
                if (model == CLAUDE_DEFAULT_MODEL) model = ""
                result = TestResult.IDLE
            }
        }

        EditorLabel("API 키")
        EditorField(
            key,
            { key = it; result = TestResult.IDLE },
            placeholder = if (status.hasSavedKey) "비워 두면 저장된 키를 써요" else "키를 붙여 넣어 주세요",
            keyboardType = KeyboardType.Password,
            visual = PasswordVisualTransformation(),
        )

        EditorLabel("모델 이름")
        EditorField(model, { model = it; result = TestResult.IDLE }, "예: gpt-4o-mini", keyboardType = KeyboardType.Ascii)

        EditorLabel("서버 주소")
        EditorField(baseUrl, { baseUrl = it; result = TestResult.IDLE }, "비워 두면 기본 주소를 써요", keyboardType = KeyboardType.Uri)

        Text(
            "탄 사람의 대답은 이름을 가린 채 이 서비스로 보내요. 키는 이 차에만 암호화해서 저장해요",
            color = c.sub, fontSize = 16.sp, lineHeight = 23.sp, modifier = Modifier.fillMaxWidth(),
        )
        when (result) {
            TestResult.OK -> StatusLed("연결됐어요", c.active, c.ink)
            TestResult.FAILED -> StatusLed("연결하지 못했어요. 키와 모델 이름을 확인해 주세요", c.warn, c.ink)
            TestResult.RUNNING -> StatusLed("확인하는 중이에요", c.sub, c.ink)
            TestResult.IDLE -> Unit
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (status.hasSavedKey) {
                ActionButton(if (confirmClear) "한 번 더 누르면 삭제" else "키 삭제", fill = null, ink = c.warn) {
                    if (confirmClear) {
                        actions.clear()
                        onDismiss()
                    } else {
                        confirmClear = true
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            ActionButton("취소", fill = null, ink = c.sub, onClick = onDismiss)
            ActionButton("연결 확인", fill = null, ink = c.ink, enabled = ready && result != TestResult.RUNNING) {
                result = TestResult.RUNNING
                scope.launch { result = if (actions.test(draft)) TestResult.OK else TestResult.FAILED }
            }
            ActionButton("저장", fill = primaryFill(), ink = Color.White, enabled = ready) {
                actions.save(draft)
                onDismiss()
            }
        }
    }
}
