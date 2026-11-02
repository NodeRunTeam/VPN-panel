package app.xuiadmin.ui

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// ---------------- тема ----------------

@Immutable
class GlassTheme(
    val dark: Boolean,
    val text: Color,
    val textDim: Color,
    val fillTop: Color,
    val fillBottom: Color,
    val sheen: Color,
    val edgeA: Color,
    val edgeB: Color,
    val edgeC: Color,
    val track: Color,
    val offDot: Color,
    val dialogBg: Color,
)

val DarkGlass = GlassTheme(
    dark = true,
    text = Color.White,
    textDim = Color.White.copy(alpha = 0.66f),
    fillTop = Color.White.copy(alpha = 0.18f),
    fillBottom = Color.White.copy(alpha = 0.06f),
    sheen = Color.White.copy(alpha = 0.14f),
    edgeA = Color.White.copy(alpha = 0.70f),
    edgeB = Color.White.copy(alpha = 0.06f),
    edgeC = Color.White.copy(alpha = 0.30f),
    track = Color.White.copy(alpha = 0.12f),
    offDot = Color.White.copy(alpha = 0.30f),
    dialogBg = Color(0xF01B2342),
)

val LightGlass = GlassTheme(
    dark = false,
    text = Color(0xFF0F172A),
    textDim = Color(0xFF0F172A).copy(alpha = 0.60f),
    fillTop = Color.White.copy(alpha = 0.60f),
    fillBottom = Color.White.copy(alpha = 0.26f),
    sheen = Color.White.copy(alpha = 0.55f),
    edgeA = Color.White.copy(alpha = 0.95f),
    edgeB = Color.Black.copy(alpha = 0.06f),
    edgeC = Color.White.copy(alpha = 0.70f),
    track = Color.Black.copy(alpha = 0.08f),
    offDot = Color.Black.copy(alpha = 0.25f),
    dialogBg = Color(0xF5FFFFFF),
)

val LocalGlass = staticCompositionLocalOf { DarkGlass }

object GlassColors {
    val Text: Color @Composable @ReadOnlyComposable get() = LocalGlass.current.text
    val TextDim: Color @Composable @ReadOnlyComposable get() = LocalGlass.current.textDim
    val Blue = Color(0xFF0A84FF)
    val Purple = Color(0xFFBF5AF2)
    val Green = Color(0xFF30D158)
    val Orange = Color(0xFFFF9F0A)
    val Red = Color(0xFFFF453A)
    val Slate = Color(0xFF64748B)
}

private fun edgeBrush(t: GlassTheme) =
    Brush.linearGradient(listOf(t.edgeA, t.edgeB, t.edgeC))

// ---------------- фон и размытие ----------------

/** Всё, что нужно стеклу, чтобы перерисовать фон позади себя и размыть его. */
class Backdrop(
    val phase: State<Float>,
    val size: State<IntSize>,
    val blur: Boolean,
    val dark: Boolean,
)

val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** Рисует весь фон (градиент, световые пятна и чёткие «шары»). Координаты — в системе всего экрана. */
fun DrawScope.drawBackdrop(dark: Boolean, v: Float, w: Float, h: Float) {
    val bg = if (dark) {
        listOf(Color(0xFF0A0F24), Color(0xFF111A3A), Color(0xFF0B1226))
    } else {
        listOf(Color(0xFFEEF3FF), Color(0xFFE6ECFF), Color(0xFFF6EFFF))
    }
    drawRect(Brush.verticalGradient(bg, startY = 0f, endY = h), size = Size(w, h))

    val a = if (dark) 0.50f else 0.55f
    blob(Color(0xFF3B82F6), Offset(w * (0.15f + 0.55f * v), h * (0.12f + 0.10f * v)), w * 0.75f, a)
    blob(Color(0xFFA855F7), Offset(w * (0.95f - 0.55f * v), h * (0.45f + 0.10f * (1 - v))), w * 0.70f, a)
    blob(Color(0xFF06B6D4), Offset(w * (0.20f + 0.35f * v), h * (0.92f - 0.15f * v)), w * 0.75f, a)
    blob(Color(0xFFEC4899), Offset(w * (0.80f - 0.30f * v), h * (0.75f - 0.10f * v)), w * 0.45f, a)

    // чёткие формы: именно они делают размытие под стеклом заметным
    val o = if (dark) 0.90f else 0.85f
    orb(Offset(w * (0.84f - 0.22f * v), h * 0.16f), w * 0.20f, Color(0xFF60A5FA).copy(alpha = o), Color(0xFF7C3AED).copy(alpha = o * 0.7f))
    orb(Offset(w * (0.10f + 0.22f * v), h * 0.50f), w * 0.19f, Color(0xFFF472B6).copy(alpha = o), Color(0xFFF97316).copy(alpha = o * 0.7f))
    orb(Offset(w * (0.72f - 0.18f * v), h * 0.84f), w * 0.25f, Color(0xFF22D3EE).copy(alpha = o), Color(0xFF3B82F6).copy(alpha = o * 0.7f))
}

private fun DrawScope.blob(color: Color, center: Offset, radius: Float, alpha: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), Color.Transparent),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

