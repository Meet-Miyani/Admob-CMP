package dev.avinya.admob.showcase.ui.inspector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Inline "title + Inspect" row used by every feature screen.
 *
 * Renders nothing when [enabled] is false — the toggle in Settings owns the
 * "is the Inspector available" decision, and an absent row keeps the rest of
 * the screen untouched.
 *
 * Text glyphs only; the project bans material-icons.
 */
@Composable
fun InspectorEntryPoint(
    title: String,
    enabled: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!enabled) return
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onOpen) { Text("Inspect") }
    }
}
