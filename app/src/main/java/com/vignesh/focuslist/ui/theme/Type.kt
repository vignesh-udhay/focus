package com.vignesh.focuslist.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val FocuslistTypography = Typography(
    headlineMedium = Typography().headlineMedium.copy(
        fontWeight = FontWeight.SemiBold
    ),

    headlineSmall = Typography().headlineSmall.copy(
        fontWeight = FontWeight.SemiBold
    ),

    titleLarge = Typography().titleLarge.copy(
        fontWeight = FontWeight.SemiBold
    ),

    titleMedium = Typography().titleMedium.copy(
        fontWeight = FontWeight.Medium
    ),

    bodyLarge = Typography().bodyLarge.copy(
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),

    bodyMedium = Typography().bodyMedium.copy(
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),

    bodySmall = Typography().bodySmall.copy(
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),

    labelLarge = Typography().labelLarge.copy(
        fontWeight = FontWeight.Medium
    )
)