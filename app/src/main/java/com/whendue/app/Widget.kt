package com.whendue.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.text.SpannableString
import android.text.style.StrikethroughSpan
import android.view.View
import android.widget.RemoteViews
import java.time.LocalDate

class WidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        draw(context, appWidgetManager, appWidgetIds)
    }

    companion object {
        // ผสมสีธีมกับสีขาว (t = สัดส่วนสีธีม)
        private fun mix(c: Int, t: Float): Int = Color.rgb(
            (Color.red(c) * t + 255 * (1 - t)).toInt(),
            (Color.green(c) * t + 255 * (1 - t)).toInt(),
            (Color.blue(c) * t + 255 * (1 - t)).toInt()
        )

        private fun tagBg(n: Long): Int = when {
            n <= 7 -> 0xFFFBE9E7.toInt()
            n <= 30 -> 0xFFFBF1DC.toInt()
            else -> 0xFFE6F2EB.toInt()
        }

        fun refresh(c: Context) {
            val m = AppWidgetManager.getInstance(c)
            val ids = m.getAppWidgetIds(ComponentName(c, WidgetProvider::class.java))
            if (ids.isNotEmpty()) draw(c, m, ids)
        }

        private fun draw(c: Context, m: AppWidgetManager, ids: IntArray) {
            settle(c)
            val col = THEME_COLORS[Prefs.theme(c)]
            val today = LocalDate.now().toEpochDay()
            val list = Store.load(c)
                .filter { it.widget && (!it.done || it.doneAt == today) }
                .sortedWith(compareBy({ it.done || it.doneAt == today }, { !it.hasDate }, { it.dueDay }))
                .take(5)
            val rows = intArrayOf(R.id.r0, R.id.r1, R.id.r2, R.id.r3, R.id.r4)
            val chk = intArrayOf(R.id.c0, R.id.c1, R.id.c2, R.id.c3, R.id.c4)
            val nm = intArrayOf(R.id.n0, R.id.n1, R.id.n2, R.id.n3, R.id.n4)
            val du = intArrayOf(R.id.d0, R.id.d1, R.id.d2, R.id.d3, R.id.d4)
            val open = PendingIntent.getActivity(
                c, 0, Intent(c, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            for (id in ids) {
                val rv = RemoteViews(c.packageName, R.layout.widget)
                rv.setInt(R.id.whead, "setBackgroundColor", col)
                rv.setInt(R.id.wstripe, "setBackgroundColor", mix(col, 0.35f))
                rv.setTextViewText(R.id.wdate, thai(today).substringBeforeLast(" "))
                rv.setOnClickPendingIntent(R.id.wroot, open)
                for (k in 0 until 5) {
                    if (k >= list.size) {
                        rv.setViewVisibility(rows[k], View.GONE)
                        continue
                    }
                    val it = list[k]
                    val struck = it.done || it.doneAt == today
                    rv.setViewVisibility(rows[k], View.VISIBLE)
                    val name = SpannableString(it.name)
                    if (struck) name.setSpan(StrikethroughSpan(), 0, name.length, 0)
                    rv.setTextViewText(nm[k], name)
                    rv.setTextColor(nm[k], if (struck) Color.parseColor("#737373") else Color.parseColor("#111111"))
                    rv.setImageViewResource(chk[k], if (struck) R.drawable.ck_on else R.drawable.ck_off)
                    rv.setInt(chk[k], "setColorFilter", col)
                    rv.setViewVisibility(du[k], View.VISIBLE)
                    if (struck) {
                        rv.setTextViewText(du[k], "ทำแล้ว")
                        rv.setTextColor(du[k], 0xFF525252.toInt())
                        rv.setInt(du[k], "setBackgroundColor", 0xFFEEEEEE.toInt())
                    } else if (it.hasDate) {
                        val n = daysLeft(it.dueDay)
                        rv.setTextViewText(du[k], leftText(n))
                        rv.setTextColor(du[k], statusColor(n))
                        rv.setInt(du[k], "setBackgroundColor", tagBg(n))
                    } else {
                        rv.setViewVisibility(du[k], View.GONE)
                    }
                    val tick = PendingIntent.getBroadcast(
                        c, it.id.toInt(),
                        Intent(c, WidgetToggleReceiver::class.java).putExtra("id", it.id),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    rv.setOnClickPendingIntent(chk[k], tick)
                }
                m.updateAppWidget(id, rv)
            }
        }
    }
}

class WidgetToggleReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getLongExtra("id", 0L)
        val today = java.time.LocalDate.now().toEpochDay()
        val l = Store.load(c).map {
            if (it.id != id) it
            else if (it.done || it.doneAt == today) it.copy(done = false, doneAt = 0L) // แตะซ้ำ = ยกเลิกการติ๊ก
            else if (it.hasDate && it.every > 0) it.copy(doneAt = today) // รายการซ้ำ: ขีดฆ่าวันนี้ แล้วเลื่อนรอบพรุ่งนี้
            else it.copy(done = true, doneAt = today)
        }
        Store.save(c, l)
        WidgetProvider.refresh(c)
    }
}
