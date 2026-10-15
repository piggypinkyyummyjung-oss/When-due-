@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.whendue.app

import android.Manifest
import android.app.TimePickerDialog
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDate

val GRAY = Color(0xFF525252)
val LINE = Color(0xFFE5E5E5)
val BORDER = Color(0xFFD4D4D4)
val INK = Color(0xFF111111)
val RED = Color(0xFFB3261E)

val TYPE_SHORT = listOf("วันบนฉลาก", "นับจากเปิดใช้", "ประกัน", "วันที่ชำระ", "โน้ต")
val TYPE_FULL = listOf("วันบนฉลาก", "นับจากเปิดใช้", "ประกัน / รับประกัน", "วันที่ชำระ", "โน้ต (ไม่มีวันที่)")
val TYPE_DESC = listOf(
    "วันหมดอายุที่พิมพ์บนฉลาก",
    "วันที่เปิดใช้ + อายุหลังเปิด (PAO)",
    "วันที่เริ่มประกัน + ระยะประกัน",
    "วันจ่ายเบี้ย ภาษี ค่างวด",
    "จดไว้ ไม่มีวันที่"
)
val REPEATS = listOf("ครั้งเดียว", "ทุกปี", "ทุก 6 เดือน")
val MONTH_FULL = listOf(
    "มกราคม", "กุมภาพันธ์", "มีนาคม", "เมษายน", "พฤษภาคม", "มิถุนายน",
    "กรกฎาคม", "สิงหาคม", "กันยายน", "ตุลาคม", "พฤศจิกายน", "ธันวาคม"
)

fun fmtMonths(m: Int): String = if (m >= 12 && m % 12 == 0) "${m / 12} ปี" else "$m เดือน"

// ระยะเตือนล่วงหน้า เก็บเป็นจำนวนวัน (1 เดือน = 30 วัน, 1 ปี = 365 วัน)
fun fmtLead(d: Int): String = when {
    d >= 365 && d % 365 == 0 -> "${d / 365} ปี"
    d >= 30 && d % 30 == 0 -> "${d / 30} เดือน"
    else -> "$d วัน"
}

fun fmtLeads(l: List<Int>): String = l.sortedDescending().joinToString(", ") { fmtLead(it) }

val LEAD_PRESETS = listOf(1, 3, 7, 14, 30, 90, 180, 365)

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

// ---------- แอปหลัก ----------
@Composable
fun App() {
    val c = LocalContext.current
    var all by remember { mutableStateOf(Store.load(c)) }
    var cats by remember { mutableStateOf(Prefs.cats(c)) }
    var leads by remember { mutableStateOf(Prefs.leads(c)) }
    var time by remember { mutableStateOf(Prefs.hour(c) * 60 + Prefs.minute(c)) }
    var theme by remember { mutableStateOf(Prefs.theme(c)) }
    var tab by remember { mutableStateOf(0) }
    var detailId by remember { mutableStateOf<Long?>(null) }
    var form by remember { mutableStateOf(false) }
    var formItem by remember { mutableStateOf<Item?>(null) }

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

    fun openForm(i: Item?) {
        formItem = i
        form = true
    }

    val detail = all.firstOrNull { it.id == detailId }

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
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        when (tab) {
                            0 -> Home(all, { detailId = it.id }, { openForm(null) }, { openForm(it) })
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
                if (detail != null) {
                    BackHandler { detailId = null }
                    Surface(Modifier.fillMaxSize(), color = Color.White) {
                        DetailScreen(
                            detail, leads,
                            onEdit = { openForm(detail) },
                            onRenew = {
                                val next = LocalDate.ofEpochDay(detail.baseDay).plusMonths(12).toEpochDay()
                                commit(all.map { if (it.id == detail.id) detail.copy(baseDay = next) else it })
                                Toast.makeText(c, "ต่ออายุ +1 ปี แล้ว", Toast.LENGTH_SHORT).show()
                            },
                            onDone = { commit(all.map { if (it.id == detail.id) roll(detail) else it }); detailId = null },
                            onDelete = { commit(all.filter { it.id != detail.id }); detailId = null }
                        )
                    }
                }
                if (form) {
                    BackHandler { form = false }
                    Surface(Modifier.fillMaxSize(), color = Color.White) {
                        FormScreen(
                            initial = formItem, cats = cats, leads = leads,
                            onClose = { form = false },
                            onSave = { n ->
                                commit(if (all.any { it.id == n.id }) all.map { if (it.id == n.id) n else it } else all + n)
                                form = false
                            }
                        )
                    }
                }
            }
        }
    }
}

