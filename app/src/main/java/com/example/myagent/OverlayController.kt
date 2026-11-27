package com.example.myagent

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams as WLP
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.abs

private class BubbleRoot(ctx: Context) : FrameLayout(ctx) {
    var onBack: (() -> Unit)? = null
    var onOutside: (() -> Unit)? = null

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (e.keyCode == KeyEvent.KEYCODE_BACK && e.action == KeyEvent.ACTION_UP) {
            onBack?.invoke()
            return true
        }
        return super.dispatchKeyEvent(e)
    }

    override fun dispatchTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_OUTSIDE) {
            onOutside?.invoke()
            return true
        }
        return super.dispatchTouchEvent(e)
    }
}

private class MaxHeightScroll(ctx: Context, private val maxH: Int) : ScrollView(ctx) {
    override fun onMeasure(w: Int, h: Int) {
        super.onMeasure(w, MeasureSpec.makeMeasureSpec(maxH, MeasureSpec.AT_MOST))
    }
}

class OverlayController(
    private val ctx: Context,
    private val onCloseRequest: () -> Unit
) {
    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    private val main = Handler(Looper.getMainLooper())
    private val dn = ctx.resources.displayMetrics.density
    private val screenW = ctx.resources.displayMetrics.widthPixels
    private val screenH = ctx.resources.displayMetrics.heightPixels
    private val orbSize = (58 * dn).toInt()
    private val cyan = Color.parseColor("#22D3EE")

    private var orb: CyberOrb? = null
    private var orbLp: WLP? = null
    private var root: BubbleRoot? = null
    private var bubbleAdded = false
    private var thinkPill: View? = null
    private var closePill: View? = null
    private var closeHide: Runnable? = null
    private var longRun: Runnable? = null

    private var mode = 0 // 0 = বন্ধ, 1 = শুধু উত্তর, 2 = লেখার ঘরসহ
    private var thinking = false
    private var alive = false
    private var longFired = false
    private var lastOutsideClose = 0L

    private var dX = 0f
    private var dY = 0f
    private var sX = 0
    private var sY = 0
    private var moved = false

    private lateinit var answerScroll: MaxHeightScroll
    private lateinit var answerText: TextView
    private lateinit var input: EditText
    private lateinit var inputRow: LinearLayout

    private fun dp(v: Int): Int = (v * dn).toInt()

    fun start() {
        alive = true
        buildBubble()

        val o = CyberOrb(ctx)
        val lp = WLP(
            orbSize, orbSize,
            WLP.TYPE_APPLICATION_OVERLAY,
            WLP.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = screenW - orbSize - dp(6)
        lp.y = (screenH * 0.35f).toInt()

        o.setOnTouchListener { v, e ->
            val p = orbLp
            if (p == null) {
                false
            } else {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        hideClosePill()
                        dX = e.rawX
                        dY = e.rawY
                        sX = p.x
                        sY = p.y
                        moved = false
                        longFired = false
                        scheduleLong(v)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = e.rawX - dX
                        val dy = e.rawY - dY
                        if (abs(dx) > dp(8) || abs(dy) > dp(8)) {
                            moved = true
                            cancelLong()
                        }
                        if (moved && !longFired) {
                            p.x = (sX + dx).toInt().coerceIn(0, screenW - orbSize)
                            p.y = (sY + dy).toInt().coerceIn(0, screenH - orbSize)
                            try { wm.updateViewLayout(o, p) } catch (_: Throwable) {}
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        cancelLong()
                        if (longFired) {
                            // কিছু না, বন্ধ বাটন দেখানো হয়েছে
                        } else if (moved) {
                            snapToEdge()
                        } else {
                            onOrbTap()
                        }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        cancelLong()
                        true
                    }
                    else -> false
                }
            }
        }

        wm.addView(o, lp)
        orb = o
        orbLp = lp
    }

    fun stop() {
        alive = false
        cancelLong()
        main.removeCallbacksAndMessages(null)
        removeBubbleWindow()
        hideThinkPill()
        hideClosePill()
        val o = orb
        if (o != null) {
            try { wm.removeView(o) } catch (_: Throwable) {}
        }
        orb = null
        orbLp = null
    }

    // অ্যাপ থেকে আসা কাজ
    fun runTask(text: String) {
        main.post { if (alive) runText(text, true) }
    }

    private fun scheduleLong(v: View) {
        cancelLong()
        val r = Runnable {
            if (!moved && alive) {
                longFired = true
                try { v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) } catch (_: Throwable) {}
                showClosePill()
            }
        }
        longRun = r
        main.postDelayed(r, 2000)
    }

    private fun cancelLong() {
        val r = longRun
        if (r != null) main.removeCallbacks(r)
        longRun = null
    }

    private fun snapToEdge() {
        val lp = orbLp ?: return
        val o = orb ?: return
        val target = if (lp.x + orbSize / 2 < screenW / 2) 0 else screenW - orbSize
        ValueAnimator.ofInt(lp.x, target).apply {
            duration = 180
            addUpdateListener {
                lp.x = it.animatedValue as Int
                try { wm.updateViewLayout(o, lp) } catch (_: Throwable) {}
            }
            start()
        }
    }

    private fun onOrbTap() {
        if (thinking) return
        if (System.currentTimeMillis() - lastOutsideClose < 400) return
        when (mode) {
            0, 1 -> showBubble(true)
            2 -> closeBubble()
        }
    }

    private fun buildBubble() {
        val r = BubbleRoot(ctx)
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(14))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F2101826"))
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), Color.parseColor("#6622D3EE"))
            }
            setOnClickListener { if (mode == 1) showBubble(true) }
        }

        val head = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(TextView(ctx).apply {
            text = "◉  MY AGENT"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(cyan)
            letterSpacing = 0.1f
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(TextView(ctx).apply {
            text = "✕"
            textSize = 16f
            setTextColor(Color.parseColor("#9CA3AF"))
            setPadding(dp(10), dp(2), dp(2), dp(2))
            setOnClickListener { closeBubble() }
        })
        card.addView(head)

        answerText = TextView(ctx).apply {
            textSize = 15f
            setTextColor(Color.WHITE)
            setLineSpacing(0f, 1.15f)
            setOnClickListener { if (mode == 1) showBubble(true) }
        }
        answerScroll = MaxHeightScroll(ctx, (screenH * 0.38f).toInt()).apply { addView(answerText) }
        card.addView(
            answerScroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        )

        input = EditText(ctx).apply {
            hint = "লিখুন…"
            setHintTextColor(Color.parseColor("#6B7280"))
            setTextColor(Color.WHITE)
            textSize = 15f
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEND
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1F2937"))
                cornerRadius = dp(14).toFloat()
            }
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setOnEditorActionListener { _, actionId, ev ->
                if (actionId == EditorInfo.IME_ACTION_SEND ||
                    actionId == EditorInfo.IME_ACTION_DONE ||
                    (ev != null && ev.keyCode == KeyEvent.KEYCODE_ENTER && ev.action == KeyEvent.ACTION_DOWN)
                ) {
                    submit()
                    true
                } else {
                    false
                }
            }
        }
        val send = TextView(ctx).apply {
            text = "➤"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(cyan)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#1F2937"))
            }
            setOnClickListener { submit() }
        }
        inputRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        inputRow.addView(input, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        inputRow.addView(send, LinearLayout.LayoutParams(dp(42), dp(42)).apply { leftMargin = dp(8) })
        card.addView(
            inputRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        )

        r.addView(
            card,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )
        r.onBack = { closeBubble() }
        r.onOutside = {
            if (mode == 2) {
                lastOutsideClose = System.currentTimeMillis()
                closeBubble()
            }
        }
        root = r
    }

    private fun statusBarHeight(): Int {
        val id = ctx.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) ctx.resources.getDimensionPixelSize(id) else dp(24)
    }

    private fun bubbleParams(inputMode: Boolean): WLP {
        var flags = WLP.FLAG_NOT_TOUCH_MODAL or WLP.FLAG_WATCH_OUTSIDE_TOUCH
        if (!inputMode) flags = flags or WLP.FLAG_NOT_FOCUSABLE
        val lp = WLP(
            (screenW * 0.92f).toInt(),
            WLP.WRAP_CONTENT,
            WLP.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        lp.y = statusBarHeight() + dp(16)
        if (inputMode) {
            lp.softInputMode = WLP.SOFT_INPUT_STATE_ALWAYS_VISIBLE or WLP.SOFT_INPUT_ADJUST_PAN
        }
        return lp
    }

    private fun showBubble(inputMode: Boolean) {
        val r = root ?: return
        if (!alive) return
        removeBubbleWindow()
        val last = ChatStore.lastAi()
        answerText.text = last
        answerScroll.visibility = if (last.isEmpty()) View.GONE else View.VISIBLE
        inputRow.visibility = if (inputMode) View.VISIBLE else View.GONE
        try {
            wm.addView(r, bubbleParams(inputMode))
            bubbleAdded = true
        } catch (_: Throwable) {
            return
        }
        mode = if (inputMode) 2 else 1
        if (inputMode) {
            input.requestFocus()
            main.postDelayed({
                if (mode == 2 && alive) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }, 200)
        }
    }

    private fun removeBubbleWindow() {
        if (!bubbleAdded) return
        try { imm.hideSoftInputFromWindow(input.windowToken, 0) } catch (_: Throwable) {}
        val r = root
        if (r != null) {
            try { wm.removeView(r) } catch (_: Throwable) {}
        }
        bubbleAdded = false
    }

    private fun closeBubble() {
        removeBubbleWindow()
        mode = 0
    }

    private fun submit() {
        val t = input.text.toString().trim()
        if (t.isEmpty()) return
        input.setText("")
        removeBubbleWindow()
        mode = 0
        runText(t, false)
    }

    private fun runText(t: String, isTask: Boolean) {
        removeBubbleWindow()
        mode = 0
        thinking = true
        orb?.thinking = true
        showThinkPill()
        Agent.handle(ctx, t, isTask) { reply ->
            if (alive) {
                thinking = false
                orb?.thinking = false
                hideThinkPill()
                if (reply.show) showBubble(false)
            }
        }
    }

    // রোবটের পাশে লাফানো "..."
    private fun showThinkPill() {
        hideThinkPill()
        val lp0 = orbLp ?: return
        val w = dp(76)
        val h = dp(40)
        val pill = FrameLayout(ctx).apply {
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F2101826"))
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), Color.parseColor("#6622D3EE"))
            }
            addView(
                DotsView(ctx),
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }
        val lp = WLP(
            w, h,
            WLP.TYPE_APPLICATION_OVERLAY,
            WLP.FLAG_NOT_FOCUSABLE or WLP.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        val onRight = lp0.x > screenW / 2
        lp.x = if (onRight) lp0.x - w - dp(8) else lp0.x + orbSize + dp(8)
        lp.y = lp0.y + (orbSize - h) / 2
        try {
            wm.addView(pill, lp)
            thinkPill = pill
        } catch (_: Throwable) {
        }
    }

    private fun hideThinkPill() {
        val p = thinkPill ?: return
        try { wm.removeView(p) } catch (_: Throwable) {}
        thinkPill = null
    }

    // ২ সেকেন্ড চেপে ধরলে "বন্ধ করুন" বাটন
    private fun showClosePill() {
        hideClosePill()
        val lp0 = orbLp ?: return
        val tv = TextView(ctx).apply {
            text = "✕  বন্ধ করুন"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#DC2626"))
                cornerRadius = dp(22).toFloat()
            }
            setPadding(dp(18), dp(12), dp(18), dp(12))
            setOnClickListener {
                hideClosePill()
                onCloseRequest()
            }
        }
        val lp = WLP(
            WLP.WRAP_CONTENT, WLP.WRAP_CONTENT,
            WLP.TYPE_APPLICATION_OVERLAY,
            WLP.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        val w = dp(140)
        val onRight = lp0.x > screenW / 2
        lp.x = (if (onRight) lp0.x - w - dp(8) else lp0.x + orbSize + dp(8)).coerceAtLeast(0)
        lp.y = lp0.y + (orbSize - dp(44)) / 2
        try {
            wm.addView(tv, lp)
            closePill = tv
        } catch (_: Throwable) {
            return
        }
        val h = Runnable { hideClosePill() }
        closeHide = h
        main.postDelayed(h, 4000)
    }

    private fun hideClosePill() {
        val h = closeHide
        if (h != null) main.removeCallbacks(h)
        closeHide = null
        val p = closePill ?: return
        try { wm.removeView(p) } catch (_: Throwable) {}
        closePill = null
    }
}
