package com.enova.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin
import kotlin.random.Random

class RobotView @JvmOverloads constructor(c: Context, a: AttributeSet? = null) : View(c, a) {
    var talking = false
    private val green = Color.parseColor("#00FF41")
    private val amber = Color.parseColor("#FFB454")
    private val dark = Color.parseColor("#04120A")
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 6f; color = green
    }
    private var lookX = 0f
    private var lookY = 0f
    private var tx = 0f
    private var ty = 0f
    private var nextLook = 0L
    private var nextBlink = 0L
    private var blinkUntil = 0L

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = System.currentTimeMillis()
        if (now > nextLook) { tx = Random.nextFloat() * 14 - 7; ty = Random.nextFloat() * 6 - 3; nextLook = now + 1800 }
        if (now > nextBlink) { blinkUntil = now + 140; nextBlink = now + 3000 + Random.nextLong(2500) }
        lookX += (tx - lookX) * 0.1f
        lookY += (ty - lookY) * 0.1f
        val s = minOf(width / 200f, height / 205f)
        canvas.save()
        canvas.translate((width - 200 * s) / 2f, (height - 205 * s) / 2f + 12 * s + (sin(now / 550.0) * 4 * s).toFloat())
        canvas.scale(s, s)
        fill.color = green
        canvas.drawRect(98f, 12f, 102f, 34f, fill)
        fill.color = amber
        fill.alpha = (140 + 115 * sin(now / 250.0)).toInt()
        canvas.drawCircle(100f, 10f, 7f, fill)
        fill.color = dark
        val head = RectF(22f, 34f, 178f, 162f)
        canvas.drawRoundRect(head, 30f, 30f, fill)
        canvas.drawRoundRect(head, 30f, 30f, stroke)
        fill.color = green
        canvas.drawRoundRect(RectF(8f, 78f, 22f, 118f), 6f, 6f, fill)
        canvas.drawRoundRect(RectF(178f, 78f, 192f, 118f), 6f, 6f, fill)
        val ry = if (now < blinkUntil) 2f else 19f
        canvas.drawOval(RectF(55f + lookX, 95f + lookY - ry, 85f + lookX, 95f + lookY + ry), fill)
        canvas.drawOval(RectF(115f + lookX, 95f + lookY - ry, 145f + lookX, 95f + lookY + ry), fill)
        val mh = if (talking) 4f + 8f * (0.5f + 0.5f * sin(now / 60.0).toFloat()) else 4f
        canvas.drawRoundRect(RectF(76f, 138f - mh, 124f, 138f + mh), 4f, 4f, fill)
        canvas.restore()
        postInvalidateOnAnimation()
    }
}
