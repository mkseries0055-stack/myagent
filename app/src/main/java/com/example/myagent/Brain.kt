package com.example.myagent

import android.content.Context

data class BrainReply(val text: String, val show: Boolean = true)

// আপাতত নকল উত্তর। আসল AI সংযোগ পরের ধাপে এখানেই বসবে।
object Brain {
    fun ask(ctx: Context, text: String, onResult: (BrainReply) -> Unit) {
        Thread {
            try {
                Thread.sleep(1400)
            } catch (_: Throwable) {
            }
            onResult(
                BrainReply(
                    "আমি এখনো কোনো AI-র সাথে যুক্ত নই (API সেটিংস পরের ধাপে আসছে)।\n\nআপনি লিখেছেন: $text"
                )
            )
        }.start()
    }
}
