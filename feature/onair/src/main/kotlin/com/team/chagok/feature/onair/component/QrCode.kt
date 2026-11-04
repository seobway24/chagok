package com.team.chagok.feature.onair.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * 글을 QR 로 그린다. 다크 모드여도 흰 바탕·검은 칸 고정 — 카메라는 반전 QR 을 못 읽는 경우가 많다.
 */
@Composable
fun QrCode(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) { encode(text) }
    Canvas(modifier.aspectRatio(1f)) {
        drawRect(Color.White)
        val cell = size.minDimension / matrix.width
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix[x, y]) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell, cell))
            }
        }
    }
}

private fun encode(text: String): BitMatrix = QRCodeWriter().encode(
    text,
    BarcodeFormat.QR_CODE,
    0,
    0,
    mapOf(
        EncodeHintType.CHARACTER_SET to "UTF-8",
        // 차 화면을 폰으로 찍는 거리라 칸이 커야 한다 → 오류 정정은 낮게(L), 칸 수를 줄인다
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
        EncodeHintType.MARGIN to 2,
    ),
)
