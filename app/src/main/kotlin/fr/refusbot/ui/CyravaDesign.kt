package fr.refusbot.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.refusbot.R

/** Couleurs de marque CYRAVA, identiques en thème clair et sombre (carte héros, bouton central). */
object CyravaColors {
    val Night = Color(0xFF040D18)
    val Navy = Color(0xFF071A2C)
    val Deep = Color(0xFF0C2A44)
    val Cyan = Color(0xFF4FE3F5)
    val Gold = Color(0xFFF2BE5C)
    val Green = Color(0xFF4ADE9B)
    val Red = Color(0xFFFF6B6B)
    val Text = Color(0xFFEFF7FB)
    val TextMuted = Color(0xFFA9BFCD)

    /** Dégradé de fond de la carte principale. */
    val heroBrush: Brush
        get() = Brush.verticalGradient(listOf(Deep, Navy, Night))
}

/** État de protection affiché sur l'accueil, dans l'ordre de priorité des problèmes. */
enum class ProtectionState(@StringRes val titleRes: Int, val accent: Color) {
    LOADING(R.string.home_state_loading, CyravaColors.TextMuted),
    SERVICE_OFF(R.string.home_state_off, CyravaColors.Red),
    SERVICE_STOPPED(R.string.home_state_stopped, CyravaColors.Red),
    NO_APPS(R.string.home_state_no_apps, CyravaColors.Gold),
    PAUSED(R.string.home_state_paused, CyravaColors.Gold),
    ACTIVE(R.string.home_state_active, CyravaColors.Green),
}

/**
 * Grand bouton circulaire façon VPN : halo animé quand la protection est active.
 * [onClick] déclenche l'action principale de l'état affiché (activer, reprendre, mettre en pause…).
 */
@Composable
fun ProtectionOrb(
    state: ProtectionState,
    actionLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 210.dp,
) {
    val accent = state.accent
    val transition = rememberInfiniteTransition(label = "orbe")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 2600, easing = LinearEasing), RepeatMode.Restart),
        label = "onde",
    )
    val active = state == ProtectionState.ACTIVE
    val title = stringResource(state.titleRes)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .clickable(onClickLabel = actionLabel, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "$title. $actionLabel" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = this.size.minDimension / 2f
            // Ondes qui s'éloignent : uniquement quand la protection tourne vraiment.
            if (active) {
                for (i in 0 until 3) {
                    val p = (pulse + i / 3f) % 1f
                    drawCircle(
                        color = accent.copy(alpha = (1f - p) * 0.32f),
                        radius = radius * (0.52f + 0.46f * p),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            } else {
                drawCircle(
                    color = accent.copy(alpha = 0.18f),
                    radius = radius * 0.92f,
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
            // Halo lumineux.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = if (active) 0.34f else 0.18f), Color.Transparent),
                    center = center,
                    radius = radius * 0.82f,
                ),
                radius = radius * 0.82f,
            )
            // Disque central.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(CyravaColors.Deep, CyravaColors.Night),
                    center = center,
                    radius = radius * 0.5f,
                ),
                radius = radius * 0.5f,
            )
            drawCircle(color = accent, radius = radius * 0.5f, style = Stroke(width = 3.dp.toPx()))
        }
        Icon(
            painter = painterResource(R.drawable.ic_shield),
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(size * 0.26f),
        )
    }
}

/** Pastille d'état : point coloré + texte court. */
@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = color)
    }
}

/** Tuile chiffrée : grand nombre, libellé dessous. Les nombres viennent toujours de l'historique réel. */
@Composable
fun StatTile(
    value: String,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(scheme.surfaceContainerLow)
            .border(1.dp, scheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) {
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(icon), contentDescription = null, tint = accent, modifier = Modifier.size(17.dp))
            }
        }
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = scheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, maxLines = 2)
    }
}

/** Emblème CYRAVA (phénix, bouclier et œil) arrondi avec liseré doré. Image PNG vérifiée. */
@Composable
fun CyravaEmblem(size: Dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.cyrava_emblem),
        contentDescription = stringResource(R.string.emblem),
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .border(1.dp, CyravaColors.Gold.copy(alpha = 0.7f), RoundedCornerShape(size * 0.3f)),
    )
}

/** En-tête de marque : emblème, nom, signature. */
@Composable
fun CyravaBrandHeader(modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CyravaEmblem(size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "CYRAVA",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "DIGITAL PRIVACY",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        trailing()
    }
}

/** Ligne de la liste « À régler » : icône d'état, texte, action. */
@Composable
fun ChecklistRow(
    title: String,
    detail: String,
    ok: Boolean,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    val color = if (ok) CyravaColors.Green else MaterialTheme.colorScheme.tertiary
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.padding(top = 2.dp).size(26.dp).clip(CircleShape).background(color.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(if (ok) R.drawable.ic_check else R.drawable.ic_warning),
                contentDescription = if (ok) stringResource(R.string.check_ok) else stringResource(R.string.check_todo),
                tint = color,
                modifier = Modifier.size(15.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (actionLabel != null) {
                TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)) {
                    Text(actionLabel, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** Texte centré clair, pour la carte héros sombre. */
@Composable
fun HeroText(text: String, muted: Boolean = false, big: Boolean = false) {
    Text(
        text,
        style = if (big) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyMedium,
        fontWeight = if (big) FontWeight.ExtraBold else FontWeight.Normal,
        color = if (muted) CyravaColors.TextMuted else CyravaColors.Text,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}
