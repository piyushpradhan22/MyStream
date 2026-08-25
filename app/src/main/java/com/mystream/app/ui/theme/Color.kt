package com.mystream.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Cinematic Void Dark Theme Palette (OLED & High Contrast Glass)
val BgDark = Color(0xFF07090E)
val BgDeepDark = Color(0xFF040508)
val SurfaceDark = Color(0xFF0D111A)
val SurfaceCard = Color(0xFF141A28)
val SurfaceCardFocused = Color(0xFF222B3F)

// Modern Frosted Glass Tokens
val GlassSurface = Color(0xCC0D121F)
val GlassSurfaceLight = Color(0x80171E2E)
val GlassBorder = Color(0x24FFFFFF)
val GlassBorderStrong = Color(0x40FFFFFF)
val GlassHighlight = Color(0x1FFFFFFF)

// Vibrant Neon & Accent Palette
val PrimaryNeon = Color(0xFF7C5CFC)
val PrimaryNeonGlow = Color(0x667C5CFC)
val SecondaryCyan = Color(0xFF00D2D3)
val SecondaryCyanGlow = Color(0x4000D2D3)
val AccentAmber = Color(0xFFFF9F43)
val AccentRed = Color(0xFFFF4757)
val EmeraldNeon = Color(0xFF00E599)
val EmeraldNeonGlow = Color(0x4400E599)
val ImdbGold = Color(0xFFFFC000)

// Text Hierarchy
val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF64748B)

// Vignettes & Gradients
val OverlayGradientDark = Color(0xF207090E)
val OverlayGradientLight = Color(0x8007090E)

val CinemaHeroGradient = Brush.verticalGradient(
    colors = listOf(
        Color.Transparent,
        Color(0x6607090E),
        Color(0xCC07090E),
        Color(0xFF07090E)
    )
)

val CardShimmerGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFF111724),
        Color(0xFF1C2438),
        Color(0xFF111724)
    )
)

// Modern Cinema Orange Uniform Focus Highlighting Ring
val FocusRingOrange = Color(0xFFFF7A00)
val FocusRingOrangeGlow = Color(0x80FF7A00)
