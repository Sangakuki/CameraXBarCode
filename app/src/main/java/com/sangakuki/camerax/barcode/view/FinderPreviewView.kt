package com.sangakuki.camerax.barcode.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.graphics.toRectF
import com.sangakuki.camerax.barcode.R
import com.sangakuki.camerax.barcode.extensions.cropRect

/**
 * 取景框View
 */
class FinderPreviewView : View {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var frame = Rect()

    @ColorInt
    private var boxColor: Int = Color.BLUE

    @ColorInt
    private var maskColor: Int = Color.parseColor("#60000000")

    constructor(context: Context?) : super(context) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        init()
    }

    private fun init() {
        this.maskColor = Color.parseColor("#60000000")
        this.boxColor = ContextCompat.getColor(context, R.color.colorAccent)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        if (!changed) return
        frame.set(left, top, right, bottom)
        frame = frame.cropRect()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawFrame(canvas, frame.toRectF())
        drawFrameBox(canvas, frame.toRectF())
    }

    /**
     * 绘制取景框外部阴影
     */
    private fun drawFrame(canvas: Canvas, frame: RectF) {
        this.paint.color = maskColor
        canvas.drawRect(0.0f, 0.0f, width.toFloat(), frame.top, paint)
        canvas.drawRect(0.0f, frame.top, frame.left, frame.bottom, paint)
        canvas.drawRect(frame.right, frame.top, width.toFloat(), frame.bottom, paint)
        canvas.drawRect(0.0f, frame.bottom, width.toFloat(), height.toFloat(), paint)
    }

    /**
     * 绘制取景框四角指示符
     */
    private fun drawFrameBox(canvas: Canvas, frame: RectF) {
        this.paint.color = boxColor
        canvas.drawRect(frame.left, frame.top, (frame.left + 50), (frame.top + 10), this.paint)
        canvas.drawRect(frame.left, frame.top, (frame.left + 10), (frame.top + 50), this.paint)
        canvas.drawRect((frame.right - 50), frame.top, frame.right, (frame.top + 10), this.paint)
        canvas.drawRect((frame.right - 10), frame.top, frame.right, (frame.top + 50), this.paint)
        canvas.drawRect(frame.left, (frame.bottom - 10), (frame.left + 50), frame.bottom, this.paint)
        canvas.drawRect(frame.left, (frame.bottom - 50), (frame.left + 10), frame.bottom, this.paint)
        canvas.drawRect((frame.right - 50), (frame.bottom - 10), frame.right, frame.bottom, this.paint)
        canvas.drawRect((frame.right - 10), (frame.bottom - 50), frame.right, frame.bottom, this.paint)
    }
}