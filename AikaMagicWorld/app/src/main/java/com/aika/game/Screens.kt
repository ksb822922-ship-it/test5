package com.aika.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

private val bgBrush = Brush.verticalGradient(listOf(Color(0xFF2B1055), Color(0xFF7597DE)))

// ---------------------------------------------------------------- Menus

@Composable
fun MenuScreen(vm: GameViewModel) {
    Column(Modifier.fillMaxSize().background(bgBrush).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("🧙", fontSize = 72.sp)
        Text("Айка и\nМагический Мир", fontSize = 34.sp, fontWeight = FontWeight.Bold,
            color = Color.White, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("Кристаллов собрано: ${vm.progress.crystals}", color = Color(0xFFFFF59D))
        Spacer(Modifier.height(32.dp))
        Button(onClick = { vm.startLevel(minOf(vm.progress.levelsDone, Levels.all.size - 1)) },
            modifier = Modifier.fillMaxWidth(0.7f)) { Text("▶ Играть") }
        Spacer(Modifier.height(12.dp))
        Button(onClick = { vm.screen = Screen.LEVELS }, modifier = Modifier.fillMaxWidth(0.7f)) { Text("Выбор мира") }
    }
}

@Composable
fun LevelsScreen(vm: GameViewModel) {
    Column(Modifier.fillMaxSize().background(bgBrush).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Выбери мир", fontSize = 28.sp, color = Color.White, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Levels.all.forEachIndexed { i, lv ->
            val open = i <= vm.progress.levelsDone
            Button(onClick = { vm.startLevel(i) }, enabled = open, modifier = Modifier.fillMaxWidth(0.8f)) {
                Text((if (open) "" else "🔒 ") + "${lv.id}. ${lv.name}")
            }
            Spacer(Modifier.height(8.dp))
        }
        Button(onClick = { vm.screen = Screen.MENU }) { Text("← Назад") }
    }
}

// ---------------------------------------------------------------- Game screen

@Composable
fun GameScreen(vm: GameViewModel) {
    val s = vm.state ?: return
    var time by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) { withFrameMillis { time = it } } }

    Column(Modifier.fillMaxSize().background(bgBrush).statusBarsPadding()) {
        TopBar(vm, s)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Board(vm, s, time, Modifier.fillMaxSize())
            if (vm.banner.isNotEmpty()) {
                Text(vm.banner, color = Color.White, fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp)
                        .background(Color(0x99000000), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 4.dp))
            }
            if (s.status == Status.LOST || s.status == Status.LOOP) {
                Overlay {
                    Text(if (s.status == Status.LOST) "Айка потеряла все сердца 💔" else "Петля времени! Ходы закончились ⏳",
                        color = Color.White, fontSize = 20.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { vm.restart() }) { Text("Попробовать снова") }
                }
            }
            if (vm.paused) {
                Overlay {
                    Text("Пауза", color = Color.White, fontSize = 28.sp)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { vm.togglePause() }) { Text("Продолжить") }
                    Button(onClick = { vm.restart(); vm.paused = false }) { Text("Заново") }
                    Button(onClick = { vm.toMenu() }) { Text("В меню") }
                }
            }
            if (vm.dialog.isNotEmpty()) DialogBox(vm)
        }
        BottomBar(vm, s)
    }
}

@Composable
private fun Overlay(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(Color(0xAA000000))
        .pointerInput(Unit) { detectTapGestures { } },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, content = content)
}

@Composable
private fun DialogBox(vm: GameViewModel) {
    val line = vm.dialog[vm.dialogIdx]
    Box(Modifier.fillMaxSize().background(Color(0x66000000)).pointerInput(Unit) { detectTapGestures { } }) {
        Column(Modifier.align(Alignment.BottomCenter).padding(12.dp)
            .background(Color(0xFFFFF8E1), RoundedCornerShape(16.dp)).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(Color(0xFFD1C4E9)), contentAlignment = Alignment.Center) {
                    Text(line.icon, fontSize = 36.sp)
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(line.who, fontWeight = FontWeight.Bold, color = Color(0xFF4A148C))
                    Text(line.text, color = Color.Black, fontSize = 15.sp)
                }
            }
            Button(onClick = { vm.nextDialog() }, modifier = Modifier.align(Alignment.End)) { Text("Далее ▶") }
        }
    }
}

