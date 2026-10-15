@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.whendue.app

import android.Manifest
import android.app.TimePickerDialog
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDate

val GRAY = Color(0xFF525252)
val LINE = Color(0xFFE5E5E5)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        Notify.schedule(this)
        setContent { App() }
    }
}

@Composable
fun App() {
    val c = LocalContext.current
    var all by remember { mutableStateOf(Store.load(c)) }
    var cats by remember { mutableStateOf(Prefs.cats(c)) }
    var leads by remember { mutableStateOf(Prefs.leads(c)) }
    var time by remember { mutableStateOf(Prefs.hour(c) * 60 + Prefs.minute(c)) }
    var theme by remember { mutableStateOf(Prefs.theme(c)) }
    var tab by remember { mutableStateOf(0) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Item?>(null) }

    fun commit(l: List<Item>) {
        all = l
        Store.save(c, l)
        WidgetProvider.refresh(c)
        Notify.schedule(c)
    }

    fun reload() {
        all = Store.load(c); cats = Prefs.cats(c); leads = Prefs.leads(c)
        time = Prefs.hour(c) * 60 + Prefs.minute(c); theme = Prefs.theme(c)
        WidgetProvider.refresh(c); Notify.schedule(c)
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(THEME_COLORS[theme]), onPrimary = Color.White,
            secondaryContainer = Color(0xFFF5F5F5), onSecondaryContainer = Color.Black,
            background = Color.White, surface = Color.White,
            surfaceContainer = Color.White, surfaceContainerHigh = Color.White,
            surfaceContainerHighest = Color(0xFFF5F5F5), surfaceTint = Color.Transparent,
            outline = Color(0xFFA3A3A3), outlineVariant = LINE
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = Color.White) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    when (tab) {
                        0 -> Home(all, { n -> commit(all.map { if (it.id == n.id) roll(n) else it }) }, { editing = it }, { adding = true })
                        1 -> Archive(
                            all.filter { it.done },
                            { r -> commit(all.map { if (it.id == r.id) r.copy(done = false, doneAt = 0L) else it }) },
                            { r -> commit(all.filter { it.id != r.id }) }
                        )
                        else -> SettingsTab(
                            cats, { Prefs.setCats(c, it); cats = it },
                            leads, { Prefs.setLeads(c, it); leads = it },
                            time, { Prefs.setTime(c, it / 60, it % 60); time = it; Notify.schedule(c) },
                            theme, { Prefs.setTheme(c, it); theme = it; WidgetProvider.refresh(c) },
                            { reload() }
                        )
                    }
                }
                TabBar(tab) { tab = it }
            }
            if (adding || editing != null) {
                val e = editing
                AddDialog(
                    initial = e, cats = cats,
                    onClose = { adding = false; editing = null },
                    onSave = { n ->
                        commit(if (e == null) all + n else all.map { if (it.id == n.id) n else it })
                        adding = false; editing = null
                    },
                    onDelete = if (e != null) fun() { commit(all.filter { it.id != e.id }); editing = null } else null
                )
            }
        }
    }
}

@Composable
fun Chip(label: String, selected: Boolean = false, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick, label = { Text(label) }, shape = RectangleShape,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = Color.White
        )
    )
}

@Composable
fun Lbl(t: String) {
    Text(t, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = GRAY, modifier = Modifier.padding(top = 6.dp))
}

@Composable
fun Hint(t: String) {
    Text(t, fontSize = 13.sp, color = GRAY)
}

@Composable
fun Tag(t: String, col: Color) {
    Text(
        t, color = col, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.border(1.dp, col).padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

@Composable
fun TabBar(sel: Int, onSel: (Int) -> Unit) {
    Column(Modifier.navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(LINE))
        Row(Modifier.fillMaxWidth()) {
            listOf("รายการ", "ที่เก็บถาวร", "ตั้งค่า").forEachIndexed { k, t ->
                Box(Modifier.weight(1f).height(56.dp).clickable { onSel(k) }, contentAlignment = Alignment.Center) {
                    Text(t, fontWeight = if (sel == k) FontWeight.Bold else FontWeight.Normal, color = if (sel == k) Color.Black else GRAY)
                    if (sel == k) Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.primary))
                }
            }
        }
    }
}

