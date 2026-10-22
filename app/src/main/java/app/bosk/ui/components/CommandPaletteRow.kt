package app.bosk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bosk.core.commands.CommandNote

/**
 * A horizontally-scrolling row of slash-command chips, shared by every AI input surface (terminal,
 * tutor, note editor). Tapping a chip drops the command into the prompt via [onPick]. Pass already
 * filtered [commands]; renders nothing when the list is empty.
 */
@Composable
fun CommandPaletteRow(
    commands: List<CommandNote.CommandDoc>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    monospace: Boolean = false,
) {
    if (commands.isEmpty()) return
    val family = if (monospace) FontFamily.Monospace else FontFamily.Default
    androidx.compose.foundation.layout.Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        commands.forEach { cmd ->
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { onPick("${cmd.name} ") }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(
                    cmd.name,
                    fontFamily = family,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