@Composable
private fun TopBar(vm: GameViewModel, s: GameState) {
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = { vm.togglePause() }, contentPadding = PaddingValues(8.dp)) { Text("⏸") }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text("Мир ${s.level.id}: ${s.level.name}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("❤".repeat(s.hearts) + "🖤".repeat(3 - s.hearts), fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.width(100.dp).height(12.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF1A237E))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(s.mana / 10f).background(Color(0xFF4FC3F7)))
                }
                Text(" ${s.mana}", color = Color(0xFF81D4FA), fontSize = 12.sp)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("💎 ${s.crystals}", color = Color(0xFFFFF59D), fontSize = 13.sp)
            if (s.level.moveLimit > 0)
                Text("⏳ ${s.moves}/${s.level.moveLimit}", color = Color.White, fontSize = 13.sp)
        }
    }
}

@Composable
private fun BottomBar(vm: GameViewModel, s: GameState) {
    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Spell.values().forEach { sp ->
            val unlocked = vm.hasSpell(sp)
            val active = sp == Spell.TELEPORT && vm.armed
            Button(onClick = { vm.cast(sp) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (!unlocked) Color(0xFF616161) else if (active) Color(0xFFFFB300) else Color(0xFF7E57C2))) {
                Text(if (unlocked) "${sp.icon}\n${sp.title} ${sp.cost}" else "🔒\n${sp.title}",
                    fontSize = 11.sp, textAlign = TextAlign.Center)
            }
        }
        Button(onClick = { vm.rotate(-1) }, contentPadding = PaddingValues(8.dp)) { Text("⟲") }
        Button(onClick = { vm.rotate(1) }, contentPadding = PaddingValues(8.dp)) { Text("⟳") }
    }
}

// ---------------------------------------------------------------- Board (2.5D canvas)

