package com.example.myagent

import android.Manifest
import android.content.Context
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
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val bg = Color.parseColor("#0B1220")
    private val card = Color.parseColor("#111827")
    private val cyan = Color.parseColor("#22D3EE")
    private val gray = Color.parseColor("#9CA3AF")
    private val userBubble = Color.parseColor("#155E75")
    private val handler = Handler(Looper.getMainLooper())
    private val timeFmt = SimpleDateFormat("hh:mm a", Locale.US)

    private lateinit var list: ListView
    private lateinit var input: EditText
    private lateinit var modeChip: TextView
    private lateinit var toggleBtn: TextView
    private lateinit var subtitle: TextView
    private lateinit var emptyText: TextView
    private val adapter = ChatAdapter()
    private var taskMode = false

    private val storeListener: () -> Unit = { runOnUiThread { refreshChat() } }

    private val notifLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun toast(t: String) {
        Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
    }

    private inner class ChatAdapter : BaseAdapter() {
        var msgs: List<Msg> = emptyList()
        var typing = false

        override fun getCount(): Int = msgs.size + (if (typing) 1 else 0)
        override fun getItem(position: Int): Any = position
        override fun getItemId(position: Int): Long = position.toLong()

        private fun bubbleBg(mine: Boolean): GradientDrawable {
            val r = dp(18).toFloat()
            val s = dp(4).toFloat()
            return GradientDrawable().apply {
                if (mine) {
                    setColor(userBubble)
                    cornerRadii = floatArrayOf(r, r, r, r, s, s, r, r)
                } else {
                    setColor(card)
                    cornerRadii = floatArrayOf(r, r, r, r, r, r, s, s)
                    setStroke(dp(1), Color.parseColor("#1F2937"))
                }
            }
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(5), dp(12), dp(5))
                layoutParams = AbsListView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            if (position >= msgs.size) {
                row.gravity = Gravity.START
                val box = FrameLayout(this@MainActivity).apply { background = bubbleBg(false) }
                box.addView(DotsView(this@MainActivity), FrameLayout.LayoutParams(dp(64), dp(32)))
                row.addView(box, LinearLayout.LayoutParams(dp(64), dp(32)))
                return row
            }
            val m = msgs[position]
            val mine = m.role == "u"
            row.gravity = if (mine) Gravity.END else Gravity.START
            val tv = TextView(this@MainActivity).apply {
                text = m.text
                textSize = 15f
                setTextColor(Color.WHITE)
                setLineSpacing(0f, 1.12f)
                maxWidth = (resources.displayMetrics.widthPixels * 0.8f).toInt()
                background = bubbleBg(mine)
                setPadding(dp(14), dp(10), dp(14), dp(10))
                setTextIsSelectable(true)
            }
            row.addView(tv)
            row.addView(TextView(this@MainActivity).apply {
                text = timeFmt.format(Date(m.ts))
                textSize = 10f
                setTextColor(Color.parseColor("#6B7280"))
                setPadding(dp(6), dp(2), dp(6), 0)
            })
            return row
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        ChatStore.ensureLoaded(this)

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }

        // ওপরের বার
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(8), dp(10))
            setBackgroundColor(card)
        }
        top.addView(CyberOrb(this), LinearLayout.LayoutParams(dp(40), dp(40)))
        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, dp(6), 0)
        }
        titles.addView(TextView(this).apply {
            text = "My Agent"
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(cyan)
        })
        subtitle = TextView(this).apply {
            textSize = 11f
            setTextColor(gray)
            maxLines = 1
        }
        titles.addView(subtitle)
        top.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        toggleBtn = TextView(this).apply {
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(7), dp(12), dp(7))
            setOnClickListener { toggleRobot() }
        }
        top.addView(toggleBtn)
        val menuBtn = TextView(this).apply {
            text = "⋮"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(12), 0, dp(12), 0)
        }
        menuBtn.setOnClickListener { showMenu(menuBtn) }
        top.addView(menuBtn)
        page.addView(top)

        // মেসেজের তালিকা
        val body = FrameLayout(this)
        list = ListView(this).apply {
            divider = null
            dividerHeight = 0
            adapter = this@MainActivity.adapter
            transcriptMode = ListView.TRANSCRIPT_MODE_ALWAYS_SCROLL
            setSelector(android.R.color.transparent)
        }
        body.addView(list, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        emptyText = TextView(this).apply {
            text = "◉\n\nকিছু জিজ্ঞেস করুন বা কাজ দিন\n\n💬 প্রশ্ন: উত্তর এখানেই আসবে\n⚡ কাজ: অ্যাপ বেরিয়ে রোবট কাজ করবে"
            textSize = 14f
            setTextColor(gray)
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.2f)
            setPadding(dp(30), 0, dp(30), 0)
        }
        body.addView(emptyText, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER
        ))
        page.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // নিচের লেখার বার
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(10))
            setBackgroundColor(card)
        }
        modeChip = TextView(this).apply {
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setOnClickListener {
                taskMode = !taskMode
                refreshMode()
                if (taskMode) toast("কাজ মোড: পাঠালে অ্যাপ বেরিয়ে রোবট কাজ শুরু করবে")
            }
        }
        bar.addView(modeChip)
        input = EditText(this).apply {
            setHintTextColor(Color.parseColor("#6B7280"))
            setTextColor(Color.WHITE)
            textSize = 15f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            maxLines = 4
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1F2937"))
                cornerRadius = dp(20).toFloat()
            }
            setPadding(dp(16), dp(10), dp(16), dp(10))
        }
        bar.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            leftMargin = dp(8)
        })
        val send = TextView(this).apply {
            text = "➤"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#0B1220"))
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(cyan)
            }
            setOnClickListener { send() }
        }
        bar.addView(send, LinearLayout.LayoutParams(dp(44), dp(44)).apply { leftMargin = dp(8) })
        page.addView(bar)

        setContentView(page)
        refreshMode()

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onStart() {
        super.onStart()
        ChatStore.addListener(storeListener)
        refreshChat()
    }

    override fun onStop() {
        ChatStore.removeListener(storeListener)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        refreshTop()
    }

    private fun refreshMode() {
        if (taskMode) {
            modeChip.text = "⚡ কাজ"
            modeChip.setTextColor(Color.parseColor("#0B1220"))
            modeChip.background = GradientDrawable().apply {
                setColor(Color.parseColor("#FACC15"))
                cornerRadius = dp(16).toFloat()
            }
            input.hint = "কী কাজ করতে হবে?"
        } else {
            modeChip.text = "💬 প্রশ্ন"
            modeChip.setTextColor(cyan)
            modeChip.background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), cyan)
            }
            input.hint = "মেসেজ লিখুন…"
        }
    }

    private fun refreshChat() {
        adapter.msgs = ChatStore.messages()
        adapter.typing = ChatStore.waiting
        adapter.notifyDataSetChanged()
        emptyText.visibility =
            if (adapter.msgs.isEmpty() && !adapter.typing) View.VISIBLE else View.GONE
        subtitle.text = ChatStore.currentTitle()
        list.post { if (adapter.count > 0) list.setSelection(adapter.count - 1) }
    }

    private fun refreshTop() {
        val run = AgentService.running
        toggleBtn.text = if (run) "◉ রোবট চালু" else "○ রোবট বন্ধ"
        toggleBtn.setTextColor(if (run) Color.parseColor("#0B1220") else cyan)
        toggleBtn.background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            if (run) {
                setColor(cyan)
            } else {
                setColor(Color.TRANSPARENT)
                setStroke(dp(1), cyan)
            }
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(input.windowToken, 0)
    }

    private fun askOverlay() {
        AlertDialog.Builder(this)
            .setTitle("অনুমতি দরকার")
            .setMessage("রোবট ভাসাতে \"অন্য অ্যাপের ওপরে দেখানো\" অনুমতি চালু করতে হবে।")
            .setNegativeButton("পরে", null)
            .setPositiveButton("অনুমতি দিন") { _, _ ->
                startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                )
            }
            .show()
    }

    private fun toggleRobot() {
        if (!Settings.canDrawOverlays(this)) {
            askOverlay()
            return
        }
        if (AgentService.running) AgentService.stop(this) else AgentService.start(this)
        handler.postDelayed({ refreshTop() }, 600)
    }

    private fun send() {
        val t = input.text.toString().trim()
        if (t.isEmpty()) return
        if (ChatStore.waiting) {
            toast("একটি কাজ চলছে, একটু অপেক্ষা করুন")
            return
        }
        if (taskMode) {
            if (!Settings.canDrawOverlays(this)) {
                askOverlay()
                return
            }
            input.setText("")
            hideKeyboard()
            AgentService.start(this, t)
            moveTaskToBack(true)
        } else {
            input.setText("")
            Agent.handle(this, t, false) { }
        }
    }

    private fun showMenu(anchor: View) {
        val m = PopupMenu(this, anchor)
        m.menu.add(0, 1, 0, "নতুন চ্যাট")
        m.menu.add(0, 2, 1, "পুরোনো চ্যাট")
        m.menu.add(0, 3, 2, "এই চ্যাট মুছুন")
        m.menu.add(0, 4, 3, "রোবটের অনুমতি")
        m.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> ChatStore.newChat(this)
                2 -> showHistory()
                3 -> confirmDelete()
                4 -> {
                    if (Settings.canDrawOverlays(this)) toast("অনুমতি দেওয়া আছে ✅") else askOverlay()
                }
            }
            true
        }
        m.show()
    }

    private fun showHistory() {
        val s = ChatStore.summaries()
        if (s.isEmpty()) {
            toast("কোনো চ্যাট নেই")
            return
        }
        val titles = s.map { it.second }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("পুরোনো চ্যাট")
            .setItems(titles) { _, i -> ChatStore.open(this, s[i].first) }
            .show()
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("এই চ্যাট মুছবেন?")
            .setMessage("মুছে গেলে আর ফেরত পাবেন না।")
            .setNegativeButton("না", null)
            .setPositiveButton("মুছুন") { _, _ -> ChatStore.deleteCurrent(this) }
            .show()
    }
}
