package com.whendue.app

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.time.LocalDate

data class ScanResult(val day: Long?, val pao: Int?)

object Scan {
    fun run(c: Context, uri: Uri, done: (ScanResult) -> Unit, fail: () -> Unit) {
        try {
            val img = InputImage.fromFilePath(c, uri)
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(img)
                .addOnSuccessListener { done(parse(it.text)) }
                .addOnFailureListener { fail() }
        } catch (e: Exception) {
            fail()
        }
    }

    private fun year(y: Int): Int = when {
        y < 100 -> 2000 + y
        y > 2400 -> y - 543
        else -> y
    }

    fun parse(text: String): ScanResult {
        val up = text.uppercase()
        val keys = Regex("EXP|BB|BEST BEFORE|USE BY|หมดอายุ").findAll(up).map { it.range.first }.toList()
        val found = mutableListOf<Pair<Long, Boolean>>() // วัน, อยู่ใกล้คำว่า EXP/หมดอายุ
        fun add(pos: Int, d: LocalDate?) {
            if (d == null || d.year !in 2020..2060) return
            found.add(d.toEpochDay() to keys.any { pos >= it && pos - it <= 25 })
        }
        // วัน/เดือน/ปี
        Regex("(\\d{1,2})[/.\\-](\\d{1,2})[/.\\-](\\d{2,4})").findAll(up).forEach {
            val (d, m, y) = it.destructured
            add(it.range.first, try { LocalDate.of(year(y.toInt()), m.toInt(), d.toInt()) } catch (e: Exception) { null })
        }
        // เดือน/ปี  (ใช้วันสุดท้ายของเดือน)
        Regex("(?<![\\d/.\\-])(\\d{1,2})[/.\\-](\\d{4}|\\d{2})(?![\\d/.\\-])").findAll(up).forEach {
            val (m, y) = it.destructured
            add(it.range.first, try { LocalDate.of(year(y.toInt()), m.toInt(), 1).plusMonths(1).minusDays(1) } catch (e: Exception) { null })
        }
        val keyed = found.filter { it.second }.map { it.first }
        val day = if (keyed.isNotEmpty()) keyed.max() else found.map { it.first }.maxOrNull()
        val pao = Regex("(\\d{1,2})\\s?M(?![A-Z])").find(up)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..60 }
        return ScanResult(day, pao)
    }
}
