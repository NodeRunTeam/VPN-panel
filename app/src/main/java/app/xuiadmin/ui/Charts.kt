package app.xuiadmin.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GlassDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(LocalGlass.current.text.copy(alpha = 0.07f)))
}

@Composable
fun Chip(text: String, color: Color) {
    val t = LocalGlass.current
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(t.text.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(color)
        HSpace(8)
        Text(text, color = t.text, fontSize = 12.sp, maxLines = 1)
    }
}

/** Кольцевой индикатор с градиентной дугой и мягким свечением. */
@Composable
fun RingGauge(
    fraction: Float,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    stroke: Dp = 10.dp,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val t = LocalGlass.current
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            val d = Size(size.width - sw, size.height - sw)
            val tl = Offset(sw / 2f, sw / 2f)
            drawArc(
                color = t.track, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = tl, size = d, style = Stroke(sw),
            )
            val f = fraction.coerceIn(0f, 1f)
            if (f > 0.003f) {
                rotate(-90f) {
                    val stops = Array(colors.size) { i ->
                        (if (colors.size == 1) 0f else i.toFloat() / (colors.size - 1) * f) to colors[i]
                    }
                    val brush = Brush.sweepGradient(*stops, center = center)
                    // свечение
                    drawArc(
                        color = colors.last().copy(alpha = 0.16f), startAngle = 0f, sweepAngle = 360f * f,
                        useCenter = false, topLeft = tl, size = d,
                        style = Stroke(sw * 1.7f, cap = StrokeCap.Round),
                    )
                    drawArc(
                        brush = brush, startAngle = 0f, sweepAngle = 360f * f,
                        useCenter = false, topLeft = tl, size = d,
                        style = Stroke(sw, cap = StrokeCap.Round),
                    )
                }
            }
        }
        content()
    }
}

/** Плавная светящаяся линия с заливкой — для истории нагрузки. */
@Composable
fun GlowLine(values: List<Float>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val w = size.width
        val h = size.height
        val n = values.size
        val maxV = maxOf(20f, values.max() * 1.25f)
        val xs = FloatArray(n) { it * w / (n - 1) }
        val ys = FloatArray(n) { h - (values[it] / maxV).coerceIn(0f, 1f) * h * 0.8f - h * 0.1f }

        val line = Path().apply {
            moveTo(xs[0], ys[0])
            for (i in 1 until n) {
                val mx = (xs[i - 1] + xs[i]) / 2f
                cubicTo(mx, ys[i - 1], mx, ys[i], xs[i], ys[i])
            }
        }
        val fill = Path().apply {
            moveTo(xs[0], h)
            lineTo(xs[0], ys[0])
            for (i in 1 until n) {
                val mx = (xs[i - 1] + xs[i]) / 2f
                cubicTo(mx, ys[i - 1], mx, ys[i], xs[i], ys[i])
            }
            lineTo(xs[n - 1], h)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), Color.Transparent), startY = 0f, endY = h))
        drawPath(line, color.copy(alpha = 0.18f), style = Stroke(8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(
            line,
            Brush.horizontalGradient(listOf(color.copy(alpha = 0.25f), color, Color(0xFF93C5FD))),
            style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        for (i in 0 until n step 4) {
            drawCircle(color, 2.dp.toPx(), Offset(xs[i], ys[i]))
        }
        val last = Offset(xs[n - 1], ys[n - 1])
        drawCircle(color.copy(alpha = 0.35f), 8.dp.toPx(), last)
        drawCircle(Color.White, 3.5.dp.toPx(), last)
    }
}

/** Столбчатый график: тонкие закруглённые столбики на «дорожке», максимум подписан сверху. */
@Composable
fun BarsChart(items: List<Pair<String, Long>>, modifier: Modifier = Modifier, height: Dp = 110.dp) {
    val t = LocalGlass.current
    val maxV = (items.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(1L)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        items.forEach { (label, v) ->
            val f = v.toFloat() / maxV.toFloat()
            val isMax = v == maxV && v > 0
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (isMax) formatBytes(v) else "",
                    color = GlassColors.Blue, fontSize = 10.sp, maxLines = 1,
                    modifier = Modifier.height(16.dp),
                )
                Box(Modifier.width(10.dp).height(height).clip(CircleShape).background(t.track)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(f.coerceIn(0.04f, 1f))
                            .align(Alignment.BottomCenter)
                            .clip(CircleShape)
                            .background(Brush.verticalGradient(listOf(Color(0xFF60A5FA), Color(0xFF2563EB)))),
                    )
                }
                VSpace(6)
                Text(label.take(6), color = t.textDim, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}
