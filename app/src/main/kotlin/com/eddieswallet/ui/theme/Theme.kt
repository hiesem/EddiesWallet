package com.eddieswallet.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = ParentCoral,
    onPrimary = SurfaceCream,
    primaryContainer = SpendTint,
    onPrimaryContainer = Ink,
    secondary = SaveTeal,
    onSecondary = SurfaceCream,
    secondaryContainer = SaveTint,
    onSecondaryContainer = Ink,
    tertiary = OwedPurple,
    onTertiary = SurfaceCream,
    tertiaryContainer = OwedTint,
    onTertiaryContainer = Ink,
    background = WarmCream,
    onBackground = Ink,
    surface = SurfaceCream,
    onSurface = Ink,
    surfaceVariant = ChildCream,
    onSurfaceVariant = SecondaryInk,
    outline = Line,
)

// The canvas specifies Nunito for display and IBM Plex Sans for body/UI. Both
// variable fonts are packaged locally, so the design never depends on a CDN.
val NunitoDisplay: FontFamily = FontFamily(
    Font(com.eddieswallet.R.font.nunito_variable, FontWeight.Normal),
    Font(com.eddieswallet.R.font.nunito_variable, FontWeight.SemiBold),
    Font(com.eddieswallet.R.font.nunito_variable, FontWeight.Bold),
    Font(com.eddieswallet.R.font.nunito_variable, FontWeight.ExtraBold),
)
val PlexBody: FontFamily = FontFamily(
    Font(com.eddieswallet.R.font.ibm_plex_sans_variable, FontWeight.Normal),
    Font(com.eddieswallet.R.font.ibm_plex_sans_variable, FontWeight.Medium),
    Font(com.eddieswallet.R.font.ibm_plex_sans_variable, FontWeight.SemiBold),
    Font(com.eddieswallet.R.font.ibm_plex_sans_variable, FontWeight.Bold),
)

val EddieTypography = Typography(
    displayLarge = androidx.compose.ui.text.TextStyle(
        fontFamily = NunitoDisplay,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 46.sp,
        lineHeight = 50.sp,
        letterSpacing = (-1).sp,
    ),
    displayMedium = androidx.compose.ui.text.TextStyle(
        fontFamily = NunitoDisplay,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 34.sp,
        lineHeight = 38.sp,
    ),
    headlineLarge = androidx.compose.ui.text.TextStyle(
        fontFamily = NunitoDisplay,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 30.sp,
        lineHeight = 35.sp,
    ),
    headlineMedium = androidx.compose.ui.text.TextStyle(
        fontFamily = NunitoDisplay,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 24.sp,
        lineHeight = 29.sp,
    ),
    titleLarge = androidx.compose.ui.text.TextStyle(
        fontFamily = PlexBody,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleMedium = androidx.compose.ui.text.TextStyle(
        fontFamily = PlexBody,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = androidx.compose.ui.text.TextStyle(
        fontFamily = PlexBody,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = androidx.compose.ui.text.TextStyle(
        fontFamily = PlexBody,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = androidx.compose.ui.text.TextStyle(
        fontFamily = PlexBody,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
    labelLarge = androidx.compose.ui.text.TextStyle(
        fontFamily = PlexBody,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = androidx.compose.ui.text.TextStyle(
        fontFamily = PlexBody,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

object EddieShapes {
    val Small = RoundedCornerShape(14.dp)
    val Card = RoundedCornerShape(20.dp)
    val LargeCard = RoundedCornerShape(22.dp)
    val Friendly = RoundedCornerShape(26.dp)
    val Hero = RoundedCornerShape(30.dp)
    val Pill = RoundedCornerShape(50)
}

object EddieSpacing {
    val Xs = 4.dp
    val Sm = 8.dp
    val Md = 12.dp
    val Lg = 16.dp
    val Xl = 20.dp
    val Xxl = 26.dp
    val Section = 30.dp
}

object EddieElevation {
    val Card = 1.dp
    val Friendly = 4.dp
    val Hero = 6.dp
}

@Composable
fun EddiesWalletTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = EddieTypography,
        shapes = androidx.compose.material3.Shapes(
            small = EddieShapes.Small,
            medium = EddieShapes.Card,
            large = EddieShapes.LargeCard,
        ),
        content = content,
    )
}