@Composable
fun Heading(t: String) {
    Row(Modifier.padding(top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(3.dp).height(22.dp).background(MaterialTheme.colorScheme.primary))
        Spacer(Modifier.width(10.dp))
        Text(t, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Home(all: List<Item>, onTick: (Item) -> Unit, onOpen: (Item) -> Unit, onAdd: () -> Unit) {
    val notes = all.filter { !it.hasDate && !it.done }
    val dues = all.filter { it.hasDate && !it.done }
        .sortedBy { if (it.payDay > 0) minOf(it.dueDay, it.payDay) else it.dueDay }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, top = 28.dp, end = 20.dp, bottom = 96.dp)) {
            item {
                Column {
                    Text("when due?", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("วันนี้ " + thai(LocalDate.now().toEpochDay()), color = GRAY, fontSize = 14.sp)
                }
            }
            if (notes.isNotEmpty()) {
                item { Heading("ปักหมุด") }
                items(notes, key = { it.id }) { n -> ItemRow(n, { onTick(n) }, { onOpen(n) }) }
            }
            item { Heading("ใกล้หมดอายุ") }
            if (dues.isEmpty()) item { Text("ยังไม่มีรายการ กด + เพิ่มรายการ", color = GRAY) }
            items(dues, key = { it.id }) { d -> ItemRow(d, { onTick(d) }, { onOpen(d) }) }
        }
        Button(
            onClick = onAdd, shape = RectangleShape,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(20.dp).height(52.dp)
        ) { Text("+ เพิ่มรายการ", fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
fun ItemRow(i: Item, onTick: () -> Unit, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onOpen() }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = false, onCheckedChange = { onTick() })
        Column(Modifier.weight(1f)) {
            Text(i.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(if (i.hasDate) "${i.category} · ${thai(i.dueDay)}" else i.category, fontSize = 14.sp, color = GRAY)
            if (i.hasDate && i.payDay > 0) Text("ชำระ ${thai(i.payDay)}", fontSize = 14.sp, color = GRAY)
        }
        if (i.hasDate) {
            val n = daysLeft(i.dueDay)
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Tag((if (i.payDay > 0) "หมด " else "") + leftText(n), Color(statusColor(n)))
                if (i.payDay > 0) {
                    val p = daysLeft(i.payDay)
                    Tag("ชำระ " + leftText(p), Color(statusColor(p)))
                }
            }
        }
    }
}

@Composable
fun Archive(done: List<Item>, onRestore: (Item) -> Unit, onDelete: (Item) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, top = 28.dp, end = 20.dp, bottom = 24.dp)) {
        item {
            Column {
                Text("ที่เก็บถาวร", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("รายการที่ติ๊กเสร็จและไม่ซ้ำ", color = GRAY, fontSize = 14.sp)
            }
        }
        if (done.isEmpty()) item { Text("ยังไม่มีรายการ", color = GRAY, modifier = Modifier.padding(top = 16.dp)) }
        items(done, key = { it.id }) { d ->
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(d.name, fontSize = 16.sp, color = Color(0xFF737373), textDecoration = TextDecoration.LineThrough)
                    if (d.doneAt > 0) Text("ทำแล้ว " + thai(d.doneAt), fontSize = 14.sp, color = GRAY)
                }
                TextButton(onClick = { onRestore(d) }) { Text("กู้คืน") }
                TextButton(onClick = { onDelete(d) }) { Text("ลบ", color = Color(0xFFB3261E)) }
            }
        }
    }
}

