package com.mystream.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Modern OLED Slate Design Palette (Refined, uniform, and glare-free)
val BgDark = Color(0xFF090B10)
val BgDeepDark = Color(0xFF05070A)
val SurfaceDark = Color(0xFF11151F)
val SurfaceCard = Color(0xFF161B26)
val SurfaceCardFocused = Color(0xFF1F2636)
val SurfaceElevated = Color(0xFF1E2433)

// Modern Frosted Glass Tokens
val GlassSurface = Color(0xD911151F)
val GlassSurfaceLight = Color(0x80161B26)
val GlassBorder = Color(0x1FFFFFFF)
val GlassBorderStrong = Color(0x33FFFFFF)
val GlassHighlight = Color(0x14FFFFFF)

// Uniform Modern Accent Palette (Indigo & Electric Sky)
val PrimaryNeon = Color(0xFF6366F1) // Modern Indigo
val PrimaryNeonGlow = Color(0x406366F1)
val SecondaryCyan = Color(0xFF38BDF8) // Electric Sky / Azure
val SecondaryCyanGlow = Color(0x4038BDF8)
val AccentAmber = Color(0xFFF59E0B) // Amber-500
val AccentRed = Color(0xFFEF4444)
val EmeraldNeon = Color(0xFF10B981) // Emerald-500
val EmeraldNeonGlow = Color(0x4410B981)
val ImdbGold = Color(0xFFF59E0B)

// Text Hierarchy
val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF64748B)

// Vignettes & Gradients
val OverlayGradientDark = Color(0xF2090B10)
val OverlayGradientLight = Color(0x80090B10)

val CinemaHeroGradient = Brush.verticalGradient(
    colors = listOf(
        Color.Transparent,
        Color(0x66090B10),
        Color(0xCC090B10),
        Color(0xFF090B10)
    )
)

val CardShimmerGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFF11151F),
        Color(0xFF1A2130),
        Color(0xFF11151F)
    )
)

// Unified Modern TV Focus Ring (Electric Azure/Sky - High-contrast & crisp)
val FocusRing = Color(0xFF38BDF8)
val FocusRingGlow = Color(0x6638BDF8)

// Aliased to FocusRing for complete backwards-compatibility across the app
val FocusRingOrange = FocusRing
val FocusRingOrangeGlow = FocusRingGlow

// JioHotstar Theme & Layout Design Tokens
val HotstarBg = Color(0xFF06080E)
val HotstarSidebarGlass = Color(0xD906080E)
val HotstarSidebarGlassExpanded = Color(0xF20A0E18)
val HotstarPillActive = Color(0xFF38BDF8)
val HotstarPillActiveBg = Color(0x2E38BDF8)
val HotstarPillInactiveBg = Color(0x1F1E293B)
val HotstarPillInactiveText = Color(0xFF94A3B8)
val HotstarGlassCard = Color(0x66161B26)
val HotstarGlassCardFocused = Color(0x991E2638)

val HotstarHeroSideVignette = Brush.horizontalGradient(
    0.0f to Color(0xF006080E),
    0.30f to Color(0xCC06080E),
    0.48f to Color(0x4006080E),
    0.60f to Color.Transparent,
    1.0f to Color.Transparent
)

val HotstarHeroBottomVignette = Brush.verticalGradient(
    0.0f to Color.Transparent,
    0.65f to Color.Transparent,
    0.85f to Color(0x8806080E),
    1.0f to Color(0xEB06080E)
)