// ---------- ส่วนประกอบเล็ก ----------
@Composable
fun CalIcon() {
    Canvas(Modifier.size(22.dp)) {
        val s = size.width
        val w = 2.dp.toPx()
        drawRect(color = INK, topLeft = Offset(s * 0.125f, s * 0.21f), size = Size(s * 0.75f, s * 0.67f), style = Stroke(width = w))
        drawLine(INK, Offset(s * 0.125f, s * 0.42f), Offset(s * 0.875f, s * 0.42f), strokeWidth = w)
        drawLine(INK, Offset(s * 0.33f, s * 0.12f), Offset(s * 0.33f, s * 0.29f), strokeWidth = w)
        drawLine(INK, Offset(s * 0.67f, s * 0.12f), Offset(s * 0.67f, s * 0.29f), strokeWidth = w)
    }
}

@Composable
fun ChevIcon() {
    Canvas(Modifier.size(22.dp)) {
        val s = size.width
        val w = 2.dp.toPx()
        drawLine(INK, Offset(s * 0.25f, s * 0.38f), Offset(s * 0.5f, s * 0.62f), strokeWidth = w)
        drawLine(INK, Offset(s * 0.5f, s * 0.62f), Offset(s * 0.75f, s * 0.38f), strokeWidth = w)
    }
}

