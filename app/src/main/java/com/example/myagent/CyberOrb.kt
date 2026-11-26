package com.example.myagent

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.sin

// ছবি ছাড়া কোডে আঁকা গোল সাইবার রোবট আইকন
class CyberOrb(ctx: Context) : View(ctx) {
    var thinking = false

    private val dn = resources.displayMetrics.density
    private var rot = 0f
    private var pulse = 0f
    private var tick = 0f

    private val cyan = Color.parseColor("#22D3EE")
    private val blue = Color.parseColor("#3B82F6")
    private val core = Color.parseColor("#0B1220")
    private val cyanClear = Color.argb(0, 34, 211, 238)
    private val cyanGlow = Color.argb(120, 34, 211, 238)

    private val pGlow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pCore = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = core }
    private val pRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = cyan
        strokeWidth = 1.6f * dn
    }
    private val pArc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val pEye = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pTick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = cyan
        strokeWidth = 1.4f * dn
        strokeCap = Paint.Cap.ROUND
    }

    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            tick += if (thinking) 0.06f else 0.02f
            rot = (rot + if (thinking) 7f else 1.6f) % 360f
            pulse = ((sin(tick * 6.0) + 1.0) / 2.0).toFloat()
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
        val cx = w / 2f
        val cy = h / 2f
        val r = minOf(w, h) / 2f

        // আভা
        pGlow.shader = RadialGradient(
            cx, cy, r,
            intArrayOf(cyanGlow, cyanClear),
            floatArrayOf(0.6f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, r, pGlow)

        // কোর ও বাইরের রিং
        val rc = r * 0.72f
        c.drawCircle(cx, cy, rc, pCore)
        c.drawCircle(cx, cy, rc, pRing)

        // ঘুরন্ত আর্ক
        pArc.strokeWidth = 3f * dn
        pArc.color = cyan
        val big = RectF(cx - rc * 0.86f, cy - rc * 0.86f, cx + rc * 0.86f, cy + rc * 0.86f)
        for (i in 0 until 3) c.drawArc(big, rot + i * 120f, 70f, false, pArc)

        pArc.strokeWidth = 2f * dn
        pArc.color = blue
        val small = RectF(cx - rc * 0.62f, cy - rc * 0.62f, cx + rc * 0.62f, cy + rc * 0.62f)
        for (i in 0 until 2) c.drawArc(small, -rot * 1.6f + i * 180f, 90f, false, pArc)

        // চারপাশে ছোট দাগ
        val t1 = rc * 0.92f
        val t2 = rc * 1.0f
        for (i in 0 until 4) {
            val a = Math.toRadians((i * 90.0) + 45.0)
            val ca = Math.cos(a).toFloat()
            val sa = Math.sin(a).toFloat()
            c.drawLine(cx + ca * t1, cy + sa * t1, cx + ca * t2, cy + sa * t2, pTick)
        }

        // স্পন্দিত চোখ
        val boost = if (thinking) 2f else 1f
        val er = rc * (0.24f + 0.06f * pulse * boost)
        pEye.shader = RadialGradient(
            cx, cy, er * 1.7f,
            intArrayOf(Color.WHITE, cyan, cyanClear),
            floatArrayOf(0.15f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, er * 1.7f, pEye)
    }
}
