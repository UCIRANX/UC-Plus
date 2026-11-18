package com.lumen.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import com.lumen.ui.screens.LocalStrings
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import com.lumen.ui.theme.expressivePalette

@Composable
fun BrandTopBar(
    onAddClick: (() -> Unit)? = null, modifier: Modifier = Modifier,
    title: String = "UC Plus", horizontalPadding: Dp = 16.dp,
    subtitle: String? = null, onClick: (() -> Unit)? = null,
    glowSubtitle: Boolean = false,
    actions: (@Composable () -> Unit)? = null
) {
    val art = expressivePalette()
    Row(modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)) {
        Text(title, modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = if (title == "UC Plus") 44.sp else 36.sp,
                letterSpacing = if (LocalStrings.current.localeCode == "fa") 0.sp else (-1.6).sp, lineHeight = 44.sp),
            fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) {
            if (glowSubtitle) GlowingSubtitle(subtitle)
            else Text(subtitle, color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        }
        if (actions != null) {
            actions()
        } else if (onAddClick != null) {
            Box(Modifier.expressiveButtonSemantics(LocalStrings.current.add, onAddClick).size(54.dp)
                .clip(expressiveShape(Motif.Clover)).background(art.lime)
                .clickable(role = Role.Button, onClick = onAddClick), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Add, null, tint = art.onLime, modifier = Modifier.size(28.dp))
            }
        } else {
            ExpressiveMotif(Motif.Sun, art.stage, Modifier.size(34.dp))
        }
    }
}

/**
 * Subtitle with a red neon light that orbits the letters, one turn every 3.6 seconds.
 * It runs all the time; there is no switch to turn it off.
 */
@Composable
private fun GlowingSubtitle(text: String) {
    val phase by rememberInfiniteTransition(label = "neon").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "neonPhase"
    )
    val angle = phase * 2f * PI.toFloat()
    Text(
        text,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge.copy(
            shadow = Shadow(
                color = Color(0xFFFF1744),
                offset = Offset(cos(angle) * 3f, sin(angle) * 3f),
                blurRadius = 18f
            )
        ),
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}
