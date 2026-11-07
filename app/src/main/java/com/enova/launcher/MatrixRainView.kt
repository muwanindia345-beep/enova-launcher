package com.enova.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.random.Random

class MatrixRainView @JvmOverloads constructor(c: Context, a: AttributeSet? = null) : View(c, a) {
    private val glyphs = "アイウエオカキクケコサシスセソ0123456789ENOVA".map { it.toString() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        color = Color.parseColor("#00FF41")
    }
    private val cell = 14f * resources.displayMetrics.density
    private val trail = 16
    private var cols = 0
    private var heads = FloatArray(0)
    private var speeds = FloatArray(0)
    private var last = 0L

    init { paint.textSize = cell }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        cols = (w / cell).toInt().coerceAtLeast(1)
        heads = FloatArray(cols) { -Random.nextFloat() * 30f }
        speeds = FloatArray(cols) { 8f + Random.nextFloat() * 10f }
    }

    override fun onDraw(canvas: Canvas) {
        val now = System.nanoTime()
        val dt = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceAtMost(0.05f)
        last = now
        val tick = (now / 150_000_000L).toInt()
        val rows = (height / cell).toInt() + 1
        for (i in 0 until cols) {
            val head = heads[i].toInt()
            for (t in 0 until trail) {
                val row = head - t
                if (row < 0 || row > rows) continue
                paint.alpha = (255 * 0.4f * (1f - t / trail.toFloat())).toInt()
                val idx = ((i * 31 + row * 17 + (if (t == 0) tick else tick / 4)) and 0x7fffffff) % glyphs.size
                canvas.drawText(glyphs[idx], i * cell, (row + 1) * cell, paint)
            }
            heads[i] += speeds[i] * dt
            if (heads[i] > rows + trail && Random.nextFloat() > 0.97f) heads[i] = -Random.nextFloat() * 10f
        }
        postInvalidateOnAnimation()
    }
}
