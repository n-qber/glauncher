package br.com.nqber.glauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class CircularRangeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class LetterLayout {
        CIRCULAR,
        LINE
    }

    private var chars: List<Char> = emptyList()
    private var countText: String = ""
    private var fallbackLabel: String = ""
    private var letterLayout: LetterLayout = LetterLayout.CIRCULAR

    private val primaryTextColor get() = ContextCompat.getColor(context, R.color.text_primary)
    private val secondaryTextColor get() = ContextCompat.getColor(context, R.color.text_secondary)

    private val letterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val singleLetterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT
        textAlign = Paint.Align.CENTER
    }

    init {
        isClickable = false
        isFocusable = false
    }

    fun setLetterLayout(layout: LetterLayout) {
        if (this.letterLayout != layout) {
            this.letterLayout = layout
            invalidate()
        }
    }

    fun setRange(characters: List<Char>, count: String, fallback: String = "") {
        this.chars = characters
        this.countText = count
        this.fallbackLabel = fallback
        invalidate()
    }

    private fun spToPx(sp: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics)

    private fun dpToPx(dp: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val color = primaryTextColor
        letterPaint.color = color
        singleLetterPaint.color = color
        countPaint.color = secondaryTextColor

        val cx = w / 2f
        val cy = h / 2f
        val radius = min(w, h) / 2f

        val baseRadius = dpToPx(75f)
        val scaleFactor = (radius / baseRadius).coerceAtLeast(0.1f)

        when {
            chars.size == 1 -> {
                singleLetterPaint.textSize = spToPx(34f) * scaleFactor
                val charStr = chars[0].toString()
                val charBaseline = cy - (singleLetterPaint.descent() + singleLetterPaint.ascent()) / 2f
                canvas.drawText(charStr, cx, charBaseline, singleLetterPaint)
            }

            chars.size > 1 -> {
                if (letterLayout == LetterLayout.LINE) {
                    val lineText = chars.joinToString("")
                    val maxAllowedWidth = radius * 1.55f
                    val baseSp = when (chars.size) {
                        2 -> 32f
                        3 -> 28f
                        4 -> 23f
                        5 -> 19f
                        else -> 16f
                    }
                    letterPaint.textSize = spToPx(baseSp) * scaleFactor
                    val measured = letterPaint.measureText(lineText)
                    if (measured > maxAllowedWidth && maxAllowedWidth > 0f) {
                        letterPaint.textSize = (spToPx(baseSp) * scaleFactor) * (maxAllowedWidth / measured)
                    }
                    val baseline = cy - (letterPaint.descent() + letterPaint.ascent()) / 2f
                    canvas.drawText(lineText, cx, baseline, letterPaint)
                } else {
                    val n = chars.size
                    val (letterSp, rFactor) = when (n) {
                        2 -> Pair(26f, 0.35f)
                        3 -> Pair(22f, 0.38f)
                        4 -> Pair(22f, 0.40f)
                        else -> Pair(18f, 0.44f)
                    }
                    letterPaint.textSize = spToPx(letterSp) * scaleFactor
                    val rLetters = radius * rFactor

                    val anglesDeg = when (n) {
                        2 -> listOf(180.0, 0.0) // Left, Right
                        3 -> listOf(-90.0, 150.0, 30.0) // Top, Bottom-Left, Bottom-Right
                        4 -> listOf(-90.0, 180.0, 0.0, 90.0) // Top, Left, Right, Bottom (Diamond / Rotated Square)
                        5 -> listOf(-90.0, 200.0, -20.0, 140.0, 40.0) // Top, Top-Left, Top-Right, Bottom-Left, Bottom-Right
                        else -> (0 until n).map { -90.0 + it * (360.0 / n) }
                    }

                    for (i in 0 until n) {
                        val angleRad = Math.toRadians(anglesDeg[i])
                        val lx = cx + (rLetters * cos(angleRad)).toFloat()
                        val ly = cy + (rLetters * sin(angleRad)).toFloat()
                        val baseline = ly - (letterPaint.descent() + letterPaint.ascent()) / 2f
                        canvas.drawText(chars[i].toString(), lx, baseline, letterPaint)
                    }
                }
            }

            else -> {
                if (fallbackLabel.isNotEmpty()) {
                    singleLetterPaint.textSize = spToPx(24f) * scaleFactor
                    val labelBaseline = cy - (singleLetterPaint.descent() + singleLetterPaint.ascent()) / 2f
                    canvas.drawText(fallbackLabel, cx, labelBaseline, singleLetterPaint)
                }
            }
        }
    }
}
