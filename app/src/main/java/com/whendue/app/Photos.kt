package com.whendue.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object Photos {
    private fun dir(c: Context) = File(c.filesDir, "photos").apply { mkdirs() }
    fun file(c: Context, name: String) = File(dir(c), name)

    // คัดลอกรูปเข้าที่เก็บของแอป ย่อให้ไม่เกิน 1600 px
    fun add(c: Context, uri: Uri): String? {
        try {
            val bytes = c.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
            var s = 1
            while (maxOf(o.outWidth, o.outHeight) / s > 2400) s *= 2
            var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s })
                ?: return null
            val rot = when (ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                6 -> 90f
                3 -> 180f
                8 -> 270f
                else -> 0f
            }
            if (rot != 0f) {
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rot) }, true)
            }
            val big = maxOf(bmp.width, bmp.height)
            if (big > 1600) {
                val k = 1600f / big
                bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt(), (bmp.height * k).toInt(), true)
            }
            val name = "p" + System.currentTimeMillis() + (0..999).random() + ".jpg"
            FileOutputStream(file(c, name)).use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            return name
        } catch (e: Exception) {
            return null
        }
    }

    fun load(c: Context, name: String, max: Int): ImageBitmap? {
        try {
            val f = file(c, name)
            if (!f.exists()) return null
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, o)
            var s = 1
            while (maxOf(o.outWidth, o.outHeight) / s > max) s *= 2
            return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })?.asImageBitmap()
        } catch (e: Exception) {
            return null
        }
    }

    // ลบไฟล์รูปที่ไม่มีรายการไหนใช้แล้ว
    fun cleanup(c: Context) {
        val used = Store.load(c).flatMap { it.photoList }.toSet()
        dir(c).listFiles()?.forEach { if (it.name !in used) it.delete() }
    }
}

// สำรองข้อมูล: ไฟล์ zip มี data.json และโฟลเดอร์ photos/
object BackupZip {
    fun export(c: Context, out: OutputStream) {
        ZipOutputStream(out).use { z ->
            z.putNextEntry(ZipEntry("data.json"))
            z.write(Backup.export(c).toByteArray())
            z.closeEntry()
            Store.load(c).flatMap { it.photoList }.distinct().forEach { n ->
                val f = Photos.file(c, n)
                if (f.exists()) {
                    z.putNextEntry(ZipEntry("photos/$n"))
                    f.inputStream().use { it.copyTo(z) }
                    z.closeEntry()
                }
            }
        }
    }

    // รับได้ทั้งไฟล์ zip และไฟล์ JSON แบบเก่า
    fun import(c: Context, input: InputStream): Boolean {
        val bytes = input.readBytes()
        if (bytes.size > 3 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
            var json: String? = null
            ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
                var e = z.nextEntry
                while (e != null) {
                    if (e.name == "data.json") {
                        json = z.readBytes().toString(Charsets.UTF_8)
                    } else if (e.name.startsWith("photos/") && !e.isDirectory) {
                        val n = File(e.name).name
                        FileOutputStream(Photos.file(c, n)).use { o -> z.copyTo(o) }
                    }
                    z.closeEntry()
                    e = z.nextEntry
                }
            }
            val j = json ?: return false
            Backup.import(c, j)
            return true
        }
        Backup.import(c, bytes.toString(Charsets.UTF_8))
        return true
    }
}
