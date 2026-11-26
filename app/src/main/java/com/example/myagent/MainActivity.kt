package com.example.myagent

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var btnPerm: TextView
    private lateinit var btnToggle: TextView
    private val handler = Handler(Looper.getMainLooper())

    private val bg = Color.parseColor("#0B1220")
    private val card = Color.parseColor("#111827")
    private val cyan = Color.parseColor("#22D3EE")
    private val gray = Color.parseColor("#9CA3AF")

    private val notifLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun button(text: String, filled: Boolean, onClick: () -> Unit): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(if (filled) Color.parseColor("#0B1220") else cyan)
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                if (filled) {
                    setColor(cyan)
                } else {
                    setColor(Color.TRANSPARENT)
                    setStroke(dp(2), cyan)
                }
            }
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setOnClickListener { onClick() }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(48), dp(24), dp(24))
        }
        col.addView(TextView(this).apply {
            text = "◉  My Agent"
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(cyan)
        })
        col.addView(TextView(this).apply {
            text = "আপনার ব্যক্তিগত এজেন্ট"
            textSize = 14f
            setTextColor(gray)
            setPadding(0, dp(4), 0, dp(24))
        })

        status = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(card)
                cornerRadius = dp(14).toFloat()
            }
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        col.addView(status)

        btnPerm = button("অনুমতি দিন (অন্য অ্যাপের ওপরে দেখানো)", false) { openOverlaySettings() }
        col.addView(
            btnPerm,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(16) }
        )

        btnToggle = button("রোবট চালু করুন", true) { toggle() }
        col.addView(
            btnToggle,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        )

        col.addView(TextView(this).apply {
            text = "চালু করার পর হোম স্ক্রিনে যান, রোবট আইকন ভাসবে। চাপলে চ্যাট বক্স খুলবে, টেনে সরানো যায়।\n\n" +
                "vivo ফোনে: iManager → ব্যাটারি → My Agent-কে ব্যাকগ্রাউন্ডে চালু থাকতে দিন, না হলে রোবট নিজে বন্ধ হয়ে যেতে পারে।"
            textSize = 13f
            setTextColor(gray)
            setPadding(0, dp(24), 0, 0)
        })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(bg)
            addView(col)
        })

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun openOverlaySettings() {
        startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        )
    }

    private fun toggle() {
        if (!Settings.canDrawOverlays(this)) {
            openOverlaySettings()
            return
        }
        if (AgentService.running) AgentService.stop(this) else AgentService.start(this)
        handler.postDelayed({ refresh() }, 500)
    }

    private fun refresh() {
        val can = Settings.canDrawOverlays(this)
        val run = AgentService.running
        status.text = when {
            !can -> "⚠️ অনুমতি বাকি: \"অন্য অ্যাপের ওপরে দেখানো\" চালু করতে হবে।"
            run -> "✅ রোবট চালু আছে। হোম স্ক্রিনে আইকনটি দেখুন।"
            else -> "রোবট বন্ধ আছে। চালু করুন।"
        }
        btnPerm.visibility = if (can) android.view.View.GONE else android.view.View.VISIBLE
        btnToggle.text = if (run) "রোবট বন্ধ করুন" else "রোবট চালু করুন"
    }
}
