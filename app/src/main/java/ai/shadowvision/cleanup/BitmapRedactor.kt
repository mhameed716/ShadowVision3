package ai.shadowvision.cleanup

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.Color as AndroidColor
import androidx.compose.ui.geometry.Offset
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** A brush stroke is stored in normalized image coordinates, independent of preview size. */
data class BrushStroke(
    val points: List<Offset>,
    val widthFraction: Float
)

enum class RedactionMode(val title: String) {
    PIXELATE("موزاييك"),
    BLUR("تمويه"),
    SOLID("تعتيم")
}

object MaskRasterizer {
    fun createMask(width: Int, height: Int, strokes: List<BrushStroke>): Bitmap {
        require(width > 0 && height > 0)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bitmap)
        canvas.drawColor(AndroidColor.BLACK)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val minEdge = min(width, height).toFloat()

        strokes.forEach { stroke ->
            val points = stroke.points
            if (points.isEmpty()) return@forEach
            paint.strokeWidth = max(1f, stroke.widthFraction * minEdge)
            if (points.size == 1) {
                val point = points.first()
                canvas.drawCircle(
                    point.x * width,
                    point.y * height,
                    paint.strokeWidth / 2f,
                    paint
                )
            } else {
                val path = AndroidPath().apply {
                    moveTo(points.first().x * width, points.first().y * height)
                    points.drop(1).forEach { point ->
                        lineTo(point.x * width, point.y * height)
                    }
                }
                canvas.drawPath(path, paint)
            }
        }
        return bitmap
    }
}

/**
 * Applies concealment only: it pixelates, blurs, or covers selected pixels. It does not
 * synthesize, infer, or reconstruct anything behind the selection.
 */
object BitmapRedactor {
    private const val PIXEL_BLOCK_SIZE = 22
    private const val BLUR_REDUCTION = 16
    private const val SOLID_COLOR = 0xFF172335.toInt()

    fun apply(source: Bitmap, mask: Bitmap, mode: RedactionMode): Bitmap {
        require(source.width == mask.width && source.height == mask.height) {
            "Image and mask dimensions must match"
        }
        val width = source.width
        val height = source.height
        val count = width * height
        val originalPixels = IntArray(count)
        val maskPixels = IntArray(count)
        source.getPixels(originalPixels, 0, width, 0, 0, width, height)
        mask.getPixels(maskPixels, 0, width, 0, 0, width, height)

        val effectPixels = when (mode) {
            RedactionMode.SOLID -> IntArray(count) { SOLID_COLOR }
            RedactionMode.PIXELATE -> makeScaledEffect(
                source = source,
                targetWidth = max(1, ceil(width.toDouble() / PIXEL_BLOCK_SIZE).toInt()),
                targetHeight = max(1, ceil(height.toDouble() / PIXEL_BLOCK_SIZE).toInt()),
                downsampleFilter = true,
                upsampleFilter = false
            )
            RedactionMode.BLUR -> makeScaledEffect(
                source = source,
                targetWidth = max(1, width / BLUR_REDUCTION),
                targetHeight = max(1, height / BLUR_REDUCTION),
                downsampleFilter = true,
                upsampleFilter = true
            )
        }

        val outputPixels = IntArray(count)
        for (index in 0 until count) {
            val coverage = maskPixels[index] and 0xFF
            outputPixels[index] = if (coverage == 0) {
                originalPixels[index]
            } else {
                blend(originalPixels[index], effectPixels[index], coverage)
            }
        }

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(outputPixels, 0, width, 0, 0, width, height)
        }
    }

    private fun makeScaledEffect(
        source: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        downsampleFilter: Boolean,
        upsampleFilter: Boolean
    ): IntArray {
        val reduced = Bitmap.createScaledBitmap(
            source,
            targetWidth,
            targetHeight,
            downsampleFilter
        )
        val expanded = Bitmap.createScaledBitmap(
            reduced,
            source.width,
            source.height,
            upsampleFilter
        )
        return try {
            IntArray(source.width * source.height).also { pixels ->
                expanded.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
            }
        } finally {
            if (expanded !== source && expanded !== reduced) expanded.recycle()
            if (reduced !== source) reduced.recycle()
        }
    }

    private fun blend(original: Int, effect: Int, amount: Int): Int {
        val inverse = 255 - amount
        val alpha = ((original ushr 24) and 0xFF)
        val red = ((((original ushr 16) and 0xFF) * inverse) + (((effect ushr 16) and 0xFF) * amount)) / 255
        val green = ((((original ushr 8) and 0xFF) * inverse) + (((effect ushr 8) and 0xFF) * amount)) / 255
        val blue = (((original and 0xFF) * inverse) + ((effect and 0xFF) * amount)) / 255
        return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
    }
}
