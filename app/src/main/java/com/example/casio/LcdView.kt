package com.example.casio

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.casio.core.Lcd

/**
 * 把 [Lcd] 显示树画到屏幕上。
 *
 * 与 `core/LcdRender`(字符画)的分工:
 *  · LcdRender 出等宽字符画,用于自测与线性模式;
 *  · 这里出**真图形** —— 分数线、根号横线是 `drawBehind` 画的真实线条,
 *    分子在上、分母在下,指数与上下限是小字。
 *  · 字形仍是 Text 节点,所以自动化(uiautomator)与无障碍仍能读到文字。
 */
@Composable
fun LcdView(
    node: Lcd,
    baseSize: TextUnit = 26.sp,
    color: Color = Color(0xFF101010),
    modifier: Modifier = Modifier
) {
    val small = (baseSize.value * 0.62f).sp
    when (node) {
        is Lcd.Text -> Text(
            text = node.s,
            fontFamily = FontFamily.Monospace,
            fontSize = baseSize,
            fontWeight = FontWeight.Medium,
            color = color,
            maxLines = 1
        )

        is Lcd.Small -> LcdView(node.inner, small, color, modifier)

        is Lcd.Row -> Row(
            modifier = modifier,
            verticalAlignment = Alignment.Bottom
        ) {
            node.items.forEach { LcdView(it, baseSize, color) }
        }

        is Lcd.Frac -> {
            // 分子在上、分母在下,中间一条真实横线。
            // 列宽用 IntrinsicSize.Max 收缩到「分子/分母中较宽者」,
            // 这样分数线只跨过数字本身,而不是拉满整屏(真机就是如此)。
            val lineColor = color
            Row(verticalAlignment = Alignment.CenterVertically) {
                node.mixedWhole?.let {
                    LcdView(it, baseSize, color)
                    Box(Modifier.width(2.dp))
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = modifier.width(IntrinsicSize.Max)
                ) {
                    LcdView(node.num, baseSize, color)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.5.dp)
                            .drawBehind {
                                drawLine(
                                    color = lineColor,
                                    start = Offset(0f, size.height / 2f),
                                    end = Offset(size.width, size.height / 2f),
                                    strokeWidth = size.height
                                )
                            }
                    )
                    LcdView(node.den, baseSize, color)
                }
            }
        }

        is Lcd.Sqrt -> {
            // 根号:符号 + 一条覆盖被开方数的横线(画在内容上方)
            val lineColor = color
            Row(verticalAlignment = Alignment.Top) {
                node.degree?.let {
                    LcdView(it, small, color)
                    Box(Modifier.width(1.dp))
                }
                Text(
                    text = "√",
                    fontFamily = FontFamily.Monospace,
                    fontSize = baseSize,
                    color = color,
                    maxLines = 1
                )
                Box(
                    modifier = Modifier.drawBehind {
                        // 在被开方数顶部画横线
                        drawLine(
                            color = lineColor,
                            start = Offset(0f, 2f),
                            end = Offset(size.width, 2f),
                            strokeWidth = 2f
                        )
                    }.padding(top = 3.dp)
                ) {
                    LcdView(node.radicand, baseSize, color)
                }
            }
        }

        is Lcd.Pow -> Row(verticalAlignment = Alignment.Top) {
            LcdView(node.base, baseSize, color)
            Box(Modifier.padding(top = 1.dp)) {
                LcdView(node.exponent, small, color)
            }
        }

        is Lcd.Limits -> {
            // 积分/求和:符号左侧竖排上下限,右侧是主体
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    node.upper?.let { LcdView(it, small, color) }
                    Text(
                        text = node.op,
                        fontFamily = FontFamily.Monospace,
                        fontSize = (baseSize.value * 1.5f).sp,
                        color = color,
                        maxLines = 1
                    )
                    node.lower?.let { LcdView(it, small, color) }
                }
                Box(Modifier.width(2.dp))
                LcdView(node.body, baseSize, color)
            }
        }
    }
}

/**
 * 指示符行:E / i / ∠ / M / Math / S / A。
 * 真机这些显示在屏幕顶部,有小方框包住。
 */
@Composable
fun IndicatorRow(
    symbols: List<String>,
    color: Color = Color(0xFF101010),
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        symbols.forEach { s ->
            Box(
                modifier = Modifier
                    .border(1.dp, color, RoundedCornerShape(2.dp))
                    .padding(horizontal = 3.dp, vertical = 1.dp)
            ) {
                Text(
                    text = s,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    maxLines = 1
                )
            }
        }
    }
}
