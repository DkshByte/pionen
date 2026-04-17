package com.pionen.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pionen.app.ui.theme.*

// ────────────────────────────────────────────────
// PIXEL CORNER DECORATIONS
// ────────────────────────────────────────────────
@Composable
fun PixelCornerDecor(
    modifier: Modifier = Modifier,
    color: Color = NeonGreen.copy(alpha = 0.25f)
) {
    val cornerSize = 20.dp
    val borderWidth = 2.dp

    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .size(cornerSize)
                .align(Alignment.TopStart)
                .padding(12.dp)
                .drawBehind {
                    drawLine(color, Offset(0f, size.height), Offset(0f, 0f), strokeWidth = borderWidth.toPx())
                    drawLine(color, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = borderWidth.toPx())
                }
        )
        Box(
            Modifier
                .size(cornerSize)
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .drawBehind {
                    drawLine(color, Offset(size.width, size.height), Offset(size.width, 0f), strokeWidth = borderWidth.toPx())
                    drawLine(color, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = borderWidth.toPx())
                }
        )
        Box(
            Modifier
                .size(cornerSize)
                .align(Alignment.BottomStart)
                .padding(12.dp)
                .drawBehind {
                    drawLine(color, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = borderWidth.toPx())
                    drawLine(color, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = borderWidth.toPx())
                }
        )
        Box(
            Modifier
                .size(cornerSize)
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .drawBehind {
                    drawLine(color, Offset(size.width, 0f), Offset(size.width, size.height), strokeWidth = borderWidth.toPx())
                    drawLine(color, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = borderWidth.toPx())
                }
        )
    }
}

// ────────────────────────────────────────────────
// PIXEL PIN PAD
// ────────────────────────────────────────────────
private val PIN_PAD_ROWS = listOf(
    listOf("1", "2", "3"),
    listOf("4", "5", "6"),
    listOf("7", "8", "9"),
    listOf("", "0", "del")
)

@Composable
fun PixelPinPad(onDigitClick: (String) -> Unit, onDeleteClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        for (row in PIN_PAD_ROWS) {
            Row(
                modifier = Modifier.padding(vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                for (key in row) {
                    when {
                        key.isEmpty() -> Spacer(Modifier.size(68.dp))
                        key == "del" -> {
                            PixelKeyButton(isTransparent = false, onClick = onDeleteClick) {
                                Icon(Icons.Default.Backspace, contentDescription = "Delete", tint = TextSecondary, modifier = Modifier.size(20.dp))
                            }
                        }
                        else -> {
                            PixelKeyButton(onClick = { onDigitClick(key) }) {
                                Text(
                                    text = key,
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PixelKeyButton(
    isTransparent: Boolean = false,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val bgColor by animateColorAsState(
        targetValue = when {
            isTransparent -> Color.Transparent
            isPressed -> DarkCardHover
            else -> DarkCard
        },
        animationSpec = tween(80),
        label = "keyBg"
    )

    Box(
        modifier = Modifier
            .size(68.dp)
            // Pixel shadow effect: offset box beneath
            .drawBehind {
                if (!isTransparent && !isPressed) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(3f, 3f),
                        size = size
                    )
                }
            }
            .background(bgColor)
            .then(
                if (!isTransparent) Modifier.border(1.dp, PixelBorderBright)
                else Modifier
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

// ────────────────────────────────────────────────
// SHARED PIXEL COMPONENTS
// ────────────────────────────────────────────────

@Composable
fun PixelButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = modifier
            .height(52.dp)
            .drawBehind {
                if (!isPressed && enabled) {
                    drawRect(
                        color = NeonGreenDark,
                        topLeft = Offset(3f, 3f),
                        size = size
                    )
                }
            }
            .background(if (enabled) NeonGreen else NeonGreen.copy(alpha = 0.3f))
            .border(1.dp, if (enabled) Color.Black.copy(alpha = 0.3f) else Color.Transparent)
            .then(
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            content = content
        )
    }
}

@Composable
fun PixelBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .border(1.dp, color.copy(alpha = 0.4f))
            .background(color.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = color
        )
    }
}
