package com.team.chagok.feature.onair

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.team.chagok.feature.onair.theme.ChagokShapes
import com.team.chagok.feature.onair.theme.LocalChagokColors

// 정차 화면 대화상자 공용 부품: 판 · 라벨 · 입력 칸 · 고르기 · 버튼.

/** Dialog 는 따로 창을 띄워 화면 밀도 보정(OnAirScreen)이 끊긴다 → 바깥 값을 다시 넣는다. */
@Composable
internal fun EditorDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalChagokColors.current
    val density = LocalDensity.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalDensity provides density) {
            Column(
                Modifier.padding(24.dp).widthIn(max = 640.dp).fillMaxWidth().clip(ChagokShapes.Control)
                    .background(c.panel)
                    // 키보드가 올라오면 창이 줄어든다 → 버튼까지 밀어 올려 볼 수 있게
                    .verticalScroll(rememberScrollState())
                    .padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

@Composable
internal fun EditorLabel(text: String) {
    Text(text, color = LocalChagokColors.current.sub, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
}

/** 밑줄 입력 칸. 항상 밑줄을 그어 어디에 쓰는지 멀리서도 보이게 한다. */
@Composable
internal fun EditorField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    fontSize: TextUnit = 20.sp,
    keyboardType: KeyboardType = KeyboardType.Text,
    visual: VisualTransformation = VisualTransformation.None,
) {
    val c = LocalChagokColors.current
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(color = c.ink, fontSize = fontSize, fontWeight = FontWeight.Bold),
        cursorBrush = SolidColor(c.accent),
        visualTransformation = visual,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
        decorationBox = { field ->
            Column {
                Box(Modifier.heightIn(min = 44.dp), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(placeholder, color = c.sub, fontSize = fontSize, fontWeight = FontWeight.Medium, maxLines = 1)
                    field()
                }
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(2.dp).background(c.accent))
            }
        },
    )
}

@Composable
internal fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalChagokColors.current
    val press = rememberPress()
    Box(
        Modifier.pressScale(press).heightIn(min = 56.dp).clip(ChagokShapes.Control)
            .background(if (selected) c.ink else Color.Transparent)
            .border(if (selected) 2.dp else 1.dp, if (selected) c.ink else c.line, ChagokShapes.Control)
            .pressable(press, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) Color.White else c.sub, fontSize = 18.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
internal fun ActionButton(text: String, fill: Brush?, ink: Color, enabled: Boolean = true, onClick: () -> Unit) {
    val c = LocalChagokColors.current
    val press = rememberPress()
    val base = Modifier.pressScale(press).heightIn(min = 56.dp).clip(ChagokShapes.Control)
    val painted = if (fill != null) base.background(fill) else base.border(1.dp, c.line, ChagokShapes.Control)
    Box(
        painted.pressable(press, enabled, onClick).padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = ink.copy(alpha = if (enabled) 1f else 0.45f), fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

/** 대화상자의 주 버튼 색. */
@Composable
internal fun primaryFill(): Brush = SolidColor(LocalChagokColors.current.go)
