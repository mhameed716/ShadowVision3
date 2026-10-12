package ai.shadowvision.cleanup

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun EditorCanvas(
    source: Bitmap,
    preview: Bitmap?,
    strokes: List<BrushStroke>,
    brushSizeDp: Float,
    editable: Boolean,
    onStrokeCommitted: (BrushStroke) -> Unit,
    modifier: Modifier = Modifier
) {
    val image = (preview ?: source).asImageBitmap()
    val density = LocalDensity.current
    val brushWidthPx = with(density) { brushSizeDp.dp.toPx() }
    var activePoints by remember(source) { mutableStateOf(emptyList<Offset>()) }

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF101827))
            .pointerInput(source, editable, brushSizeDp, onStrokeCommitted) {
                if (!editable) return@pointerInput
                detectDragGestures(
                    onDragStart = { position ->
                        val rect = fitImageRect(
                            size.width.toFloat(),
                            size.height.toFloat(),
                            source.width.toFloat(),
                            source.height.toFloat()
                        )
                        activePoints = if (rect.contains(position)) {
                            listOf(toNormalized(position, rect))
                        } else {
                            emptyList()
                        }
                    },
                    onDrag = { change, _ ->
                        val rect = fitImageRect(
                            size.width.toFloat(),
                            size.height.toFloat(),
                            source.width.toFloat(),
                            source.height.toFloat()
                        )
                        if (activePoints.isNotEmpty() && rect.contains(change.position)) {
                            activePoints = activePoints + toNormalized(change.position, rect)
                            change.consume()
                        }
                    },
                    onDragEnd = {
                        if (activePoints.isNotEmpty()) {
                            val rect = fitImageRect(
                                size.width.toFloat(),
                                size.height.toFloat(),
                                source.width.toFloat(),
                                source.height.toFloat()
                            )
                            val widthFraction = brushWidthPx / min(rect.width, rect.height)
                            onStrokeCommitted(
                                BrushStroke(
                                    points = activePoints,
                                    widthFraction = widthFraction.coerceIn(0.003f, 0.25f)
                                )
                            )
                        }
                        activePoints = emptyList()
                    },
                    onDragCancel = { activePoints = emptyList() }
                )
            }
    ) {
        drawRect(Color(0xFF101827))
        val rect = fitImageRect(
            size.width,
            size.height,
            source.width.toFloat(),
            source.height.toFloat()
        )
        drawImage(
            image = image,
            dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
            dstSize = IntSize(rect.width.roundToInt(), rect.height.roundToInt())
        )

        if (preview == null) {
            strokes.forEach { stroke ->
                drawMaskStroke(stroke.points, stroke.widthFraction, rect, Color(0x99FF6F68))
            }
            if (activePoints.isNotEmpty()) {
                val activeWidth = brushWidthPx / min(rect.width, rect.height)
                drawMaskStroke(activePoints, activeWidth, rect, Color(0xC8FF6F68))
            }
        }
    }
}

private fun fitImageRect(
    canvasWidth: Float,
    canvasHeight: Float,
    imageWidth: Float,
    imageHeight: Float
): Rect {
    val scale = min(canvasWidth / imageWidth, canvasHeight / imageHeight)
    val fittedWidth = imageWidth * scale
    val fittedHeight = imageHeight * scale
    val left = (canvasWidth - fittedWidth) / 2f
    val top = (canvasHeight - fittedHeight) / 2f
    return Rect(left, top, left + fittedWidth, top + fittedHeight)
}

private fun toNormalized(position: Offset, rect: Rect): Offset = Offset(
    x = ((position.x - rect.left) / rect.width).coerceIn(0f, 1f),
    y = ((position.y - rect.top) / rect.height).coerceIn(0f, 1f)
)

private fun DrawScope.drawMaskStroke(
    points: List<Offset>,
    widthFraction: Float,
    rect: Rect,
    color: Color
) {
    if (points.isEmpty()) return
    val widthPx = widthFraction * min(rect.width, rect.height)
    val mapped = points.map { point ->
        Offset(rect.left + point.x * rect.width, rect.top + point.y * rect.height)
    }
    if (mapped.size == 1) {
        drawCircle(color = color, radius = widthPx / 2f, center = mapped.first())
        return
    }
    val path = Path().apply {
        moveTo(mapped.first().x, mapped.first().y)
        mapped.drop(1).forEach { point -> lineTo(point.x, point.y) }
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = widthPx, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}
