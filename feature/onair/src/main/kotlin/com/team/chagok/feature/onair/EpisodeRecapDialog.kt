package com.team.chagok.feature.onair

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.team.chagok.feature.onair.theme.LocalChagokColors

/** 지난 회 정리: 한 줄 요약 · 명장면(원문) · 인사이트. 쌓인 기억 지우기도 여기 있다. 정차 중에만 열린다. */
@Composable
internal fun EpisodeRecapDialog(recap: EpisodeRecapUi?, canForget: Boolean, onForget: () -> Unit, clipName: String? = null, onListen: () -> Unit = {}, onDismiss: () -> Unit) {
    val c = LocalChagokColors.current
    var confirming by remember { mutableStateOf(false) }
    EditorDialog(onDismiss) {
        Text(recap?.let { "제${it.number}회 정리" } ?: "회차 정리", color = c.ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        if (recap == null) {
            Text("방송이 끝나면 여기에 정리돼요", color = c.sub, fontSize = 17.sp)
        } else {
            Text(recap.oneLine, color = c.ink, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, lineHeight = 29.sp)
            if (recap.highlights.isNotEmpty()) {
                EditorLabel("명장면")
                recap.highlights.forEach { (who, text) ->
                    Column {
                        Text(who, color = c.sub, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(text, color = c.ink, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp)
                    }
                }
            }
            if (recap.insights.isNotEmpty()) {
                EditorLabel("이번 회에 보인 것")
                recap.insights.forEach { Text(it, color = c.ink, fontSize = 18.sp, lineHeight = 25.sp) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (canForget) {
                // 실수로 지우지 않게 두 번 눌러야 지운다
                ActionButton(if (confirming) "한 번 더 누르면 지워요" else "기억 지우기", fill = null, ink = c.warn) {
                    if (confirming) {
                        onForget()
                        onDismiss()
                    } else {
                        confirming = true
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            clipName?.let { ActionButton("${it}의 한마디 듣기", fill = null, ink = c.ink, onClick = onListen) }
            ActionButton("닫기", fill = null, ink = c.ink, onClick = onDismiss)
        }
    }
}
