package com.everettpenne.baliset.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.everettpenne.glass.GlassButton
import com.everettpenne.glass.GlassSheet
import com.everettpenne.glass.IosType
import com.everettpenne.glass.LocalGlassColors
import com.everettpenne.glass.glassPalette

/** A one-field glass sheet, for naming a playlist or renaming one. Enter saves; the sheet's own buttons do the same. */
@Composable
fun NameSheet(
    title: String,
    initial: String = "",
    confirmLabel: String = "Save",
    hint: String = "Name",
    onDone: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    GlassSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, style = IosType.title3.copy(fontWeight = FontWeight.SemiBold))
            Box(
                Modifier.fillMaxWidth().height(50.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(25.dp)).padding(horizontal = 18.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (name.isEmpty()) Text(hint, style = IosType.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    textStyle = IosType.body.copy(color = glassPalette().ink),
                    cursorBrush = SolidColor(LocalGlassColors.current.accent),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton("Cancel", onDismiss, Modifier.weight(1f))
                GlassButton(confirmLabel, { if (name.isNotBlank()) onDone(name.trim()) }, Modifier.weight(1f), enabled = name.isNotBlank(), prominent = true)
            }
            Spacer(Modifier.height(2.dp).imePadding())
        }
    }
}
