package com.omix.alleq.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.omix.alleq.ui.theme.LocalEqColors

// handle for modal sheets; when the sheet slides under the status bar it grows by the overlap,
// so the handle and the title never end up behind the clock and camera cutout.
// onStatusBarCover reports 0..1 so the sheet can square its corners when it is full screen
@Composable
fun SheetDragHandle(onStatusBarCover: (Float) -> Unit = {}) {
    val colors = LocalEqColors.current
    val density = LocalDensity.current
    val statusBarPx = WindowInsets.statusBars.getTop(density)
    var overlapPx by remember { mutableIntStateOf(0) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.onGloballyPositioned { coords ->
            overlapPx = (statusBarPx - coords.positionInWindow().y.toInt()).coerceIn(0, statusBarPx)
            if (statusBarPx > 0) onStatusBarCover(overlapPx / statusBarPx.toFloat())
        },
    ) {
        Spacer(modifier = Modifier.height(with(density) { overlapPx.toDp() }))
        Box(
            modifier = Modifier
                .padding(vertical = 12.dp)
                .width(36.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(if (colors.isDark) Color(0xFF333333) else Color(0xFFD4D8DE)),
        )
    }
}