@Composable
fun CamIcon() {
    Canvas(Modifier.size(22.dp)) {
        val s = size.width
        val w = 2.dp.toPx()
        drawRect(color = INK, topLeft = Offset(s * 0.1f, s * 0.3f), size = Size(s * 0.8f, s * 0.55f), style = Stroke(width = w))
        drawCircle(color = INK, radius = s * 0.16f, center = Offset(s * 0.5f, s * 0.57f), style = Stroke(width = w))
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
fun Lab(t: String) {
    Text(t, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = GRAY)
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
fun Select(label: String, value: String, modifier: Modifier = Modifier, cal: Boolean = false, onClick: () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Lab(label)
        Row(
            Modifier.fillMaxWidth().height(48.dp).border(1.dp, BORDER).clickable(onClick = onClick).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            Text(value, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            if (cal) CalIcon() else ChevIcon()
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
fun Sheet(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onClose, shape = RectangleShape, containerColor = Color.White,
        dragHandle = null, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
fun OptionRow(title: String, desc: String? = null, selected: Boolean = false, onClick: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold)
                if (desc != null) Text(desc, fontSize = 13.sp, color = GRAY)
            }
            if (selected) Text("✓", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(LINE))
    }
}

@Composable
fun CalendarSheet(initial: Long, onPick: (Long) -> Unit, onClose: () -> Unit) {
    var sel by remember { mutableStateOf(initial) }
    var month by remember { mutableStateOf(LocalDate.ofEpochDay(initial).withDayOfMonth(1)) }
    val primary = MaterialTheme.colorScheme.primary
    fun setSel(d: Long) {
        sel = d
        month = LocalDate.ofEpochDay(d).withDayOfMonth(1)
    }
    Sheet("เลือกวันที่", onClose) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(Color(0xFFF5F5F5)).clickable { month = month.minusMonths(1) }, contentAlignment = Alignment.Center) { Text("‹", fontSize = 22.sp) }
            Text("${MONTH_FULL[month.monthValue - 1]} ${month.year + 543}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Box(Modifier.size(44.dp).background(Color(0xFFF5F5F5)).clickable { month = month.plusMonths(1) }, contentAlignment = Alignment.Center) { Text("›", fontSize = 22.sp) }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("อา", "จ", "อ", "พ", "พฤ", "ศ", "ส").forEach {
                Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 13.sp, color = GRAY, fontWeight = FontWeight.SemiBold)
            }
        }
        val offset = month.dayOfWeek.value % 7
        val len = month.lengthOfMonth()
        val rows = (offset + len + 6) / 7
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (k in 0 until 7) {
                    val d = r * 7 + k - offset + 1
                    val valid = d in 1..len
                    val picked = valid && month.withDayOfMonth(d).toEpochDay() == sel
                    Box(
                        Modifier.weight(1f).height(44.dp)
                            .then(if (picked) Modifier.background(primary) else Modifier)
                            .clickable(enabled = valid) { sel = month.withDayOfMonth(d).toEpochDay() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (valid) Text("$d", fontSize = 16.sp, color = if (picked) Color.White else INK, fontWeight = if (picked) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("วันนี้") { setSel(LocalDate.now().toEpochDay()) }
            Chip("+1 เดือน") { setSel(LocalDate.ofEpochDay(sel).plusMonths(1).toEpochDay()) }
            Chip("+6 เดือน") { setSel(LocalDate.ofEpochDay(sel).plusMonths(6).toEpochDay()) }
            Chip("+1 ปี") { setSel(LocalDate.ofEpochDay(sel).plusYears(1).toEpochDay()) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onClose, shape = RectangleShape, modifier = Modifier.weight(1f).height(52.dp), border = BorderStroke(1.dp, INK), colors = ButtonDefaults.outlinedButtonColors(contentColor = INK)) { Text("ยกเลิก", fontWeight = FontWeight.SemiBold) }
            Button(onClick = { onPick(sel) }, shape = RectangleShape, modifier = Modifier.weight(1f).height(52.dp)) { Text("ตกลง", fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
fun MonthsSheet(title: String, options: List<Int>, current: Int, onPick: (Int) -> Unit, onCustom: () -> Unit, onClose: () -> Unit) {
    Sheet(title, onClose) {
        options.forEach { m -> OptionRow(fmtMonths(m), selected = m == current) { onPick(m) } }
        OptionRow("กำหนดเอง…") { onCustom() }
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
                if (onDelete != null) TextButton(onClick = onDelete) { Text("ลบ", color = RED) }
                TextButton(onClick = onClose) { Text("ยกเลิก") }
            }
        }
    )
}

// ---------- หน้าแรก ----------
fun quicks(): List<Pair<String, Item>> {
    val t = LocalDate.now().toEpochDay()
    fun mk(n: String, cat: String, type: Int, repeat: Int = 0) = Item(
        id = 0, name = n, category = cat, type = type, baseDay = t, months = 12,
        repeat = repeat, details = "", done = false, hasDate = true
    )
    return listOf(
        "พ.ร.บ." to mk("พ.ร.บ. รถ", "รถ", 2),
        "ภาษีที่ดิน" to mk("ภาษีที่ดิน", "ภาษี", 3, 1),
        "ประกันรถ" to mk("ประกันรถยนต์", "ประกัน", 2),
        "เครื่องสำอาง" to mk("", "เครื่องสำอาง", 1)
    )
}

fun subText(i: Item): String = when (i.type) {
    1 -> "เปิดใช้แล้ว · PAO ${i.months} เดือน"
    2 -> "ประกันถึง ${thai(i.dueDay)}"
    3 -> "ชำระ ${thai(i.dueDay)}"
    else -> "หมด ${thai(i.dueDay)}"
}

@Composable
fun Home(all: List<Item>, onOpen: (Item) -> Unit, onAdd: () -> Unit, onQuick: (Item) -> Unit) {
    val live = all.filter { !it.done }
    val notes = live.filter { !it.hasDate }
    val dues = live.filter { it.hasDate }.sortedBy { it.dueDay }
    Box(Modifier.fillMaxSize()) {
        if (live.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(start = 20.dp, top = 28.dp, end = 20.dp, bottom = 96.dp)) {
                Column {
                    Text("when due?", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("วันนี้ " + thai(LocalDate.now().toEpochDay()), color = GRAY, fontSize = 14.sp)
                }
                Column(
                    Modifier.weight(1f).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                ) {
                    Text("ยังไม่มีรายการ", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text("เริ่มจากรายการที่ใช้บ่อย หรือเพิ่มเอง", fontSize = 14.sp, color = GRAY)
                    Spacer(Modifier.height(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) { quicks().forEach { (t, i) -> Chip(t) { onQuick(i) } } }
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(start = 20.dp, top = 28.dp, end = 20.dp, bottom = 96.dp)) {
                item {
                    Column {
                        Text("when due?", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Text("วันนี้ " + thai(LocalDate.now().toEpochDay()), color = GRAY, fontSize = 14.sp)
                    }
                }
                if (notes.isNotEmpty()) {
                    item { Heading("ปักหมุด") }
                    items(notes, key = { it.id }) { n ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpen(n) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(n.name, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Tag(n.category, GRAY)
                        }
                    }
                }
                if (dues.isNotEmpty()) {
                    item { Heading("ใกล้หมดอายุ") }
                    items(dues, key = { it.id }) { d ->
                        val n = daysLeft(d.dueDay)
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpen(d) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(d.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                Text(subText(d), fontSize = 14.sp, color = GRAY)
                            }
                            Tag(leftText(n), Color(statusColor(n)))
                        }
                    }
                }
            }
        }
        Button(
            onClick = onAdd, shape = RectangleShape,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(20.dp).height(52.dp)
        ) { Text("+ เพิ่มรายการ", fontWeight = FontWeight.SemiBold) }
    }
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

// ---------- หน้ารายละเอียด ----------
@Composable
fun DetailScreen(
    i: Item, leads: List<Int>,
    onEdit: () -> Unit, onRenew: () -> Unit, onDone: () -> Unit, onDelete: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = 20.dp, top = 28.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(i.category, fontSize = 14.sp, color = GRAY)
        Text(i.name, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        if (i.hasDate) {
            val n = daysLeft(i.dueDay)
            val col = Color(statusColor(n))
            Text(
                if (n < 0) "เกินกำหนด ${-n} วัน" else if (n == 0L) "วันนี้" else "เหลือ $n วัน",
                fontSize = 44.sp, fontWeight = FontWeight.Bold, color = col
            )
            Text(
                (when (i.type) { 2 -> "หมดประกัน "; 3 -> "กำหนดชำระ "; else -> "หมดอายุ " }) + thai(i.dueDay),
                fontSize = 14.sp, color = GRAY
            )
            if (i.every > 0) Text("ครบกำหนด " + repText(i.every, i.everyUnit), fontSize = 14.sp, color = GRAY)
            val start = if (i.type == 1 || i.type == 2) i.baseDay else i.id / 86_400_000L
            val total = (i.dueDay - start).coerceAtLeast(1)
            val frac = ((LocalDate.now().toEpochDay() - start).toFloat() / total).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(4.dp).background(LINE)) {
                Box(Modifier.fillMaxWidth(frac).height(4.dp).background(col))
            }
            Heading("เตือนล่วงหน้า")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (if (i.customLeads.isNotEmpty()) i.customLeads else leads).sortedDescending().forEach { d ->
                    val passed = n <= d
                    Tag(fmtLead(d) + " · " + if (passed) "แล้ว" else "รอเตือน", if (passed) Color(0xFF1F6F43) else GRAY)
                }
            }
        }
        Heading("รายละเอียด")
        Text(if (i.details.isBlank()) "-" else i.details, fontSize = 14.sp)
        Spacer(Modifier.height(12.dp))
        if (i.hasDate) {
            Button(onClick = onRenew, shape = RectangleShape, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("ต่ออายุ +1 ปี", fontWeight = FontWeight.SemiBold) }
        }
        OutlinedButton(
            onClick = onEdit, shape = RectangleShape, modifier = Modifier.fillMaxWidth().height(48.dp),
            border = BorderStroke(1.dp, INK), colors = ButtonDefaults.outlinedButtonColors(contentColor = INK)
        ) { Text("แก้ไข", fontWeight = FontWeight.SemiBold) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onDone) { Text(if (i.hasDate && i.every > 0) "เสร็จแล้ว (เลื่อนรอบถัดไป)" else "เสร็จแล้ว (เก็บถาวร)") }
            TextButton(onClick = onDelete) { Text("ลบ", color = RED) }
        }
    }
}

// ---------- ฟอร์มเพิ่ม/แก้ไข ----------
@Composable
fun FormScreen(initial: Item?, cats: List<String>, leads: List<Int>, onClose: () -> Unit, onSave: (Item) -> Unit) {
    val c = LocalContext.current
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var cat by remember { mutableStateOf(initial?.category ?: cats.firstOrNull() ?: "อื่นๆ") }
    var ui by remember { mutableStateOf(if (initial != null && !initial.hasDate) 4 else initial?.type ?: 0) }
    var day by remember { mutableStateOf(initial?.baseDay ?: LocalDate.now().toEpochDay()) }
    var months by remember { mutableStateOf(initial?.months ?: 12) }
    var repN by remember { mutableStateOf(initial?.every ?: 0) }
    var repUnit by remember { mutableStateOf(initial?.everyUnit ?: 1) }
    var remind by remember { mutableStateOf(initial?.customLeads ?: emptyList()) }
    var details by remember { mutableStateOf(initial?.details ?: "") }
    var widget by remember { mutableStateOf(initial?.widget ?: true) }
    var sheet by remember { mutableStateOf(0) } // 1 หมวด 2 ชนิดวัน 3 วันที่ 4 ครบกำหนด 5 เดือน 6 เดือนกำหนดเอง
    var scanR by remember { mutableStateOf<ScanResult?>(null) }
    var scanTick by remember { mutableStateOf(0) }

    val scanFile = remember { File(c.cacheDir, "scan.jpg") }
    val scanUri = remember { FileProvider.getUriForFile(c, c.packageName + ".fp", scanFile) }
    val takePic = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) {
            Toast.makeText(c, "กำลังอ่านฉลาก…", Toast.LENGTH_SHORT).show()
            Scan.run(c, scanUri, { r -> scanR = r; scanTick++ }, {
                Toast.makeText(c, "สแกนไม่สำเร็จ กรอกเองได้เลย", Toast.LENGTH_LONG).show()
            })
        }
    }

    val due = if (ui == 1 || ui == 2) LocalDate.ofEpochDay(day).plusMonths(months.toLong()).toEpochDay() else day
    val primary = MaterialTheme.colorScheme.primary
    val dateLabel = listOf("วันหมดอายุ", "วันที่เปิดใช้", "วันที่เริ่มประกัน", "วันที่ชำระ")[ui.coerceAtMost(3)]

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(start = 20.dp, top = 28.dp, end = 20.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(if (initial != null && initial.id > 0) "แก้ไขรายการ" else "เพิ่มรายการ", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            OutlinedButton(
                onClick = { takePic.launch(scanUri) }, shape = RectangleShape,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                border = BorderStroke(1.dp, INK), colors = ButtonDefaults.outlinedButtonColors(contentColor = INK)
            ) {
                CamIcon()
                Spacer(Modifier.width(8.dp))
                Text("สแกนฉลากด้วยกล้อง", fontWeight = FontWeight.SemiBold)
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Lab("ชื่อรายการ")
                OutlinedTextField(
                    name, { name = it }, placeholder = { Text("เช่น พ.ร.บ. รถ, ครีมกันแดด") },
                    shape = RectangleShape, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Select("หมวด", cat, Modifier.weight(1f)) { sheet = 1 }
                Select("ชนิดวัน", TYPE_SHORT[ui], Modifier.weight(1f)) { sheet = 2 }
            }

            if (ui == 0 || ui == 3) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Select(dateLabel, thai(day), Modifier.weight(1f), cal = true) { sheet = 3 }
                    Select("ครบกำหนด", repText(repN, repUnit), Modifier.weight(1f)) { sheet = 4 }
                }
            }
            if (ui == 1 || ui == 2) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Select(dateLabel, thai(day), Modifier.weight(1f), cal = true) { sheet = 3 }
                    Select(
                        if (ui == 1) "อายุหลังเปิด" else "ระยะประกัน",
                        if (ui == 1) "$months เดือน" else fmtMonths(months), Modifier.weight(1f)
                    ) { sheet = 5 }
                }
                Row(
                    Modifier.fillMaxWidth().border(1.dp, primary).padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(if (ui == 1) "จะหมดอายุ" else "หมดประกัน", color = primary)
                    Text(thai(due), color = primary, fontWeight = FontWeight.Bold)
                }
            }

            Select(
                "เตือนล่วงหน้า",
                if (remind.isEmpty()) "ตามค่าเริ่มต้น (" + fmtLeads(leads) + ")"
                else fmtLeads(remind),
                Modifier.fillMaxWidth()
            ) { sheet = 8 }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Lab("รายละเอียด")
                OutlinedTextField(
                    details, { details = it }, placeholder = { Text("ค่าใช้จ่าย โน้ตเพิ่มเติม") },
                    shape = RectangleShape, minLines = 3, modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                Modifier.fillMaxWidth().clickable { widget = !widget },
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                Text("แสดงใน widget", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Checkbox(checked = widget, onCheckedChange = { widget = it })
            }
        }
        Button(
            onClick = {
                if (name.isBlank()) {
                    Toast.makeText(c, "ใส่ชื่อรายการก่อน", Toast.LENGTH_SHORT).show()
                } else onSave(
                    Item(
                        id = initial?.id?.takeIf { it > 0 } ?: System.currentTimeMillis(),
                        name = name.trim(), category = cat, type = if (ui == 4) 0 else ui,
                        baseDay = day, months = months,
                        repeat = if ((ui == 0 || ui == 3) && repN > 0) 3 else 0,
                        repN = if (ui == 0 || ui == 3) repN else 0, repUnit = repUnit, remind = remind.joinToString(","), details = details,
                        done = initial?.done ?: false, hasDate = ui != 4,
                        widget = widget, doneAt = initial?.doneAt ?: 0L
                    )
                )
            },
            shape = RectangleShape,
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 12.dp).navigationBarsPadding().height(52.dp)
        ) { Text("บันทึก", fontWeight = FontWeight.SemiBold) }
    }

    when (sheet) {
        1 -> Sheet("เลือกหมวด", { sheet = 0 }) {
            (cats + "อื่นๆ").distinct().forEach { k -> OptionRow(k, selected = k == cat) { cat = k; sheet = 0 } }
            Hint("เพิ่มหรือแก้หมวดได้ในแท็บตั้งค่า")
        }
        2 -> Sheet("เลือกชนิดวัน", { sheet = 0 }) {
            TYPE_FULL.forEachIndexed { k, t -> OptionRow(t, TYPE_DESC[k], k == ui) { ui = k; sheet = 0 } }
        }
        3 -> CalendarSheet(day, { day = it; sheet = 0 }, { sheet = 0 })
        4 -> Sheet("ครบกำหนด", { sheet = 0 }) {
            PRESETS.forEach { (pn, pu) ->
                OptionRow(repText(pn, pu), selected = repText(repN, repUnit) == repText(pn, pu)) { repN = pn; repUnit = pu; sheet = 0 }
            }
            OptionRow("กำหนดเอง…") { sheet = 7 }
        }
        8 -> RemindSheet(remind, leads, { remind = it; sheet = 0 }, { sheet = 0 })
        7 -> RepeatDialog(repN, repUnit, { n, u -> repN = n; repUnit = u; sheet = 0 }, { sheet = 0 })
        5 -> MonthsSheet(
            if (ui == 1) "อายุหลังเปิด" else "ระยะประกัน",
            if (ui == 1) listOf(3, 6, 9, 12, 18, 24, 36) else listOf(6, 12, 24, 36, 60),
            months, { months = it; sheet = 0 }, { sheet = 6 }, { sheet = 0 }
        )
        6 -> Prompt(
            title = "จำนวนเดือน", initial = "", numeric = true,
            onOk = { v -> v.toIntOrNull()?.let { if (it in 1..120) months = it }; sheet = 0 },
            onClose = { sheet = 0 }
        )
    }

    scanR?.let { r ->
        BackHandler { scanR = null }
        Surface(Modifier.fillMaxSize(), color = Color.White) {
            ScanScreen(
                r, scanFile.path, scanTick, name, months,
                onRetake = { scanR = null; takePic.launch(scanUri) },
                onUse = { n, d, m ->
                    if (n.isNotBlank()) name = n
                    day = d; months = m; ui = 0
                    scanR = null
                }
            )
        }
    }
}

// ---------- หน้าผลสแกนฉลาก ----------
@Composable
fun ScanScreen(r: ScanResult, path: String, tick: Int, name0: String, months0: Int, onRetake: () -> Unit, onUse: (String, Long, Int) -> Unit) {
    var name by remember { mutableStateOf(name0) }
    var day by remember { mutableStateOf(r.day ?: LocalDate.now().toEpochDay()) }
    var months by remember { mutableStateOf(r.pao ?: months0) }
    var sheet by remember { mutableStateOf(0) }
    val bmp = remember(tick) {
        try {
            val o = BitmapFactory.Options().apply { inSampleSize = 4 }
            BitmapFactory.decodeFile(path, o)?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
    Column(
        Modifier.fillMaxSize().padding(start = 20.dp, top = 28.dp, end = 20.dp, bottom = 12.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("ผลสแกนฉลาก", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Box(Modifier.fillMaxWidth().height(150.dp).border(1.dp, BORDER), contentAlignment = Alignment.Center) {
            if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            else Text("ไม่มีภาพตัวอย่าง", color = GRAY, fontSize = 14.sp)
        }
        Text("ตรวจสอบก่อนบันทึก", fontSize = 14.sp, color = GRAY)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Lab("ชื่อสินค้า")
            OutlinedTextField(name, { name = it }, shape = RectangleShape, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Select("วันหมดอายุ", thai(day), Modifier.weight(1f), cal = true) { sheet = 1 }
            Select("อายุหลังเปิด (PAO)", "$months เดือน", Modifier.weight(1f)) { sheet = 2 }
        }
        Text(
            if (r.day == null) "อ่านวันที่ไม่ได้ เลือกวันเองได้เลย" else "ปีบนฉลากแปลงเป็น พ.ศ. ให้แล้ว กรุณาตรวจสอบ",
            fontSize = 14.sp, color = GRAY
        )
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRetake, shape = RectangleShape, modifier = Modifier.weight(1f).height(52.dp), border = BorderStroke(1.dp, INK), colors = ButtonDefaults.outlinedButtonColors(contentColor = INK)) { Text("ถ่ายใหม่", fontWeight = FontWeight.SemiBold) }
            Button(onClick = { onUse(name.trim(), day, months) }, shape = RectangleShape, modifier = Modifier.weight(1f).height(52.dp)) { Text("ใช้ค่านี้", fontWeight = FontWeight.SemiBold) }
        }
    }
    when (sheet) {
        1 -> CalendarSheet(day, { day = it; sheet = 0 }, { sheet = 0 })
        2 -> MonthsSheet("อายุหลังเปิด (PAO)", listOf(3, 6, 9, 12, 18, 24, 36), months, { months = it; sheet = 0 }, { sheet = 3 }, { sheet = 0 })
        3 -> Prompt(
            title = "จำนวนเดือน", initial = "", numeric = true,
            onOk = { v -> v.toIntOrNull()?.let { if (it in 1..120) months = it }; sheet = 0 },
            onClose = { sheet = 0 }
        )
    }
}

// ---------- ที่เก็บถาวร ----------
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
                TextButton(onClick = { onDelete(d) }) { Text("ลบ", color = RED) }
            }
        }
    }
}

