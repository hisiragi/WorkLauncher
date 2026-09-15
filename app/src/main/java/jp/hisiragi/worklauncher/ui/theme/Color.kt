package jp.hisiragi.worklauncher.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// A calm, desk-friendly palette: deep navy for chrome, amber for the timer,
// and a restrained green for anything "done".
private val Navy10 = Color(0xFF0B1A2A)
private val Navy20 = Color(0xFF13273C)
private val Navy30 = Color(0xFF1B3A5C)
private val Navy40 = Color(0xFF2B5382)
private val Navy80 = Color(0xFFA8C8EE)
private val Navy90 = Color(0xFFD3E3FA)

private val Amber30 = Color(0xFF7A4E00)
private val Amber40 = Color(0xFFA06A00)
private val Amber80 = Color(0xFFFFB95C)
private val Amber90 = Color(0xFFFFDDB0)

private val Teal30 = Color(0xFF00504B)
private val Teal40 = Color(0xFF006A63)
private val Teal80 = Color(0xFF52DBD0)
private val Teal90 = Color(0xFF7BF7EB)

private val Red40 = Color(0xFFB3261E)
private val Red80 = Color(0xFFFFB4AB)

val LightColors = lightColorScheme(
    primary = Navy30,
    onPrimary = Color.White,
    primaryContainer = Navy90,
    onPrimaryContainer = Navy10,
    secondary = Teal40,
    onSecondary = Color.White,
    secondaryContainer = Teal90,
    onSecondaryContainer = Teal30,
    tertiary = Amber40,
    onTertiary = Color.White,
    tertiaryContainer = Amber90,
    onTertiaryContainer = Amber30,
    error = Red40,
    onError = Color.White,
    background = Color(0xFFF7F9FC),
    onBackground = Navy10,
    surface = Color(0xFFF7F9FC),
    onSurface = Navy10,
    surfaceVariant = Color(0xFFDFE3EB),
    onSurfaceVariant = Color(0xFF43474E),
    outline = Color(0xFF73777F),
)

val DarkColors = darkColorScheme(
    primary = Navy80,
    onPrimary = Navy20,
    primaryContainer = Navy40,
    onPrimaryContainer = Navy90,
    secondary = Teal80,
    onSecondary = Teal30,
    secondaryContainer = Teal40,
    onSecondaryContainer = Teal90,
    tertiary = Amber80,
    onTertiary = Amber30,
    tertiaryContainer = Amber40,
    onTertiaryContainer = Amber90,
    error = Red80,
    onError = Color(0xFF690005),
    background = Color(0xFF0E1419),
    onBackground = Color(0xFFE2E2E6),
    surface = Color(0xFF0E1419),
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF43474E),
    onSurfaceVariant = Color(0xFFC3C6CF),
    outline = Color(0xFF8D9199),
)

/** Priority accents, ordered low → urgent. */
val PriorityColors = listOf(
    Color(0xFF7E8B99),
    Color(0xFF3B82C4),
    Color(0xFFD98324),
    Color(0xFFD1453B),
)

/** Background tints available to notes. */
val NoteColors = listOf(
    Color(0xFFFFF3C4),
    Color(0xFFD7EAFB),
    Color(0xFFD9F2E1),
    Color(0xFFFBD9D9),
    Color(0xFFE8DDF7),
    Color(0xFFEDEDED),
)
