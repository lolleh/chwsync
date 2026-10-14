package org.openmrs.chwsync.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B5E20),
    onPrimary = Color.White,
    secondary = Color(0xFF00695C),
    background = Color(0xFFF5F7F5),
    surface = Color.White,
    error = Color(0xFFB00020),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7CC47F),
    onPrimary = Color(0xFF003912),
    secondary = Color(0xFF4DB6AC),
    background = Color(0xFF101410),
    surface = Color(0xFF1B201B),
)

@Composable
fun ChwSyncTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