// ---------- ตั้งค่า ----------
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
            leads.forEach { d -> Chip(fmtLead(d) + " ✕", true) { if (leads.size > 1) onLeads(leads - d) } }
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
        LeadDialog(
            onOk = { d -> onLeads((leads + d).distinct().sortedDescending()); leadPrompt = false },
            onClose = { leadPrompt = false }
        )
    }
}

// ครบกำหนดแบบสำเร็จรูป: (จำนวน, หน่วย) หน่วย 0 วัน 1 เดือน 2 ปี 3 สัปดาห์
val PRESETS = listOf(0 to 1, 1 to 3, 1 to 1, 3 to 1, 6 to 1, 1 to 2, 2 to 2)

@Composable
fun RepeatDialog(n0: Int, u0: Int, onOk: (Int, Int) -> Unit, onClose: () -> Unit) {
    var n by remember { mutableStateOf(if (n0 > 0) n0.toString() else "1") }
    var u by remember { mutableStateOf(u0) }
    AlertDialog(
        onDismissRequest = onClose, shape = RectangleShape,
        title = { Text("กำหนดเอง") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ทุก")
                    OutlinedTextField(
                        n, { n = it.filter { ch -> ch.isDigit() } }, singleLine = true, shape = RectangleShape,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(96.dp)
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("วัน" to 0, "สัปดาห์" to 3, "เดือน" to 1, "ปี" to 2).forEach { (t, k) -> Chip(t, u == k) { u = k } }
                }
            }
        },
        confirmButton = { TextButton(onClick = { val v = n.toIntOrNull() ?: 0; if (v in 1..999) onOk(v, u) }) { Text("ตกลง") } },
        dismissButton = { TextButton(onClick = onClose) { Text("ยกเลิก") } }
    )
}

