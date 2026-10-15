package com.whendue.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.ZonedDateTime

object Notify {
    private const val CH = "due"

    // ตั้งปลุกวันละครั้ง ตามเวลาที่ตั้งในแท็บตั้งค่า
    fun schedule(c: Context) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = ZonedDateTime.now()
        var t = now.toLocalDate().atTime(Prefs.hour(c), Prefs.minute(c)).atZone(now.zone)
        if (!t.isAfter(now)) t = t.plusDays(1)
        val pi = PendingIntent.getBroadcast(
            c, 1, Intent(c, AlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.toInstant().toEpochMilli(), pi)
    }

    fun check(c: Context) {
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CH, "แจ้งเตือนวันครบกำหนด", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val leads = (Prefs.leads(c).map { it.toLong() } + 0L).toSet()
        val items = Store.load(c).filter { it.hasDate && !it.done }
        items.filter { daysLeft(it.dueDay) in leadsFor(it, leads) }.forEach {
            val n = daysLeft(it.dueDay)
            val text = if (n == 0L) "ครบกำหนดวันนี้" else "อีก $n วัน (${thai(it.dueDay)})"
            post(c, nm, it.id.toInt(), it.name, text)
        }
        items.filter { it.payDay > 0 && daysLeft(it.payDay) in leadsFor(it, leads) }.forEach {
            val n = daysLeft(it.payDay)
            val text = if (n == 0L) "ถึงกำหนดชำระวันนี้" else "ชำระอีก $n วัน (${thai(it.payDay)})"
            post(c, nm, it.id.toInt() + 1, it.name, text)
        }
    }

    private fun leadsFor(i: Item, global: Set<Long>): Set<Long> =
        if (i.customLeads.isNotEmpty()) (i.customLeads.map { it.toLong() } + 0L).toSet() else global

    private fun post(c: Context, nm: NotificationManager, id: Int, title: String, text: String) {
        val nb = Notification.Builder(c, CH)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
        try {
            nm.notify(id, nb.build())
        } catch (e: SecurityException) {
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Notify.check(c)
        Notify.schedule(c)
        WidgetProvider.refresh(c)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Notify.schedule(c)
        WidgetProvider.refresh(c)
    }
}
