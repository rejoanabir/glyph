package com.veltusstudio.glyph.agent.ui.theme

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

data class GlyphColors(
    val bgObsidian: Color,
    val surfaceGlass: Color,
    val surfaceCard: Color,
    val surfaceElevated: Color,
    val surfaceTerminal: Color,
    val borderSubtle: Color,
    val borderActive: Color,
    val accentPrimary: Color,
    val accentSecondary: Color,
    val accentGlow: Color,
    val accentOnPrimary: Color,
    val statusEmerald: Color = Color(0xFF10B981),
    val statusAmber: Color = Color(0xFFF59E0B),
    val statusCrimson: Color = Color(0xFFF43F5E),
    val textPrimary: Color = Color(0xFFFAFAFA),
    val textSecondary: Color = Color(0xFFA1A1AA),
    val textMuted: Color = Color(0xFF5A5A6E)
)

enum class PaletteId(val label: String, val vibe: String, val dotColor: Color) {
    NOIR_VIOLET("Noir Violet", "Linear / Raycast", Color(0xFF8B5CF6)),
    CARBON_LIME("Carbon Lime", "Cyber Terminal / TE", Color(0xFFA3E635)),
    GRAPHITE_AMBER("Graphite Amber", "Industrial Warm Dark", Color(0xFFF97316)),
    OLED_STEALTH("OLED Stealth", "Pitch Black & Platinum", Color(0xFFE4E4E7)),
    SPEC_CYAN("Spec Cyan", "Original Obsidian & Cyan", Color(0xFF38BDF8))
}

val NOIR_VIOLET_COLORS = GlyphColors(
    bgObsidian = Color(0xFF050507),
    surfaceGlass = Color(0xFF09090D),
    surfaceCard = Color(0xFF0E0E15),
    surfaceElevated = Color(0xFF171724),
    surfaceTerminal = Color(0xFF030305),
    borderSubtle = Color(0xFF1C1C2B),
    borderActive = Color(0xFF2E2E48),
    accentPrimary = Color(0xFF8B5CF6),
    accentSecondary = Color(0xFF7C3AED),
    accentGlow = Color(0x288B5CF6),
    accentOnPrimary = Color(0xFFFFFFFF),
    statusEmerald = Color(0xFF10B981),
    statusAmber = Color(0xFFF59E0B),
    statusCrimson = Color(0xFFF43F5E),
    textPrimary = Color(0xFFFAFAFA),
    textSecondary = Color(0xFFA1A1AA),
    textMuted = Color(0xFF5A5A6E)
)

val CARBON_LIME_COLORS = GlyphColors(
    bgObsidian = Color(0xFF050605),
    surfaceGlass = Color(0xFF0A0C0A),
    surfaceCard = Color(0xFF0F130F),
    surfaceElevated = Color(0xFF171E17),
    surfaceTerminal = Color(0xFF030403),
    borderSubtle = Color(0xFF1B231B),
    borderActive = Color(0xFF2E3D2E),
    accentPrimary = Color(0xFFA3E635),
    accentSecondary = Color(0xFF84CC16),
    accentGlow = Color(0x24A3E635),
    accentOnPrimary = Color(0xFF050605),
    statusEmerald = Color(0xFF22C55E),
    statusAmber = Color(0xFFEAB308),
    statusCrimson = Color(0xFFEF4444),
    textPrimary = Color(0xFFF4F4F5),
    textSecondary = Color(0xFF9CA3AF),
    textMuted = Color(0xFF5C665C)
)

val GRAPHITE_AMBER_COLORS = GlyphColors(
    bgObsidian = Color(0xFF090807),
    surfaceGlass = Color(0xFF100E0C),
    surfaceCard = Color(0xFF161311),
    surfaceElevated = Color(0xFF221D19),
    surfaceTerminal = Color(0xFF050504),
    borderSubtle = Color(0xFF26201B),
    borderActive = Color(0xFF3D332A),
    accentPrimary = Color(0xFFF97316),
    accentSecondary = Color(0xFFEA580C),
    accentGlow = Color(0x26F97316),
    accentOnPrimary = Color(0xFF090807),
    statusEmerald = Color(0xFF10B981),
    statusAmber = Color(0xFFFBBF24),
    statusCrimson = Color(0xFFEF4444),
    textPrimary = Color(0xFFFAF7F5),
    textSecondary = Color(0xFFA8A29E),
    textMuted = Color(0xFF78716C)
)

val OLED_STEALTH_COLORS = GlyphColors(
    bgObsidian = Color(0xFF000000),
    surfaceGlass = Color(0xFF080808),
    surfaceCard = Color(0xFF0F0F0F),
    surfaceElevated = Color(0xFF191919),
    surfaceTerminal = Color(0xFF020202),
    borderSubtle = Color(0xFF1F1F1F),
    borderActive = Color(0xFF383838),
    accentPrimary = Color(0xFFE4E4E7),
    accentSecondary = Color(0xFFD4D4D8),
    accentGlow = Color(0x1FE4E4E7),
    accentOnPrimary = Color(0xFF000000),
    statusEmerald = Color(0xFF22C55E),
    statusAmber = Color(0xFFF59E0B),
    statusCrimson = Color(0xFFEF4444),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFA1A1AA),
    textMuted = Color(0xFF63636B)
)

val SPEC_CYAN_COLORS = GlyphColors(
    bgObsidian = Color(0xFF060709),
    surfaceGlass = Color(0xFF090B10),
    surfaceCard = Color(0xFF0C0E14),
    surfaceElevated = Color(0xFF131A26),
    surfaceTerminal = Color(0xFF050608),
    borderSubtle = Color(0xFF161A24),
    borderActive = Color(0xFF1E2536),
    accentPrimary = Color(0xFF38BDF8),
    accentSecondary = Color(0xFF0284C7),
    accentGlow = Color(0x2438BDF8),
    accentOnPrimary = Color(0xFF060709),
    statusEmerald = Color(0xFF10B981),
    statusAmber = Color(0xFFF59E0B),
    statusCrimson = Color(0xFFEF4444),
    textPrimary = Color(0xFFF8FAFC),
    textSecondary = Color(0xFF94A3B8),
    textMuted = Color(0xFF64748B)
)

fun getPaletteColors(paletteId: PaletteId): GlyphColors {
    return when (paletteId) {
        PaletteId.NOIR_VIOLET -> NOIR_VIOLET_COLORS
        PaletteId.CARBON_LIME -> CARBON_LIME_COLORS
        PaletteId.GRAPHITE_AMBER -> GRAPHITE_AMBER_COLORS
        PaletteId.OLED_STEALTH -> OLED_STEALTH_COLORS
        PaletteId.SPEC_CYAN -> SPEC_CYAN_COLORS
    }
}

val LocalGlyphColors = staticCompositionLocalOf { NOIR_VIOLET_COLORS }
val LocalActivePalette = compositionLocalOf { mutableStateOf(PaletteId.NOIR_VIOLET) }

@Composable
fun GlyphTheme(
    paletteId: PaletteId = PaletteId.NOIR_VIOLET,
    content: @Composable () -> Unit
) {
    val colors = remember(paletteId) { getPaletteColors(paletteId) }
    CompositionLocalProvider(
        LocalGlyphColors provides colors,
        content = content
    )
}
