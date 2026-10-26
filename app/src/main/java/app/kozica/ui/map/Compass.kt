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
 * beside it, a needle, and an N above the needle's north tip. MapLibre turns the whole image with
 * the map's bearing, so north is drawn at the top.
 *
 * MapLibre's compass view is a fixed 48dp and fit-centres its image, so the disc is drawn inside a
 * 48dp image with a transparent rim of [RIM]; back the compass margins off by that much and the
 * disc lines up with the 40dp buttons.
 */
class CompassDrawable(
    colors: ColorScheme,
    density: Density,
) : Drawable() {
    private val size = with(density) { VIEW_SIZE.roundToPx() }
    private val disc = with(density) { DISC_SIZE.toPx() / 2 }
    private val letterOffset = with(density) { 13.dp.toPx() }
    private val needleCentre = with(density) { 4.dp.toPx() }
    private val needleHalfLength = with(density) { 10.dp.toPx() }
    private val needleHalfWidth = with(density) { 4.5.dp.toPx() }
    private val pivot = with(density) { 1.5.dp.toPx() }

    private val discPaint = fill(colors.secondaryContainer.toArgb())
    private val northPaint = fill(colors.primary.toArgb())
    private val southPaint = fill(colors.outline.toArgb())
    private val letterPaint =
        fill(colors.onSecondaryContainer.toArgb()).apply {
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

        val letterCentre = cy - letterOffset
        canvas.drawText(LETTER, cx, letterCentre - letterBounds.exactCenterY(), letterPaint)

        val hub = cy + needleCentre
        path.reset()
        path.moveTo(cx, hub - needleHalfLength)
        path.lineTo(cx + needleHalfWidth, hub)
        path.lineTo(cx - needleHalfWidth, hub)
        path.close()
        canvas.drawPath(path, northPaint)
        path.reset()
        path.moveTo(cx, hub + needleHalfLength)
        path.lineTo(cx + needleHalfWidth, hub)
        path.lineTo(cx - needleHalfWidth, hub)
        path.close()
        canvas.drawPath(path, southPaint)
        canvas.drawCircle(cx, hub, pivot, discPaint)
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
