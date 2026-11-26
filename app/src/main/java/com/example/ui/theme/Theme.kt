package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class StudioExtendedColors(
    val success: Color = SageEmerald,
    val successContainer: Color = SageEmeraldContainer,
    val warning: Color = WarmOchre,
    val warningContainer: Color = WarmOchreContainer,
    val info: Color = SteelSlate,
    val infoContainer: Color = SteelSlateContainer,
    val designAccent: Color = StudioPlum,
    val designContainer: Color = StudioPlumContainer,
    val surfaceElevated: Color = StudioSurfaceElevated,
    val surfaceHigh: Color = StudioSurfaceHigh,
    val borderSubtle: Color = StudioBorderSubtle,
    val inkMuted: Color = StudioInkMuted,
    // Realistic Architectural Office Materials
    val oakFloor: Color = OfficeOakFloor,
    val oakFloorDark: Color = OfficeOakFloorDark,
    val oakPlankLine: Color = OfficeOakPlankLine,
    val concreteHall: Color = OfficeHallConcrete,
    val wallPlaster: Color = OfficeWallPlaster,
    val wallTrim: Color = OfficeWallTrim,
    val glassTint: Color = OfficeGlassTint,
    val deskWoodTop: Color = OfficeDeskWoodTop,
    val deskWoodSide: Color = OfficeDeskWoodSide,
    val deskWoodDark: Color = OfficeDeskWoodDark,
    val chairFabric: Color = OfficeChairFabric,
    val rugControl: Color = OfficeRugControl,
    val rugDesign: Color = OfficeRugDesign,
    val rugBuild: Color = OfficeRugBuild,
    val rugAudit: Color = OfficeRugAudit,
    val rugShip: Color = OfficeRugShip,
    val plantLeaf: Color = OfficePlantLeaf,
    val plantPot: Color = OfficePlantPot,
    val monitorScreen: Color = OfficeMonitorScreen
)

val LocalStudioColors = staticCompositionLocalOf { StudioExtendedColors() }

private val AgentDeskDarkColorScheme = darkColorScheme(
    primary = SignalTerracotta,
    onPrimary = Color(0xFF14110F),
    primaryContainer = SignalTerracottaContainer,
    onPrimaryContainer = SignalTerracottaSoft,
    secondary = SteelSlate,
    onSecondary = StudioObsidian,
    secondaryContainer = SteelSlateContainer,
    onSecondaryContainer = SteelSlate,
    tertiary = WarmOchre,
    onTertiary = StudioObsidian,
    tertiaryContainer = WarmOchreContainer,
    onTertiaryContainer = WarmOchre,
    background = StudioObsidian,
    onBackground = StudioInkPrimary,
    surface = StudioSurface,
    onSurface = StudioInkPrimary,
    surfaceVariant = StudioSurfaceElevated,
    onSurfaceVariant = StudioInkSecondary,
    outline = StudioBorder,
    outlineVariant = StudioBorderSubtle,
    error = AlertVermilion,
    onError = Color.White,
    errorContainer = AlertVermilionContainer,
    onErrorContainer = AlertVermilion
)

private val AgentDeskShapes = Shapes(
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

@Composable
fun AgentDeskTheme(
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalStudioColors provides StudioExtendedColors()) {
        MaterialTheme(
            colorScheme = AgentDeskDarkColorScheme,
            typography = AgentDeskTypography,
            shapes = AgentDeskShapes,
            content = content
        )
    }
}
