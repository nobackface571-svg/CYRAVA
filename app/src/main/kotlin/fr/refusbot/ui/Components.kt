package fr.refusbot.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.refusbot.R
import fr.refusbot.core.OutcomeStatus

enum class CardTone { NORMAL, OK, WARNING, ERROR }

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    tone: CardTone = CardTone.NORMAL,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val (container, accent) = when (tone) {
        CardTone.NORMAL -> scheme.surfaceContainerLow to scheme.primary
        CardTone.OK -> scheme.surfaceContainer to scheme.primary
        CardTone.WARNING -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        CardTone.ERROR -> scheme.errorContainer to scheme.onErrorContainer
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.55f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 17.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(painterResource(icon), contentDescription = null, tint = accent, modifier = Modifier.size(21.dp))
                    Spacer(Modifier.width(11.dp))
                }
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    color = if (tone == CardTone.NORMAL) scheme.onSurface else accent)
            }
            content()
        }
    }
}

@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            if (supporting != null) Text(supporting, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
fun BodyText(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = modifier,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun NumberedStep(number: Int, text: String) {
    Row(Modifier.fillMaxWidth()) {
        Text("$number.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
            modifier = Modifier.width(24.dp), color = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
fun PageHeader(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
            }
            Spacer(Modifier.width(4.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground)
            // Trait de marque cyan/or sous le titre de chaque page.
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(width = 26.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.primary))
                Box(Modifier.size(width = 8.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.tertiary))
            }
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight)
        }
    }
}

@Composable
fun NavRow(title: String, subtitle: String?, @DrawableRes icon: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClickLabel = stringResource(R.string.action_open), onClick = onClick)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Icon(painterResource(icon), contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp).size(21.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit, detail: String? = null, enabled: Boolean = true) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun statusColor(status: OutcomeStatus): Color = when (status) {
    // Refus réussis en vert : vérifié (vu) ou fait avec le bouton de refus du site (non vérifiable,
    // ce que précise toujours le libellé).
    OutcomeStatus.OPTIONS_SUCCESS, OutcomeStatus.UNVERIFIED, OutcomeStatus.DIRECT_SUCCESS -> CyravaColors.Green
    OutcomeStatus.PARTIAL, OutcomeStatus.HANDED_OVER -> MaterialTheme.colorScheme.tertiary
    OutcomeStatus.INCOMPLETE_CAPTURE, OutcomeStatus.IMPOSSIBLE, OutcomeStatus.INTERRUPTED -> MaterialTheme.colorScheme.error
    OutcomeStatus.OBSERVED -> MaterialTheme.colorScheme.onSurfaceVariant
}

@DrawableRes
fun statusIcon(status: OutcomeStatus): Int = when (status) {
    OutcomeStatus.OPTIONS_SUCCESS, OutcomeStatus.UNVERIFIED, OutcomeStatus.DIRECT_SUCCESS -> R.drawable.ic_check
    OutcomeStatus.PARTIAL, OutcomeStatus.INCOMPLETE_CAPTURE, OutcomeStatus.IMPOSSIBLE, OutcomeStatus.HANDED_OVER -> R.drawable.ic_warning
    OutcomeStatus.INTERRUPTED -> R.drawable.ic_stop
    OutcomeStatus.OBSERVED -> R.drawable.ic_info
}


@Composable
fun AutoDiagnosticSwitch(checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    SwitchRow(
        label = stringResource(R.string.autodiag_t),
        supporting = stringResource(R.string.autodiag_d),
        checked = checked, enabled = enabled,
        onCheckedChange = { v -> if (v) confirm = true else onChange(false) },
    )
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.autodiag_confirm_t)) },
            text = { Text(stringResource(R.string.autodiag_confirm)) },
            confirmButton = { TextButton(onClick = { confirm = false; onChange(true) }) { Text(stringResource(R.string.autodiag_confirm_ok)) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.apps_cancel)) } },
        )
    }
}
