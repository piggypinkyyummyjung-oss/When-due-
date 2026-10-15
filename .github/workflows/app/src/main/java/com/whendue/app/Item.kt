package com.whendue.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

// type: 0 = วันบนฉลาก, 1 = นับจากเปิดใช้ (PAO), 2 = ซื้อ + รับประกัน
// repeat: 0 = ไม่ซ้ำ, 1 = ทุกปี, 2 = ทุก 6 เดือน
data class Item(
    val id: Long,
    val name: String,
    val category: String,
    val type: Int,
    val baseDay: Long,
    val months: Int,
    val repeat: Int,
    val details: String,
    val done: Boolean,
    val hasDate: Boolean,
    val widget: Boolean = true,
    val doneAt: Long = 0L
) {
    val dueDay: Long
        get() = if (type == 1 || type == 2)
            LocalDate.ofEpochDay(baseDay).plusMonths(months.toLong()).toEpochDay()
        else baseDay
}

val THEME_NAMES = listOf("เขียวเซจ", "ถ่าน", "น้ำเงินเทา", "ดินเผา", "ม่วงเทา")
val THEME_COLORS = intArrayOf(
    0xFF3F6B5E.toInt(), 0xFF262626.toInt(), 0xFF44546A.toInt(), 0xFF8C5A44.toInt(), 0xFF6B5B73.toInt()
)

private val MONTHS = listOf("ม.ค.", "ก.พ.", "มี.ค.", "เม.ย.", "พ.ค.", "มิ.ย.", "ก.ค.", "ส.ค.", "ก.ย.", "ต.ค.", "พ.ย.", "ธ.ค.")

fun thai(day: Long): String {
    val d = LocalDate.ofEpochDay(day)
    return "${d.dayOfMonth} ${MONTHS[d.monthValue - 1]} ${d.year + 543}"
}

fun daysLeft(day: Long): Long = day - LocalDate.now().toEpochDay()

fun leftText(n: Long): String = when {
    n < 0 -> "เกิน ${-n} วัน"
    n == 0L -> "วันนี้"
    n < 60 -> "$n วัน"
    else -> "${n / 30} เดือน"
}

fun statusColor(n: Long): Int = when {
    n <= 7 -> 0xFFB3261E.toInt()
    n <= 30 -> 0xFF8A5A00.toInt()
    else -> 0xFF1F6F43.toInt()
}

// ติ๊กเสร็จ: ถ้าตั้งซ้ำ เลื่อนไปรอบถัดไป ถ้าไม่ซ้ำ ย้ายเข้าที่เก็บถาวร
fun roll(i: Item): Item {
    if (i.hasDate && i.repeat > 0) {
        val m = if (i.repeat == 1) 12L else 6L
        val next = LocalDate.ofEpochDay(i.dueDay).plusMonths(m).toEpochDay()
        return i.copy(type = 0, baseDay = next)
    }
    return i.copy(done = true, doneAt = LocalDate.now().toEpochDay())
}

fun itemsToJson(list: List<Item>): JSONArray {
    val a = JSONArray()
    list.forEach {
        a.put(
            JSONObject().put("id", it.id).put("name", it.name).put("category", it.category)
                .put("type", it.type).put("baseDay", it.baseDay).put("months", it.months)
                .put("repeat", it.repeat).put("details", it.details)
                .put("done", it.done).put("hasDate", it.hasDate)
                .put("widget", it.widget).put("doneAt", it.doneAt)
        )
    }
    return a
}

fun itemsFromJson(a: JSONArray): List<Item> = List(a.length()) { k ->
    val o = a.getJSONObject(k)
    Item(
        o.getLong("id"), o.getString("name"), o.getString("category"),
        o.getInt("type"), o.getLong("baseDay"), o.getInt("months"),
        o.getInt("repeat"), o.getString("details"),
        o.getBoolean("done"), o.getBoolean("hasDate"),
        o.optBoolean("widget", true), o.optLong("doneAt", 0L)
    )
}

object Store {
    fun load(c: Context): List<Item> {
        val s = c.getSharedPreferences("wd", 0).getString("items", "[]") ?: "[]"
        return itemsFromJson(JSONArray(s))
    }

    fun save(c: Context, list: List<Item>) {
        c.getSharedPreferences("wd", 0).edit().putString("items", itemsToJson(list).toString()).apply()
    }
}

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("wd", 0)
    private val DEFAULT_CATS = listOf("เครื่องสำอาง", "เครื่องใช้ไฟฟ้า", "ประกัน", "รถ", "ภาษี", "เอกสาร")

    fun cats(c: Context): List<String> {
        val s = sp(c).getString("cats", null) ?: return DEFAULT_CATS
        val a = JSONArray(s)
        return List(a.length()) { a.getString(it) }
    }

    fun setCats(c: Context, l: List<String>) = sp(c).edit().putString("cats", JSONArray(l).toString()).apply()

    fun leads(c: Context): List<Int> {
        val s = sp(c).getString("leads", null) ?: return listOf(30, 7, 1)
        val a = JSONArray(s)
        return List(a.length()) { a.getInt(it) }
    }

    fun setLeads(c: Context, l: List<Int>) = sp(c).edit().putString("leads", JSONArray(l).toString()).apply()
    fun hour(c: Context) = sp(c).getInt("hour", 9)
    fun minute(c: Context) = sp(c).getInt("minute", 0)
    fun setTime(c: Context, h: Int, m: Int) = sp(c).edit().putInt("hour", h).putInt("minute", m).apply()
    fun theme(c: Context) = sp(c).getInt("theme", 0)
    fun setTheme(c: Context, t: Int) = sp(c).edit().putInt("theme", t).apply()
}

object Backup {
    fun export(c: Context): String = JSONObject()
        .put("items", itemsToJson(Store.load(c)))
        .put("cats", JSONArray(Prefs.cats(c)))
        .put("leads", JSONArray(Prefs.leads(c)))
        .put("hour", Prefs.hour(c)).put("minute", Prefs.minute(c))
        .put("theme", Prefs.theme(c)).toString()

    fun import(c: Context, s: String) {
        val o = JSONObject(s)
        Store.save(c, itemsFromJson(o.getJSONArray("items")))
        val ca = o.getJSONArray("cats")
        Prefs.setCats(c, List(ca.length()) { ca.getString(it) })
        val la = o.getJSONArray("leads")
        Prefs.setLeads(c, List(la.length()) { la.getInt(it) })
        Prefs.setTime(c, o.getInt("hour"), o.getInt("minute"))
        Prefs.setTheme(c, o.getInt("theme"))
    }
}