@Composable
private fun Board(vm: GameViewModel, s: GameState, time: Long, modifier: Modifier) {
    val rot = vm.rot
    val w = s.level.w
    val h = s.level.h
    val dw = if (rot % 2 == 0) w else h
    val dh = if (rot % 2 == 0) h else w

    // Logical -> display coordinates for the current camera rotation
    fun toDisp(p: Pos): Pos = when (rot) {
        0 -> p
        1 -> Pos(h - 1 - p.y, p.x)
        2 -> Pos(w - 1 - p.x, h - 1 - p.y)
        else -> Pos(p.y, w - 1 - p.x)
    }
    fun toLogic(d: Pos): Pos = when (rot) {
        0 -> d
        1 -> Pos(d.y, h - 1 - d.x)
        2 -> Pos(w - 1 - d.x, h - 1 - d.y)
        else -> Pos(w - 1 - d.y, d.x)
    }
    // Layout: cell size, row height (squashed for the side view), offsets
    fun lay(sw: Float, sh: Float): FloatArray {
        val cell = minOf(sw / dw, sh / (dh * 0.75f + 0.65f))
        val rowH = cell * 0.75f
        val totalH = rowH * dh + cell * 0.65f
        return floatArrayOf(cell, rowH, (sw - cell * dw) / 2f, (sh - totalH) / 2f + cell * 0.4f)
    }

    Canvas(modifier
        .pointerInput(rot, w, h) {
            detectTapGestures { off ->
                val l = lay(size.width.toFloat(), size.height.toFloat())
                val dx = floor((off.x - l[2]) / l[0]).toInt()
                val dy = floor((off.y - l[3]) / l[1]).toInt()
                if (dx in 0 until dw && dy in 0 until dh) vm.onTap(toLogic(Pos(dx, dy)))
            }
        }
        .pointerInput(Unit) {
            var acc = 0f
            detectHorizontalDragGestures(onDragEnd = {
                if (acc > 120f) vm.rotate(1) else if (acc < -120f) vm.rotate(-1)
                acc = 0f
            }) { _, amount -> acc += amount }
        }) {
        val l = lay(size.width, size.height)
        val cell = l[0]; val rowH = l[1]; val ox = l[2]; val oy = l[3]
        val lift = cell * 0.35f
        fun center(p: Pos): Offset { val d = toDisp(p); return Offset(ox + d.x * cell + cell / 2, oy + d.y * rowH + rowH / 2) }

        // ---- tiles, drawn back to front
        for (dy in 0 until dh) for (dx in 0 until dw) {
            val p = toLogic(Pos(dx, dy))
            val c = s.level.rows[p.y][p.x]
            val tl = Offset(ox + dx * cell, oy + dy * rowH)
            val sz = Size(cell, rowH)
            val grass = if ((p.x + p.y) % 2 == 0) Color(0xFF7CD66B) else Color(0xFF6BC85A)
            when (c) {
                '#' -> {
                    drawRect(grass, tl, sz)
                    drawRect(Color(0xFF9E9E9E), Offset(tl.x, tl.y - lift), sz)
                    drawRect(Color(0xFF616161), Offset(tl.x, tl.y - lift + rowH), Size(cell, lift))
                }
                '~' -> {
                    val wv = (sin(time / 400.0 + p.x + p.y) * 20).toInt()
                    drawRect(Color(0xFF1E6FD9).copy(alpha = 1f), tl, sz)
                    drawRect(Color(0x33FFFFFF), Offset(tl.x + cell * 0.1f, tl.y + rowH * 0.45f + wv * 0.1f), Size(cell * 0.8f, rowH * 0.1f))
                }
                'B' -> {
                    if (s.bridgeOpen) {
                        drawRect(Color(0xFF8D6E63), tl, sz)
                        for (i in 1..3) drawRect(Color(0xFF5D4037), Offset(tl.x, tl.y + rowH * i / 4f), Size(cell, 2f))
                    } else {
                        drawRect(Color(0xFF263238), tl, sz)
                        drawRect(Color(0x558D6E63), Offset(tl.x + cell * 0.2f, tl.y + rowH * 0.2f), Size(cell * 0.6f, rowH * 0.6f))
                    }
                }
                'D' -> {
                    drawRect(grass, tl, sz)
                    if (!s.doorOpen) {
                        drawRect(Color(0xFF8D6E63), Offset(tl.x, tl.y - lift), sz)
                        drawRect(Color(0xFF4E342E), Offset(tl.x, tl.y - lift + rowH), Size(cell, lift))
                    }
                }
                else -> drawRect(grass, tl, sz)
            }
            val ctr = Offset(tl.x + cell / 2, tl.y + rowH / 2)
            when (c) {
                'p' -> {
                    val done = p in s.painted
                    val col = if (done) Color.hsv(((time / 10 + p.x * 40) % 360).toFloat(), 0.6f, 1f) else Color(0xFFF8BBD0)
                    drawRect(col, Offset(tl.x + cell * 0.1f, tl.y + rowH * 0.1f), Size(cell * 0.8f, rowH * 0.8f))
                    if (!done) drawRect(Color.White, Offset(tl.x + cell * 0.1f, tl.y + rowH * 0.1f), Size(cell * 0.8f, rowH * 0.8f), style = Stroke(3f))
                }
                'c' -> if (p !in s.collected) {
                    val bob = (sin(time / 250.0) * cell * 0.04).toFloat()
                    val path = Path().apply {
                        moveTo(ctr.x, ctr.y - cell * 0.3f + bob); lineTo(ctr.x + cell * 0.2f, ctr.y + bob)
                        lineTo(ctr.x, ctr.y + cell * 0.25f + bob); lineTo(ctr.x - cell * 0.2f, ctr.y + bob); close()
                    }
                    drawPath(path, Color(0xFF4DD0E1)); drawPath(path, Color.White, style = Stroke(2f))
                }
                'P' -> {
                    val open = !(s.level.bossGoal && s.monsters.any { it.type == MType.BOSS })
                    for (i in 0 until 3) {
                        val r = cell * (0.4f - i * 0.1f) + (sin(time / 200.0 + i) * cell * 0.03).toFloat()
                        drawCircle(if (open) Color.hsv(((time / 8 + i * 60) % 360).toFloat(), 0.7f, 1f) else Color(0xFF616161),
                            r, ctr, style = Stroke(5f))
                    }
                }
                'L' -> { drawCircle(Color(0xFFFFD54F), cell * 0.3f, ctr); drawCircle(Color.White, cell * 0.12f, ctr) }
                'T' -> {
                    val hit = s.doorOpen
                    val path = Path().apply {
                        moveTo(ctr.x, ctr.y - cell * 0.35f); lineTo(ctr.x + cell * 0.3f, ctr.y)
                        lineTo(ctr.x, ctr.y + cell * 0.3f); lineTo(ctr.x - cell * 0.3f, ctr.y); close()
                    }
                    drawPath(path, if (hit) Color(0xFFFFEB3B) else Color(0xFFB71C1C))
                }
                'm', 'n' -> {
                    val slash = s.mirrors[p] ?: (c == 'm')
                    drawCircle(Color(0x55B3E5FC), cell * 0.38f, ctr)
                    val a = Offset(ctr.x - cell * 0.28f, if (slash) ctr.y + cell * 0.25f else ctr.y - cell * 0.25f)
                    val b = Offset(ctr.x + cell * 0.28f, if (slash) ctr.y - cell * 0.25f else ctr.y + cell * 0.25f)
                    drawLine(Color.White, a, b, strokeWidth = cell * 0.1f)
                }
            }
        }

        // ---- magic beam
        if (s.beam.isNotEmpty()) {
            var prev: Offset? = null
            for (y in 0 until s.level.h) for (x in 0 until s.level.w) if (s.level.rows[y][x] == 'L') prev = center(Pos(x, y))
            for (b in s.beam) {
                val c = center(b)
                if (prev != null) drawLine(Color(0xFFFFF176), prev, c, strokeWidth = cell * 0.12f)
                prev = c
            }
        }

        // ---- fx particles
        val now = System.currentTimeMillis()
        for (f in s.fx) {
            val age = (now - f.born) / 700f
            if (age < 0f || age > 1f) continue
            val c = center(f.pos)
            val col = when (f.kind) { 1 -> Color(0xFFFFD54F); 2 -> Color(0xFFFF7043); else -> Color(0xFFE53935) }
            for (i in 0 until 8) {
                val a = i * PI / 4
                val r = cell * (0.1f + age * 0.5f)
                drawCircle(col.copy(alpha = 1f - age), cell * 0.07f, Offset(c.x + (cos(a) * r).toFloat(), c.y + (sin(a) * r).toFloat()))
            }
        }

        // ---- monsters and Aika, sorted by display row so lower rows overlap upper ones
        val ents = mutableListOf<Pair<Int, () -> Unit>>()
        for (m in s.monsters) ents.add(toDisp(m.pos).y to { drawMonster(m, center(m.pos), cell, time) })
        ents.add(toDisp(s.aika).y to {
            val v = when (rot) {
                0 -> Pos(s.facing.dx, s.facing.dy); 1 -> Pos(-s.facing.dy, s.facing.dx)
                2 -> Pos(-s.facing.dx, -s.facing.dy); else -> Pos(s.facing.dy, -s.facing.dx)
            }
            drawAika(center(s.aika), cell, v, time, s.invis > 0)
        })
        ents.sortedBy { it.first }.forEach { it.second() }
    }
}