@Composable
fun Prompt(
    title: String, initial: String, numeric: Boolean = false,
    onOk: (String) -> Unit, onDelete: (() -> Unit)? = null, onClose: () -> Unit
) {
    var v by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onClose, shape = RectangleShape,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                v, { v = it }, singleLine = true, shape = RectangleShape,
                keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text)
            )
        },
        confirmButton = { TextButton(onClick = { if (v.isNotBlank()) onOk(v.trim()) }) { Text("ตกลง") } },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("ลบ", color = Color(0xFFB3261E)) }
                TextButton(onClick = onClose) { Text("ยกเลิก") }
            }
        }
    )
}

@Composable
fun SettingsTab(
    cats: List<String>, onCats: (List<String>) -> Unit,
    leads: List<Int>, onLeads: (List<Int>) -> Unit,
    time: Int, onTime: (Int) -> Unit,
    theme: Int, onTheme: (Int) -> Unit,
    onReload: () -> Unit
) {
    val c = LocalContext.current
    var catPrompt by remember { mutableStateOf<String?>(null) } // "" = เพิ่มใหม่
    var leadPrompt by remember { mutableStateOf(false) }
    val exp = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { u ->
        if (u != null) {
            c.contentResolver.openOutputStream(u)?.use { it.write(Backup.export(c).toByteArray()) }
            Toast.makeText(c, "สำรองข้อมูลแล้ว", Toast.LENGTH_SHORT).show()
        }
    }
    val imp = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) {
            try {
                val s = c.contentResolver.openInputStream(u)!!.bufferedReader().use { it.readText() }
                Backup.import(c, s)
                onReload()
                Toast.makeText(c, "นำเข้าข้อมูลแล้ว", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(c, "ไฟล์ไม่ถูกต้อง", Toast.LENGTH_SHORT).show()
            }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, top = 28.dp, end = 20.dp, bottom = 24.dp)) {
        Text("ตั้งค่า", fontSize = 28.sp, fontWeight = FontWeight.Bold)

        Heading("หมวดของฉัน")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            cats.forEach { k -> Chip(k) { catPrompt = k } }
            Chip("+ เพิ่ม") { catPrompt = "" }
        }
        Hint("แตะหมวดเพื่อเปลี่ยนชื่อหรือลบ")

        Heading("การเตือน")
        Hint("เตือนล่วงหน้า (แตะเพื่อเอาออก)")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            leads.forEach { d -> Chip("$d วัน ✕", true) { if (leads.size > 1) onLeads(leads - d) } }
            Chip("+ เพิ่ม") { leadPrompt = true }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { TimePickerDialog(c, { _, h, m -> onTime(h * 60 + m) }, time / 60, time % 60, true).show() },
            shape = RectangleShape
        ) { Text("เวลาเตือน: %02d:%02d".format(time / 60, time % 60)) }
        Hint("ถ้าเตือนช้าหรือไม่มา ให้ปิดการประหยัดแบตของแอปนี้ในตั้งค่ามือถือ")

        Heading("ธีมสี (แอปและ widget)")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            THEME_COLORS.forEachIndexed { k, col ->
                Box(
                    Modifier.size(44.dp).background(Color(col))
                        .then(if (k == theme) Modifier.border(2.dp, Color.Black) else Modifier)
                        .clickable { onTheme(k) },
                    contentAlignment = Alignment.Center
                ) { if (k == theme) Text("✓", color = Color.White, fontWeight = FontWeight.Bold) }
            }
        }
        Hint(THEME_NAMES[theme])

        Heading("ข้อมูล")
        OutlinedButton(onClick = { exp.launch("whendue-backup.json") }, shape = RectangleShape, modifier = Modifier.fillMaxWidth()) { Text("สำรองข้อมูล (ส่งออกไฟล์)") }
        OutlinedButton(onClick = { imp.launch(arrayOf("application/json", "*/*")) }, shape = RectangleShape, modifier = Modifier.fillMaxWidth()) { Text("นำเข้าข้อมูลจากไฟล์") }
    }

    catPrompt?.let { cp ->
        Prompt(
            title = if (cp.isEmpty()) "เพิ่มหมวด" else "แก้ไขหมวด", initial = cp,
            onOk = { v ->
                onCats((if (cp.isEmpty()) cats + v else cats.map { if (it == cp) v else it }).distinct())
                catPrompt = null
            },
            onDelete = if (cp.isNotEmpty()) fun() { onCats(cats - cp); catPrompt = null } else null,
            onClose = { catPrompt = null }
        )
    }
    if (leadPrompt) {
        Prompt(
            title = "เตือนล่วงหน้า (วัน)", initial = "", numeric = true,
            onOk = { v ->
                v.toIntOrNull()?.let { if (it in 1..365) onLeads((leads + it).distinct().sortedDescending()) }
                leadPrompt = false
            },
            onClose = { leadPrompt = false }
        )
    }
}

