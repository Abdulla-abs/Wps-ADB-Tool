package `fun`.abbas.wps_adb.ui.decompile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `fun`.abbas.wps_adb.model.FileNode
import `fun`.abbas.wps_adb.theme.CarbonColors

@Composable
fun ProjectExplorer(
    rootFolder: FileNode.Folder?,
    onFileClick: (FileNode) -> Unit,
    showRootFolder: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(CarbonColors.SurfaceContainerLowest)
            .verticalScroll(rememberScrollState())
    ) {
        if (rootFolder != null) {
            if (showRootFolder) {
                FileNodeItem(
                    node = rootFolder,
                    indentLevel = 0,
                    onNodeClick = onFileClick
                )
            } else {
                rootFolder.children.forEach { child ->
                    FileNodeItem(
                        node = child,
                        indentLevel = 0,
                        onNodeClick = onFileClick
                    )
                }
            }
        } else {
            Text(
                text = "Empty workspace",
                color = CarbonColors.Outline,
                fontSize = 12.sp,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Composable
private fun FileNodeItem(
    node: FileNode,
    indentLevel: Int,
    onNodeClick: (FileNode) -> Unit
) {
    val paddingLeft = (indentLevel * 12).dp

    when (node) {
        is FileNode.Folder -> {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fileTreeItemClickable { onNodeClick(node) }
                        .padding(start = paddingLeft, top = 6.dp, bottom = 6.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (node.isExpanded) "▼" else "▶",
                        color = CarbonColors.Outline,
                        fontSize = 9.sp,
                        modifier = Modifier.width(12.dp)
                    )
                    Text(
                        text = "📁",
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = node.name,
                        color = CarbonColors.OnSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (node.isExpanded) {
                    node.children.forEach { child ->
                        FileNodeItem(
                            node = child,
                            indentLevel = indentLevel + 1,
                            onNodeClick = onNodeClick
                        )
                    }
                }
            }
        }
        is FileNode.File -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fileTreeItemClickable { onNodeClick(node) }
                    .padding(start = paddingLeft + 12.dp, top = 4.dp, bottom = 4.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val icon = when (node.extension.lowercase()) {
                    "xml" -> "📝"
                    "dex" -> "📦"
                    "smali" -> "📄"
                    "java" -> "☕"
                    "json" -> "📋"
                    "properties", "prop" -> "⚙️"
                    "md", "markdown" -> "📖"
                    "txt", "log" -> "📃"
                    "yml", "yaml" -> "📑"
                    "html", "htm" -> "🌐"
                    "css" -> "🎨"
                    else -> "📄"
                }
                val iconColor = when (node.extension.lowercase()) {
                    "xml" -> CarbonColors.Primary
                    "dex" -> CarbonColors.Tertiary
                    "smali" -> CarbonColors.Secondary
                    "java" -> CarbonColors.Tertiary
                    "json" -> CarbonColors.Secondary
                    else -> CarbonColors.OnSurfaceVariant
                }

                Text(
                    text = icon,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = node.name,
                    color = iconColor,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun Modifier.fileTreeItemClickable(onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    return this
        .background(if (isHovered) CarbonColors.SurfaceContainerHigh else Color.Transparent)
        .hoverable(interactionSource = interactionSource)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        )
}