@Composable
fun RemindSheet(current: List<Int>, defaults: List<Int>, onOk: (List<Int>) -> Unit, onClose: () -> Unit) {
    var useDefault by remember { mutableStateOf(current.isEmpty()) }
    var sel by remember { mutableStateOf(current) }
    var custom by remember { mutableStateOf(false) }
    val options = (LEAD_PRESETS + sel).distinct().sorted()
    Sheet("เตือนล่วงหน้า", onClose) {
        OptionRow("ตามค่าเริ่มต้น (" + fmtLeads(defaults) + ")", "ใช้ค่าจากแท็บตั้งค่า", useDefault) { useDefault = true }
        Lab("หรือเลือกเอง (เลือกได้หลายอัน)")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { d ->
                Chip(fmtLead(d), !useDefault && d in sel) {
                    if (useDefault) {
                        useDefault = false
                        sel = listOf(d)
                    } else {
                        sel = if (d in sel) sel - d else sel + d
                    }
                }
            }
            Chip("กำหนดเอง…") { custom = true }
        }
        Hint("วันครบกำหนดจะเตือนให้เสมอ")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onClose, shape = RectangleShape, modifier = Modifier.weight(1f).height(52.dp), border = BorderStroke(1.dp, INK), colors = ButtonDefaults.outlinedButtonColors(contentColor = INK)) { Text("ยกเลิก", fontWeight = FontWeight.SemiBold) }
            Button(onClick = { onOk(if (useDefault || sel.isEmpty()) emptyList() else sel.sortedDescending()) }, shape = RectangleShape, modifier = Modifier.weight(1f).height(52.dp)) { Text("ตกลง", fontWeight = FontWeight.SemiBold) }
        }
    }
    if (custom) {
        LeadDialog(
            onOk = { d ->
                sel = (sel + d).distinct()
                useDefault = false
                custom = false
            },
            onClose = { custom = false }
        )
    }
}

@Composable
fun LeadDialog(onOk: (Int) -> Unit, onClose: () -> Unit) {
    var n by remember { mutableStateOf("") }
    var u by remember { mutableStateOf(0) } // 0 วัน, 1 เดือน, 2 ปี
    AlertDialog(
        onDismissRequest = onClose, shape = RectangleShape,
        title = { Text("เตือนก่อนครบกำหนด") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    n, { n = it.filter { ch -> ch.isDigit() } }, singleLine = true, shape = RectangleShape,
                    placeholder = { Text("จำนวน") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(140.dp)
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("วัน" to 0, "เดือน" to 1, "ปี" to 2).forEach { (t, k) -> Chip(t, u == k) { u = k } }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val d = (n.toIntOrNull() ?: 0) * listOf(1, 30, 365)[u]
                if (d in 1..1100) onOk(d)
            }) { Text("ตกลง") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("ยกเลิก") } }
    )
}
