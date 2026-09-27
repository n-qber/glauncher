package br.com.nqber.glauncher

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

class SquareFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val size = if (width > 0 && height > 0) {
            minOf(width, height)
        } else {
            maxOf(width, height)
        }
        val finalSpec = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)
        super.onMeasure(finalSpec, finalSpec)
    }
}