private fun DrawScope.orb(center: Offset, r: Float, c1: Color, c2: Color) {
    drawCircle(
        brush = Brush.linearGradient(
            colors = listOf(c1, c2),
            start = Offset(center.x - r, center.y - r),
            end = Offset(center.x + r, center.y + r),
        ),
        radius = r,
        center = center,
    )
}

@Composable
fun GlassBackground(
    dark: Boolean,
    blur: Boolean,
    animate: Boolean,
    content: @Composable BoxScope.() -> Unit,
) {
    val phase: State<Float> = if (animate) {
        rememberInfiniteTransition(label = "bg").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(18000, easing = LinearEasing), RepeatMode.Reverse),
            label = "p",
        )
    } else {
        remember { mutableStateOf(0.35f) }
    }
    val rootSize = remember { mutableStateOf(IntSize.Zero) }
    val backdrop = remember(dark, blur, animate) { Backdrop(phase, rootSize, blur, dark) }

    CompositionLocalProvider(LocalBackdrop provides backdrop) {
        Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged { rootSize.value = it }
                .drawBehind { drawBackdrop(dark, phase.value, this.size.width, this.size.height) },
        ) {
            content()
        }
    }
}

// ---------------- стеклянные компоненты ----------------

/**
 * Стеклянная поверхность. На Android 12+ позади неё перерисовывается фон и размывается
 * (настоящее размытие подложки), сверху — полупрозрачная заливка, блик и светящаяся кромка.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape,
    onClick: (() -> Unit)? = null,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    val t = LocalGlass.current
    val bd = LocalBackdrop.current
    var pos by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = contentAlignment,
    ) {
        if (bd != null && bd.blur && Build.VERSION.SDK_INT >= 31) {
            Box(
                Modifier
                    .matchParentSize()
                    .onGloballyPositioned { pos = it.positionInRoot() }
                    .graphicsLayer {
                        val r = 22.dp.toPx()
                        renderEffect = BlurEffect(r, r, TileMode.Clamp)
                    }
                    .drawBehind {
                        val s = bd.size.value
                        if (s.width > 0 && s.height > 0) {
                            translate(left = -pos.x, top = -pos.y) {
                                drawBackdrop(bd.dark, bd.phase.value, s.width.toFloat(), s.height.toFloat())
                            }
                        }
                    },
            )
        }
        Box(
            Modifier
                .matchParentSize()
                .background(Brush.linearGradient(listOf(t.fillTop, t.fillBottom)))
                .drawWithCache {
                    val sheen = Brush.verticalGradient(
                        colors = listOf(t.sheen, Color.Transparent),
                        startY = 0f,
                        endY = size.height * 0.45f,
                    )
                    onDrawBehind { drawRect(sheen) }
                }
                .border(1.dp, edgeBrush(t), shape),
        )
        content()
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    corner: Dp = 28.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassSurface(modifier = modifier, shape = RoundedCornerShape(corner), onClick = onClick) {
        Column(Modifier.fillMaxWidth().padding(18.dp), content = content)
    }
}

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = GlassColors.Blue,
    enabled: Boolean = true,
    small: Boolean = false,
) {
    val shape = RoundedCornerShape(50)
    val t = LocalGlass.current
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.5f)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.95f), tint.copy(alpha = 0.60f))))
            .border(1.dp, edgeBrush(t), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (small) 14.dp else 22.dp, vertical = if (small) 9.dp else 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = if (small) 13.sp else 16.sp, maxLines = 1)
    }
}

@Composable
fun GlassIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    GlassSurface(
        modifier = Modifier.size(44.dp),
        shape = CircleShape,
        onClick = onClick,
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = LocalGlass.current.text, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun GlassField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val t = LocalGlass.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType),
        shape = RoundedCornerShape(20.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = t.text.copy(alpha = 0.10f),
            unfocusedContainerColor = t.text.copy(alpha = 0.06f),
            focusedBorderColor = t.text.copy(alpha = 0.75f),
            unfocusedBorderColor = t.text.copy(alpha = 0.25f),
            focusedTextColor = t.text,
            unfocusedTextColor = t.text,
            cursorColor = t.text,
            focusedLabelColor = t.text.copy(alpha = 0.85f),
            unfocusedLabelColor = t.text.copy(alpha = 0.60f),
        ),
    )
}

@Composable
fun GlassSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val t = LocalGlass.current
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = GlassColors.Green,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = if (t.dark) Color.White.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.18f),
            uncheckedBorderColor = Color.Transparent,
        ),
    )
}

@Composable
fun GlassProgress(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val t = LocalGlass.current
    Box(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(CircleShape)
            .background(t.track),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.65f), color))),
        )
    }
}

@Composable
fun StatusDot(color: Color) {
    Box(Modifier.size(10.dp).clip(CircleShape).background(color))
}

@Composable
fun VSpace(h: Int) = Spacer(Modifier.height(h.dp))

@Composable
fun HSpace(w: Int) = Spacer(Modifier.size(w.dp, 1.dp))

/** Диалог в отдельном окне: размытие подложки там не работает, поэтому заливка почти непрозрачная. */
@Composable
fun GlassDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val t = LocalGlass.current
    val shape = RoundedCornerShape(28.dp)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(24.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(t.dialogBg)
                .border(1.dp, edgeBrush(t), shape)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            content = content,
        )
    }
}
