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
        fun refresh(c: Context) {
            val m = AppWidgetManager.getInstance(c)
            val ids = m.getAppWidgetIds(ComponentName(c, WidgetProvider::class.java))
            if (ids.isNotEmpty()) draw(c, m, ids)
        }

        private fun draw(c: Context, m: AppWidgetManager, ids: IntArray) {
            val col = THEME_COLORS[Prefs.theme(c)]
            val today = LocalDate.now().toEpochDay()
            val list = Store.load(c)
                .filter { it.widget && (!it.done || it.doneAt == today) }
                .sortedWith(compareBy({ it.done }, { !it.hasDate }, { it.dueDay }))
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
                rv.setTextColor(R.id.wtitle, col)
                rv.setOnClickPendingIntent(R.id.wroot, open)
                for (k in 0 until 5) {
                    if (k >= list.size) {
                        rv.setViewVisibility(rows[k], View.GONE)
                        continue
                    }
                    val it = list[k]
                    rv.setViewVisibility(rows[k], View.VISIBLE)
                    val name = SpannableString(it.name)
                    if (it.done) name.setSpan(StrikethroughSpan(), 0, name.length, 0)
                    rv.setTextViewText(nm[k], name)
                    rv.setTextColor(nm[k], if (it.done) Color.parseColor("#737373") else Color.parseColor("#111111"))
                    rv.setImageViewResource(chk[k], if (it.done) R.drawable.ck_on else R.drawable.ck_off)
                    rv.setInt(chk[k], "setColorFilter", col)
                    if (it.done) {
                        rv.setTextViewText(du[k], "ทำแล้ว")
                        rv.setTextColor(du[k], Color.parseColor("#525252"))
                    } else if (it.hasDate) {
                        val n = daysLeft(it.dueDay)
                        rv.setTextViewText(du[k], leftText(n))
                        rv.setTextColor(du[k], statusColor(n))
                    } else {
                        rv.setTextViewText(du[k], "")
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
        val l = Store.load(c).map {
            if (it.id != id) it else if (it.done) it.copy(done = false, doneAt = 0L) else roll(it)
        }
        Store.save(c, l)
        WidgetProvider.refresh(c)
    }
}
