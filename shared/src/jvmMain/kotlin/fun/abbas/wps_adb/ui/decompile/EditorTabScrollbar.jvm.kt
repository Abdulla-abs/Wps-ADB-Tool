package `fun`.abbas.wps_adb.ui.decompile

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import `fun`.abbas.wps_adb.theme.CarbonColors

private val editorTabScrollbarStyle = ScrollbarStyle(
    minimalHeight = 32.dp,
    thickness = 6.dp,
    shape = RoundedCornerShape(3.dp),
    hoverDurationMillis = 150,
    unhoverColor = CarbonColors.Outline.copy(alpha = 0.75f),
    hoverColor = CarbonColors.Primary.copy(alpha = 0.9f),
)

@Composable
internal actual fun EditorTabScrollbar(
    scrollState: ScrollState,
    modifier: Modifier,
) {
    Box(
        modifier = modifier.background(CarbonColors.SurfaceContainerLow),
    ) {
        HorizontalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 1.dp),
            style = editorTabScrollbarStyle,
        )
    }
}