@Composable
fun AddDialog(initial: Item?, cats: List<String>, onClose: () -> Unit, onSave: (Item) -> Unit, onDelete: (() -> Unit)?) {
    val c = LocalContext.current
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var cat by remember { mutableStateOf(initial?.category ?: cats.firstOrNull() ?: "อื่นๆ") }
    var catMenu by remember { mutableStateOf(false) }
    var type by remember { mutableStateOf(if (initial != null && !initial.hasDate) 3 else initial?.type ?: 0) }
    var day by remember { mutableStateOf(initial?.baseDay ?: LocalDate.now().toEpochDay()) }
    var months by remember { mutableStateOf((initial?.months ?: 12).toString()) }
    var repeat by remember { mutableStateOf(initial?.repeat ?: 0) }
    var pay by remember { mutableStateOf(initial?.payDay ?: 0L) }
    var details by remember { mutableStateOf(initial?.details ?: "") }
    var widget by remember { mutableStateOf(initial?.widget ?: true) }
    var picker by remember { mutableStateOf(0) } // 0 = ปิด, 1 = วันหลัก, 2 = วันที่ชำระ

    val scanUri = remember { FileProvider.getUriForFile(c, c.packageName + ".fp", File(c.cacheDir, "scan.jpg")) }
    val takePic = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) Scan.run(c, scanUri, { r ->
            r.pao?.let { months = it.toString() }
            if (r.day != null) {
                day = r.day; type = 0
                Toast.makeText(c, "พบวันที่ ${thai(r.day)} กรุณาตรวจสอบก่อนบันทึก", Toast.LENGTH_LONG).show()
            } else Toast.makeText(c, "อ่านวันที่ไม่ได้ กรอกเองได้เลย", Toast.LENGTH_LONG).show()
        }, { Toast.makeText(c, "สแกนไม่สำเร็จ กรอกเองได้เลย", Toast.LENGTH_LONG).show() })
    }

    val m = months.toIntOrNull() ?: 12
    val due = if (type == 1 || type == 2) LocalDate.ofEpochDay(day).plusMonths(m.toLong()).toEpochDay() else day
    val primary = MaterialTheme.colorScheme.primary

    AlertDialog(
        onDismissRequest = onClose, shape = RectangleShape,
        title = { Text(if (initial == null) "เพิ่มรายการ" else "แก้ไขรายการ") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { takePic.launch(scanUri) }, shape = RectangleShape, modifier = Modifier.fillMaxWidth()) { Text("สแกนฉลากด้วยกล้อง") }

                Lbl("ชื่อรายการ")
                OutlinedTextField(
                    name, { name = it }, placeholder = { Text("เช่น พ.ร.บ. รถ, ครีมกันแดด") },
                    shape = RectangleShape, singleLine = true, modifier = Modifier.fillMaxWidth()
                )

                Lbl("หมวด")
                Box {
                    OutlinedButton(onClick = { catMenu = true }, shape = RectangleShape) { Text(cat) }
                    DropdownMenu(expanded = catMenu, onDismissRequest = { catMenu = false }) {
                        (cats + "อื่นๆ").distinct().forEach { k -> DropdownMenuItem(text = { Text(k) }, onClick = { cat = k; catMenu = false }) }
                    }
                }

                Lbl("ชนิดวัน")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("วันบนฉลาก", "นับจากเปิดใช้", "ประกัน / รับประกัน", "โน้ต (ไม่มีวันที่)").forEachIndexed { k, t ->
                        Chip(t, type == k) { type = k }
                    }
                }
                Hint(
                    when (type) {
                        0 -> "วันหมดอายุที่พิมพ์บนฉลาก หรือวันครบกำหนดที่รู้อยู่แล้ว (เช่น ภาษี)"
                        1 -> "ใส่วันที่เริ่มเปิดใช้ + อายุหลังเปิด (PAO) แอปคำนวณวันหมดให้"
                        2 -> "ใส่วันที่เริ่มประกัน + ระยะประกัน แอปคำนวณวันหมดให้ ใช้ได้ทั้งประกันภัยและรับประกันสินค้า"
                        else -> "จดไว้เฉยๆ ไม่มีวันที่และไม่แจ้งเตือน"
                    }
                )

                if (type != 3) {
                    Lbl(listOf("วันครบกำหนด / หมดอายุ", "วันที่เปิดใช้", "วันที่เริ่มประกัน")[type])
                    OutlinedButton(onClick = { picker = 1 }, shape = RectangleShape, modifier = Modifier.fillMaxWidth()) { Text(thai(day)) }

                    if (type == 1 || type == 2) {
                        Lbl(if (type == 1) "อายุหลังเปิด (เดือน)" else "ระยะประกัน (เดือน)")
                        OutlinedTextField(
                            months, { months = it.filter { ch -> ch.isDigit() } },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RectangleShape, singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            Modifier.fillMaxWidth().border(1.dp, primary).padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(if (type == 1) "จะหมดอายุ" else "หมดประกัน", color = primary)
                            Text(thai(due), color = primary, fontWeight = FontWeight.Bold)
                        }
                    }

                    Lbl("ซ้ำ")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("ไม่ซ้ำ", "ทุกปี", "ทุก 6 เดือน").forEachIndexed { k, t -> Chip(t, repeat == k) { repeat = k } }
                    }

                    Lbl("วันที่ชำระ (ถ้ามี)")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { picker = 2 }, shape = RectangleShape, modifier = Modifier.weight(1f)) {
                            Text(if (pay > 0) thai(pay) else "ไม่ระบุ (แตะเพื่อเลือก)")
                        }
                        if (pay > 0) TextButton(onClick = { pay = 0L }) { Text("ล้าง") }
                    }
                    Hint("เช่น วันจ่ายเบี้ยประกันหรือภาษี จะแจ้งเตือนแยกจากวันหมดอายุ")
                }

                Lbl("รายละเอียด / ค่าใช้จ่าย")
                OutlinedTextField(details, { details = it }, shape = RectangleShape, modifier = Modifier.fillMaxWidth())

                Row(Modifier.fillMaxWidth().clickable { widget = !widget }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = widget, onCheckedChange = { widget = it })
                    Text("แสดงใน widget")
                }
                if (onDelete != null) TextButton(onClick = onDelete) { Text("ลบรายการนี้", color = Color(0xFFB3261E)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) onSave(
                    Item(
                        id = initial?.id ?: System.currentTimeMillis(), name = name.trim(), category = cat,
                        type = if (type == 3) 0 else type, baseDay = day, months = m,
                        repeat = if (type == 3) 0 else repeat, details = details,
                        done = initial?.done ?: false, hasDate = type != 3,
                        widget = widget, doneAt = initial?.doneAt ?: 0L,
                        payDay = if (type == 3) 0L else pay
                    )
                )
            }) { Text("บันทึก") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("ยกเลิก") } }
    )

    if (picker != 0) {
        val start = if (picker == 2) (if (pay > 0) pay else due) else day
        val st = rememberDatePickerState(initialSelectedDateMillis = start * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { picker = 0 },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let { ms -> if (picker == 2) pay = ms / 86_400_000L else day = ms / 86_400_000L }
                    picker = 0
                }) { Text("ตกลง") }
            }
        ) { DatePicker(state = st) }
    }
}
