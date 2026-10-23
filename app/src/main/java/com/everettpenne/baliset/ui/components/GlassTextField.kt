package com.everettpenne.baliset.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.everettpenne.glass.frostedGlass
import com.everettpenne.glass.glassPalette

/** The suite's frosted input field, for the Jellyfin address, username and password. */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onSubmit: () -> Unit = {},
) {
    val ink = glassPalette().ink
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = ink),
        cursorBrush = SolidColor(ink),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            imeAction = imeAction,
            keyboardType = if (password) KeyboardType.Password else keyboardType,
            autoCorrectEnabled = false,
        ),
        keyboardActions = KeyboardActions(
            onDone = { onSubmit() },
            onSearch = { onSubmit() },
            onGo = { onSubmit() },
            onNext = { onSubmit() },
        ),
        modifier = modifier.fillMaxWidth().height(52.dp)
            .frostedGlass(RoundedCornerShape(percent = 50), lensHeight = 10f, lensAmount = 20f),
        decorationBox = { inner ->
            Row(
                Modifier.padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (leading != null) {
                    Icon(leading, null, tint = ink.copy(alpha = .6f), modifier = Modifier.size(20.dp))
                }
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(placeholder, color = ink.copy(alpha = .45f), style = MaterialTheme.typography.bodyLarge)
                    }
                    inner()
                }
                if (value.isNotEmpty() && !password) {
                    Icon(
                        Icons.Rounded.Close,
                        "Clear",
                        tint = ink.copy(alpha = .6f),
                        modifier = Modifier.size(20.dp).clickable { onValueChange("") },
                    )
                }
            }
        },
    )
}