// ---------------------------------------------------------------- Chibi sprites drawn with code

/** Chibi Aika: dir is the display-space facing vector (4 directions). */
private fun DrawScope.drawAika(c: Offset, cell: Float, dir: Pos, time: Long, invis: Boolean) {
    val a = if (invis) 0.4f else 1f
    val r = cell * 0.4f
    val bob = (sin(time / 180.0) * r * 0.06).toFloat()
    val cy = c.y - r * 0.15f + bob
    drawOval(Color(0x44000000), Offset(c.x - r * 0.7f, c.y + r * 0.6f), Size(r * 1.4f, r * 0.4f))
    drawCircle(Color(0xFFEC407A).copy(alpha = a), r * 0.5f, Offset(c.x, cy + r * 0.6f))            // dress
    drawCircle(Color(0xFF6A1B9A).copy(alpha = a), r * 0.85f, Offset(c.x, cy - r * 0.1f))           // hair
    drawCircle(Color(0xFFFFE0B2).copy(alpha = a), r * 0.68f, Offset(c.x + dir.x * r * 0.08f, cy - r * 0.05f)) // face
    if (dir.y >= 0) {  // front/side view: draw eyes; back view: hair only
        val ex = dir.x * r * 0.2f
        drawCircle(Color(0xFF212121).copy(alpha = a), r * 0.09f, Offset(c.x - r * 0.25f + ex, cy))
        drawCircle(Color(0xFF212121).copy(alpha = a), r * 0.09f, Offset(c.x + r * 0.25f + ex, cy))
    } else {
        drawCircle(Color(0xFF6A1B9A).copy(alpha = a), r * 0.66f, Offset(c.x, cy - r * 0.05f))
    }
    val hat = Path().apply {                                                                          // witch hat
        moveTo(c.x - r * 0.8f, cy - r * 0.55f); lineTo(c.x, cy - r * 1.7f); lineTo(c.x + r * 0.8f, cy - r * 0.55f); close()
    }
    drawPath(hat, Color(0xFF311B92).copy(alpha = a))
    drawCircle(Color(0xFFFFD54F).copy(alpha = a), r * 0.12f, Offset(c.x, cy - r * 0.6f))
}

