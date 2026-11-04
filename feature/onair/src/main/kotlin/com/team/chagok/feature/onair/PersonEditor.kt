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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.team.chagok.core.model.Seat
import com.team.chagok.feature.onair.theme.LocalChagokColors

/**
 * 사람 카드 편집: 호칭 · 자리 · 아이 여부. 새 사람(id 빈 값)이면 "카드 만들기", 있던 사람이면 삭제도 된다.
 * 정차 화면에서만 열린다(주행 중 입력 금지 — 정리 화면은 대기·엔딩에만 보인다).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PersonEditor(
    card: PersonCard,
    onSave: (PersonCard) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalChagokColors.current
    val isNew = card.id.isBlank()
    var name by remember { mutableStateOf(card.nickname) }
    var seat by remember { mutableStateOf(card.seat) }
    var child by remember { mutableStateOf(card.isChild) }
    var confirmDelete by remember { mutableStateOf(false) }
    EditorDialog(onDismiss) {
        Column {
            Text(if (isNew) "새 사람 카드" else "카드 편집", color = c.ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("가족도, 함께 탄 친구도 카드가 돼요", color = c.sub, fontSize = 16.sp)
        }

        EditorLabel("부르는 이름")
        EditorField(name, { name = it.take(MAX_NAME) }, "예: 엄마, 막내, 지우", fontSize = 28.sp)

        EditorLabel("앉는 자리")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Seat.entries.forEach { s -> ChoiceChip(s.label, selected = s == seat) { seat = s } }
        }

        EditorLabel("어린이")
        ChoiceChip("아이예요 · 질문을 쉽게", selected = child) { child = !child }

        if (card.lines.isNotEmpty()) {
            EditorLabel("쌓인 기억")
            card.lines.forEach { Text(it, color = c.ink, fontSize = 18.sp, lineHeight = 25.sp) }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!isNew) {
                ActionButton(if (confirmDelete) "한 번 더 누르면 삭제" else "카드 삭제", fill = null, ink = c.warn) {
                    if (confirmDelete) onDelete() else confirmDelete = true
                }
            }
            Spacer(Modifier.weight(1f))
            ActionButton("취소", fill = null, ink = c.sub, onClick = onDismiss)
            ActionButton(if (isNew) "카드 만들기" else "저장", fill = primaryFill(), ink = Color.White, enabled = name.isNotBlank()) {
                onSave(card.copy(nickname = name.trim(), seat = seat, isChild = child))
            }
        }
    }
}

private const val MAX_NAME = 12
