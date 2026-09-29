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

    private var chars: List<Char> = emptyList()
    private var countText: String = ""
    private var fallbackLabel: String = ""

    private val primaryTextColor = ContextCompat.getColor(context, R.color.text_primary)
    private val secondaryTextColor = ContextCompat.getColor(context, R.color.text_secondary)

    private val letterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = primaryTextColor
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val singleLetterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = primaryTextColor
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = secondaryTextColor
        typeface = Typeface.DEFAULT
        textAlign = Paint.Align.CENTER
    }

    init {
        isClickable = false
        isFocusable = false
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

        val cx = w / 2f
        val cy = h / 2f
        val radius = min(w, h) / 2f

        when {
            chars.size == 1 -> {
                singleLetterPaint.textSize = spToPx(34f)
                val charStr = chars[0].toString()
                val charBaseline = cy - (singleLetterPaint.descent() + singleLetterPaint.ascent()) / 2f
                canvas.drawText(charStr, cx, charBaseline, singleLetterPaint)
            }

            chars.size > 1 -> {
                val n = chars.size
                val (letterSp, rFactor) = when (n) {
                    2 -> Pair(26f, 0.35f)
                    3 -> Pair(22f, 0.38f)
                    4 -> Pair(22f, 0.40f)
                    else -> Pair(18f, 0.44f)
                }
                letterPaint.textSize = spToPx(letterSp)
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

            else -> {
                if (fallbackLabel.isNotEmpty()) {
                    singleLetterPaint.textSize = spToPx(24f)
                    val labelBaseline = cy - (singleLetterPaint.descent() + singleLetterPaint.ascent()) / 2f
                    canvas.drawText(fallbackLabel, cx, labelBaseline, singleLetterPaint)
                }
            }
        }
    }
}
