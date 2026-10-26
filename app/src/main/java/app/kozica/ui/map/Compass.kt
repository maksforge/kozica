package app.kozica.ui.map

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/**
 * MapLibre's compass image in the app's colours: a tonal disc like the Safety and Settings buttons
 * beside it, and a needle spanning it with an N inside its north half. MapLibre turns the whole
 * image with the map's bearing, so north is drawn at the top.
 *
 * The disc sits in a 48dp image, a transparent rim of [RIM] around it, because MapLibre sizes the
 * compass to its image and the reset-to-north tap needs the same 48dp target as the buttons.
 */
class CompassDrawable(
    colors: ColorScheme,
    density: Density,
) : Drawable() {
    private val size = with(density) { VIEW_SIZE.roundToPx() }
    private val disc = with(density) { DISC_SIZE.toPx() / 2 }
    private val needleHalfLength = with(density) { 17.5.dp.toPx() }
    private val needleHalfWidth = with(density) { 7.dp.toPx() }
    private val shoulder = with(density) { 5.dp.toPx() }
    private val letterCentre = with(density) { 7.5.dp.toPx() }

    private val discPaint = fill(colors.secondaryContainer.toArgb())
    private val northPaint = fill(colors.primary.toArgb())
    private val southPaint = fill(colors.outline.toArgb())
    private val letterPaint =
        fill(colors.onPrimary.toArgb()).apply {
            textSize = with(density) { 10.dp.toPx() }
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    private val letterBounds = Rect().also { letterPaint.getTextBounds(LETTER, 0, LETTER.length, it) }
    private val path = Path()

    override fun getIntrinsicWidth(): Int = size

    override fun getIntrinsicHeight(): Int = size

    override fun draw(canvas: Canvas) {
        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()
        canvas.drawCircle(cx, cy, disc, discPaint)

        canvas.half(cx, cy, -1f, northPaint)
        canvas.half(cx, cy, 1f, southPaint)
        canvas.drawText(LETTER, cx, cy - letterCentre - letterBounds.exactCenterY(), letterPaint)
    }

    private fun Canvas.half(
        cx: Float,
        cy: Float,
        towards: Float,
        paint: Paint,
    ) {
        path.reset()
        path.moveTo(cx, cy + towards * needleHalfLength)
        path.lineTo(cx + needleHalfWidth, cy + towards * shoulder)
        path.lineTo(cx, cy)
        path.lineTo(cx - needleHalfWidth, cy + towards * shoulder)
        path.close()
        drawPath(path, paint)
    }

    override fun setAlpha(alpha: Int) {
        listOf(discPaint, northPaint, southPaint, letterPaint).forEach { it.alpha = alpha }
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        listOf(discPaint, northPaint, southPaint, letterPaint).forEach { it.colorFilter = colorFilter }
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    companion object {
        private const val LETTER = "N"
        private val VIEW_SIZE = 48.dp
        private val DISC_SIZE = 40.dp
        val RIM = (VIEW_SIZE - DISC_SIZE) / 2
    }

    private fun fill(color: Int) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = color
        }
}
