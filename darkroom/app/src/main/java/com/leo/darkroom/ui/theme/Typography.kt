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
 * 排版层级（DESIGN.md §2.3）：Display/Title 衬线（系统 Noto Serif CJK），
 * 正文/小标签无衬线；labelSmall = 印刷小标签母题（小号 + 宽字距）。
 */
val EditorialTypography = Typography(
    displayLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 38.sp),
    displayMedium = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp),
    displaySmall = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),
    headlineMedium = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    headlineSmall = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    // 「DEVELOPING · 02」式小标签：小号 + 宽字距
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 2.sp),
)

/** 卡片 20dp、弹层顶 28dp、chip 8dp（DESIGN.md §2.4） */
val DarkroomShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
