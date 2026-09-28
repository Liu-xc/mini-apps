package com.leo.darkroom.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Sans = FontFamily.SansSerif
private val Serif = FontFamily.Serif

/**
 * A restrained CJK editorial scale: serif is reserved for the entrance title and printed work title;
 * all functional Chinese text uses the system sans family for clear small-size rendering.
 */
val EditorialTypography = Typography(
    displayLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 40.sp),
    displayMedium = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 34.sp),
    displaySmall = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
    headlineMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 30.sp),
    headlineSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 30.sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 21.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 23.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 17.sp),
    // Chinese labels stay compact; add tracking only at short Latin wordmarks in the call site.
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp),
)

/** Card 20dp, sheet top 28dp, chip 8dp (DESIGN.md §2.4). */
val DarkroomShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
