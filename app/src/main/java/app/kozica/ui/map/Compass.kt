package app.kozica.ui.map

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * MapLibre's compass image in the app's colours: a bezel compass on a tonal disc like the Safety and
 * Settings buttons beside it, a graduated ring with a gap for the N, cardinal ticks and a diamond
 * needle. MapLibre turns the whole image with the map's bearing, so north is drawn at the top.
 *
 * The disc sits in a 48dp image, a transparent rim of [RIM] around it, because MapLibre sizes the
 * compass to its image and the reset-to-north tap needs the same 48dp target as the buttons.
 */
class CompassDrawable(
    colors: ColorScheme,
    private val density: Density,
) : Drawable() {
    private val sizeDp = SIZE.value
    private val discRadiusDp = DISC_RADIUS.value
    private val ringRadiusDp = 17f
    private val ringGapHalfAngle = 24f
    private val northLetterMarginDp = 1f
    private val northLetterSizeDp = 11f
    private val tickOuterDp = 15f
    private val minorTickInnerDp = 12.5f
    private val cardinalTickInnerDp = 11f
    private val needleTipDp = 9.5f
    private val needleHalfWidthDp = 3f
    private val hubRadiusDp = 1.5f

    private fun fill(color: Int) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = color
        }

    private fun stroke(
        color: Int,
        widthDp: Float,
    ) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
        strokeWidth = widthDp.px()
        this.color = color
    }

    private val disc = fill(colors.secondaryContainer.toArgb())
    private val ring = stroke(colors.onSecondaryContainer.toArgb(), 1.5f)
    private val minorTick = stroke(colors.outline.toArgb(), 1f)
    private val cardinalTick = stroke(colors.onSecondaryContainer.toArgb(), 2f)
    private val needleNorth = fill(colors.primary.toArgb())
    private val needleSouth = fill(colors.onSecondaryContainer.toArgb())
    private val hub = fill(colors.secondaryContainer.toArgb())
    private val northLetter =
        fill(colors.onSecondaryContainer.toArgb()).apply {
            typeface = Typeface.DEFAULT_BOLD
            textSize = northLetterSizeDp.px()
            textAlign = Paint.Align.CENTER
        }
    private val paints = listOf(disc, ring, minorTick, cardinalTick, needleNorth, needleSouth, hub, northLetter)

    private val letterBounds = Rect().also { northLetter.getTextBounds("N", 0, 1, it) }
    private val ringRect = RectF(-ringRadiusDp.px(), -ringRadiusDp.px(), ringRadiusDp.px(), ringRadiusDp.px())
    private val northHalf = diamond(-needleTipDp.px(), 0f)
    private val southHalf = diamond(needleTipDp.px(), 0f)

    private fun Float.px() = this * density.density

    private fun diamond(
        tipY: Float,
        baseY: Float,
    ) = Path().apply {
        moveTo(0f, tipY)
        lineTo(needleHalfWidthDp.px(), baseY)
        lineTo(0f, baseY)
        lineTo(-needleHalfWidthDp.px(), baseY)
        close()
    }

    override fun getIntrinsicWidth() = sizeDp.px().toInt()

    override fun getIntrinsicHeight() = sizeDp.px().toInt()

    override fun draw(canvas: Canvas) {
        val b = bounds
        val scale = min(b.width(), b.height()) / sizeDp.px()
        canvas.save()
        canvas.translate(b.exactCenterX(), b.exactCenterY())
        canvas.scale(scale, scale)

        canvas.drawCircle(0f, 0f, discRadiusDp.px(), disc)
        canvas.drawArc(ringRect, 270f + ringGapHalfAngle, 360f - 2 * ringGapHalfAngle, false, ring)
        for (step in 1 until 12) {
            val angle = Math.toRadians(step * 30.0 - 90.0)
            val cardinal = step % 3 == 0
            val inner = (if (cardinal) cardinalTickInnerDp else minorTickInnerDp).px()
            val outer = tickOuterDp.px()
            val dx = cos(angle).toFloat()
            val dy = sin(angle).toFloat()
            canvas.drawLine(dx * inner, dy * inner, dx * outer, dy * outer, if (cardinal) cardinalTick else minorTick)
        }
        canvas.drawPath(northHalf, needleNorth)
        canvas.drawPath(southHalf, needleSouth)
        canvas.drawCircle(0f, 0f, hubRadiusDp.px(), hub)

        val baseline = -(discRadiusDp - northLetterMarginDp).px() - letterBounds.top
        canvas.drawText("N", 0f, baseline, northLetter)
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) {
        paints.forEach { it.alpha = alpha }
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paints.forEach { it.colorFilter = colorFilter }
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    companion object {
        private val SIZE = 48.dp
        private val DISC_RADIUS = 20.dp
        val RIM = SIZE / 2 - DISC_RADIUS
    }
}
