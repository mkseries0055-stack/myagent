package com.example.myagent

import android.content.Context
import android.os.Handler
import android.os.Looper

// অ্যাপ ও রোবট দুটোই এই এক জায়গা দিয়ে মেসেজ পাঠায়
object Agent {
    fun handle(ctx: Context, text: String, isTask: Boolean, onDone: (BrainReply) -> Unit) {
        val app = ctx.applicationContext
        ChatStore.ensureLoaded(app)
        ChatStore.add(app, "u", text)
        ChatStore.setWaiting(true)
        Brain.ask(app, text, isTask) { reply ->
            ChatStore.add(app, "a", reply.text)
            ChatStore.setWaiting(false)
            Handler(Looper.getMainLooper()).post { onDone(reply) }
        }
    }
}
