package com.verdantgem.ledger.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration

enum class WindowWidth { COMPACT, MEDIUM, EXPANDED }

data class WindowSizeClass(val width: WindowWidth)

val LocalWindowSize = compositionLocalOf { WindowSizeClass(WindowWidth.COMPACT) }

val MaterialTheme.windowSize: WindowSizeClass
    @Composable get() = LocalWindowSize.current

@Composable
fun rememberWindowSizeClass(): WindowSizeClass {
    val widthDp = LocalConfiguration.current.screenWidthDp
    val width = when {
        widthDp < 600 -> WindowWidth.COMPACT
        widthDp < 840 -> WindowWidth.MEDIUM
        else -> WindowWidth.EXPANDED
    }
    return WindowSizeClass(width)
}
