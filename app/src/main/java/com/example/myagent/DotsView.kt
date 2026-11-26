package com.example.myagent

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.sin

// তিনটি লাফানো ফোঁটা "..."
class DotsView(ctx: Context) : View(ctx) {
    private val dn = resources.displayMetrics.density
    private var t = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#22D3EE") }

    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 900
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            t = it.animatedFraction
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        anim.start()
    }

    override fun onDetachedFromWindow() {
        anim.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val rad = 4f * dn
        val gap = 6f * dn
        val total = 3 * 2 * rad + 2 * gap
        val startX = (w - total) / 2f + rad
        for (i in 0 until 3) {
            val phase = (t - i * 0.16f + 1f) % 1f
            val bounce = sin(Math.PI * phase).toFloat().coerceAtLeast(0f)
            val y = h / 2f + 4f * dn - bounce * 9f * dn
            c.drawCircle(startX + i * (2 * rad + gap), y, rad, paint)
        }
    }
}
