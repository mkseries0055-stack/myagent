package com.example.myagent

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

data class Msg(val role: String, val text: String, val ts: Long) // role: "u" = আপনি, "a" = এজেন্ট

class Conv(val id: Long, var title: String) {
    val msgs = ArrayList<Msg>()
    var updated = id
}

// সব চ্যাট এক জায়গায় (অ্যাপের পাতা ও রোবটের বাবল একই ইতিহাস শেয়ার করে)
object ChatStore {
    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val convs = ArrayList<Conv>()
    private var currentId = 0L
    private var loaded = false
    @Volatile private var w = false

    val waiting: Boolean get() = w

    private fun file(ctx: Context) = File(ctx.filesDir, "chats.json")

    @Synchronized
    fun ensureLoaded(ctx: Context) {
        if (loaded) return
        loaded = true
        try {
            val f = file(ctx)
            if (f.exists()) {
                val root = JSONObject(f.readText())
                currentId = root.optLong("current", 0L)
                val arr = root.optJSONArray("convs") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val c = Conv(o.getLong("id"), o.optString("title", "চ্যাট"))
                    c.updated = o.optLong("updated", c.id)
                    val ms = o.optJSONArray("msgs") ?: JSONArray()
                    for (j in 0 until ms.length()) {
                        val m = ms.getJSONObject(j)
                        c.msgs.add(Msg(m.optString("r", "u"), m.optString("t", ""), m.optLong("ts", 0L)))
                    }
                    convs.add(c)
                }
            }
        } catch (_: Throwable) {
        }
        if (convs.isEmpty()) {
            val c = Conv(System.currentTimeMillis(), "নতুন চ্যাট")
            convs.add(c)
            currentId = c.id
        } else if (convs.none { it.id == currentId }) {
            currentId = convs.maxByOrNull { it.updated }?.id ?: convs[0].id
        }
    }

    private fun currentLocked(): Conv {
        val c = convs.firstOrNull { it.id == currentId }
        if (c != null) return c
        val n = Conv(System.currentTimeMillis(), "নতুন চ্যাট")
        convs.add(n)
        currentId = n.id
        return n
    }

    @Synchronized
    fun messages(): List<Msg> = ArrayList(currentLocked().msgs)

    @Synchronized
    fun currentTitle(): String = currentLocked().title

    @Synchronized
    fun lastAi(): String = currentLocked().msgs.lastOrNull { it.role == "a" }?.text ?: ""

    @Synchronized
    fun summaries(): List<Pair<Long, String>> =
        convs.sortedByDescending { it.updated }.map { Pair(it.id, it.title) }

    fun add(ctx: Context, role: String, text: String) {
        synchronized(this) {
            ensureLoaded(ctx)
            val c = currentLocked()
            c.msgs.add(Msg(role, text, System.currentTimeMillis()))
            c.updated = System.currentTimeMillis()
            if (role == "u" && (c.title == "নতুন চ্যাট" || c.title.isEmpty())) {
                c.title = text.replace("\n", " ").take(30)
            }
            save(ctx)
        }
        changed()
    }

    fun newChat(ctx: Context) {
        synchronized(this) {
            ensureLoaded(ctx)
            val cur = currentLocked()
            if (cur.msgs.isNotEmpty()) {
                val n = Conv(System.currentTimeMillis(), "নতুন চ্যাট")
                convs.add(n)
                currentId = n.id
            }
            save(ctx)
        }
        changed()
    }

    fun open(ctx: Context, id: Long) {
        synchronized(this) {
            ensureLoaded(ctx)
            if (convs.any { it.id == id }) currentId = id
            save(ctx)
        }
        changed()
    }

    fun deleteCurrent(ctx: Context) {
        synchronized(this) {
            ensureLoaded(ctx)
            convs.removeAll { it.id == currentId }
            if (convs.isEmpty()) {
                val n = Conv(System.currentTimeMillis(), "নতুন চ্যাট")
                convs.add(n)
            }
            currentId = convs.maxByOrNull { it.updated }?.id ?: convs[0].id
            save(ctx)
        }
        changed()
    }

    fun setWaiting(b: Boolean) {
        w = b
        changed()
    }

    private fun save(ctx: Context) {
        try {
            val root = JSONObject()
            root.put("current", currentId)
            val arr = JSONArray()
            for (c in convs) {
                val o = JSONObject()
                o.put("id", c.id)
                o.put("title", c.title)
                o.put("updated", c.updated)
                val ms = JSONArray()
                for (m in c.msgs) {
                    val mo = JSONObject()
                    mo.put("r", m.role)
                    mo.put("t", m.text)
                    mo.put("ts", m.ts)
                    ms.put(mo)
                }
                o.put("msgs", ms)
                arr.put(o)
            }
            root.put("convs", arr)
            file(ctx).writeText(root.toString())
        } catch (_: Throwable) {
        }
    }

    fun addListener(l: () -> Unit) {
        listeners.add(l)
    }

    fun removeListener(l: () -> Unit) {
        listeners.remove(l)
    }

    private fun changed() {
        main.post {
            for (l in listeners) {
                try { l() } catch (_: Throwable) {}
            }
        }
    }
}