private fun DrawScope.drawMonster(m: Monster, c: Offset, cell: Float, time: Long) {
    val r = cell * 0.36f
    val bob = (sin(time / 200.0 + m.id) * r * 0.08).toFloat()
    drawOval(Color(0x44000000), Offset(c.x - r * 0.8f, c.y + r * 0.5f), Size(r * 1.6f, r * 0.4f))
    when (m.type) {
        MType.SLIME -> {
            drawOval(Color(0xFF66BB6A), Offset(c.x - r, c.y - r * 0.5f + bob), Size(r * 2f, r * 1.3f))
            drawCircle(Color.White, r * 0.2f, Offset(c.x - r * 0.35f, c.y + bob)); drawCircle(Color.White, r * 0.2f, Offset(c.x + r * 0.35f, c.y + bob))
            drawCircle(Color.Black, r * 0.09f, Offset(c.x - r * 0.35f, c.y + bob)); drawCircle(Color.Black, r * 0.09f, Offset(c.x + r * 0.35f, c.y + bob))
        }
        MType.BAT -> {
            val flap = (sin(time / 90.0) * r * 0.5).toFloat()
            val wing = Path().apply {
                moveTo(c.x, c.y); lineTo(c.x - r * 1.4f, c.y - r * 0.5f + flap); lineTo(c.x - r * 0.9f, c.y + r * 0.4f); close()
                moveTo(c.x, c.y); lineTo(c.x + r * 1.4f, c.y - r * 0.5f + flap); lineTo(c.x + r * 0.9f, c.y + r * 0.4f); close()
            }
            drawPath(wing, Color(0xFF7B1FA2)); drawCircle(Color(0xFF4A148C), r * 0.6f, Offset(c.x, c.y + bob))
            drawCircle(Color(0xFFFF5252), r * 0.1f, Offset(c.x - r * 0.2f, c.y + bob)); drawCircle(Color(0xFFFF5252), r * 0.1f, Offset(c.x + r * 0.2f, c.y + bob))
        }
        else -> {
            val k = if (m.type == MType.BOSS) 1.35f else 1f
            val rr = r * k
            if (m.type == MType.BOSS) {
                drawCircle(Color(0xFF4A148C), rr * 0.9f, Offset(c.x, c.y + rr * 0.5f))                // robe
                val hat = Path().apply { moveTo(c.x - rr * 0.8f, c.y - rr * 0.4f); lineTo(c.x, c.y - rr * 1.6f); lineTo(c.x + rr * 0.8f, c.y - rr * 0.4f); close() }
                drawPath(hat, Color(0xFF311B92))
            }
            drawCircle(Color(0xFFF5F5F5), rr * 0.7f, Offset(c.x, c.y - rr * 0.1f + bob))             // skull
            drawCircle(Color.Black, rr * 0.16f, Offset(c.x - rr * 0.28f, c.y - rr * 0.15f + bob))
            drawCircle(Color.Black, rr * 0.16f, Offset(c.x + rr * 0.28f, c.y - rr * 0.15f + bob))
            if (m.type == MType.BOSS) {                                                               // boss HP bar
                drawRect(Color(0xFF424242), Offset(c.x - rr, c.y - rr * 1.9f), Size(rr * 2f, 8f))
                drawRect(Color(0xFFE53935), Offset(c.x - rr, c.y - rr * 1.9f), Size(rr * 2f * m.hp / m.maxHp, 8f))
            }
        }
    }
    if (m.frozen > 0) drawCircle(Color(0x884FC3F7), r * 1.2f, Offset(c.x, c.y))                      // ice overlay
}
