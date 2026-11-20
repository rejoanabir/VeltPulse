package com.veltusstudio.veltpulse.core.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object VeltColors {
    // Pure & Obsidian bases
    val PureBlack = Color(0xFF000000)
    val Obsidian = Color(0xFF07040D)
    val DeepBg = Color(0xFF0A0614)
    val SurfaceDark = Color(0xFF130E22)
    val SurfaceElevated = Color(0xFF1C1430)
    val SurfaceHighlight = Color(0xFF261C40)
    val SurfaceGlass = Color(0xFF161028)
    val SurfaceGlassBorder = Color(0xFF2D1F4C)

    // VeltPulse Neon Violet & Velvet Theme (Mockup Reference 16474.png & 16475.png)
    val NeonViolet = Color(0xFFA855F7)
    val ElectricPurple = Color(0xFF7C3AED)
    val VioletPrimary = Color(0xFF9333EA)
    val DeepPurplePill = Color(0xFF2A154D)
    val DeepPurplePillBorder = Color(0xFF6B21A8)
    val MagentaGlow = Color(0xFFD946EF)
    val LavenderMuted = Color(0xFF9E92B6)

    // Gradients
    val PurpleGradient = Brush.horizontalGradient(
        listOf(Color(0xFF9333EA), Color(0xFF7C3AED))
    )
    val HeroGradient = Brush.verticalGradient(
        listOf(Color(0xFF281146), Color(0xFF120A24), Color(0xFF080410))
    )
    val CardGradient = Brush.verticalGradient(
        listOf(Color(0xFF1E1436), Color(0xFF120B22))
    )
    val GlowWaveGradient = Brush.horizontalGradient(
        listOf(Color(0xFFC026D3), Color(0xFF7C3AED), Color(0xFF4F46E5))
    )

    // Legacy alias compatibility so older references continue to compile cleanly
    val CrimsonPillActive = NeonViolet
    val CrimsonCardBg = Color(0xFF2A133D)
    val CrimsonCardDark = Color(0xFF190C26)
    val CrimsonCardSubtle = Color(0xFF201030)

    val SageGreenActive = ElectricPurple
    val SageGreenPill = DeepPurplePill
    val SageCardBg = Color(0xFF1A1230)
    val GreenActiveDot = Color(0xFF22C55E)
    val RedBadge = Color(0xFFEF4444)

    // Progress bar tokens (Electric violet & Magenta glow)
    val AmberProgress = NeonViolet
    val AmberThumbGlow = Color(0x80A855F7)

    // Text tokens
    val SilverPrimary = Color(0xFFFFFFFF)
    val SilverSecondary = Color(0xFFC4B5FD)
    val SilverMuted = Color(0xFF8B80A6)

    val PulseAccent = NeonViolet
    val PulseCyanHint = Color(0xFF38BDF8)
    val GlassBorder = Color(0x33A855F7)
    val GlassFill = Color(0x1A1E1436)
    val Hairline = Color(0xFF24193B)
}
