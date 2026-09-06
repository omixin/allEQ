package com.omix.alleq.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.omix.alleq.R

val BenzinFontFamily = FontFamily(
    Font(R.font.benzin_regular, FontWeight.Normal),
    Font(R.font.benzin_medium, FontWeight.Medium),
    Font(R.font.benzin_semibold, FontWeight.SemiBold),
    Font(R.font.benzin_bold, FontWeight.Bold),
    Font(R.font.benzin_extrabold, FontWeight.ExtraBold)
)

val WixFontFamily = FontFamily(
    Font(R.font.wix_madefor_display_regular, FontWeight.Normal),
    Font(R.font.wix_madefor_display_medium, FontWeight.Medium),
    Font(R.font.wix_madefor_display_semibold, FontWeight.SemiBold),
    Font(R.font.wix_madefor_display_bold, FontWeight.Bold),
    Font(R.font.wix_madefor_display_extrabold, FontWeight.ExtraBold)
)

val Typography = Typography(
    headlineLarge = TextStyle(
        fontFamily = BenzinFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        letterSpacing = 0.5.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = WixFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        letterSpacing = 0.2.sp
    ),
    titleLarge = TextStyle(
        fontFamily = WixFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        letterSpacing = 0.2.sp
    ),
    titleMedium = TextStyle(
        fontFamily = WixFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = 0.1.sp
    ),
    titleSmall = TextStyle(
        fontFamily = WixFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = WixFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = WixFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    bodySmall = TextStyle(
        fontFamily = WixFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp
    ),
    labelLarge = TextStyle(
        fontFamily = WixFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp
    ),
    labelMedium = TextStyle(
        fontFamily = WixFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp
    ),
    labelSmall = TextStyle(
        fontFamily = WixFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 9.sp
    )
)