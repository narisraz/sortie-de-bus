package com.narisraz.sortiedebus

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private class BusColor(val c: Color, val d: Color, val l: Color)

private val COLORS = listOf(
    BusColor(Color(0xFFF0453A), Color(0xFFB02A22), Color(0xFFFF8A80)), // rouge
    BusColor(Color(0xFF2F80ED), Color(0xFF1C56A8), Color(0xFF7FB3FF)), // bleu
    BusColor(Color(0xFF27AE60), Color(0xFF1A7A43), Color(0xFF7BE0A5)), // vert
    BusColor(Color(0xFFF7C325), Color(0xFFC4910B), Color(0xFFFFE27A)), // jaune
    BusColor(Color(0xFF9B51E0), Color(0xFF6B2FA6), Color(0xFFC99BFF)), // violet
    BusColor(Color(0xFFEC4F9C), Color(0xFFA8306B), Color(0xFFFF9CCB)), // rose
    BusColor(Color(0xFF1FC8DB), Color(0xFF108C9A), Color(0xFF8AF0FB)), // cyan
    BusColor(Color(0xFFF2802C), Color(0xFFB5561A), Color(0xFFFFB57F)), // orange
)

private val Sky = Color(0xFFCFE3F2)
private val Ink = Color(0xFF1D2A3A)
private val InkSoft = Color(0xFF4A5B70)
private val Accent = Color(0xFFFF8A1F)
private val AccentInk = Color(0xFF5A2A00)

@Composable
fun GameScreen(startLevel: Int, onLevel: (Int) -> Unit) {
    val game = remember { Game(startLevel) }
    var tick by remember { mutableLongStateOf(0L) }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current.density

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { t ->
                if (last != 0L) game.update((t - last) / 1e9)
                last = t
                tick = t
            }
        }
    }
    @Suppress("UNUSED_VARIABLE") val frame = tick // recompose HUD and overlays every frame

    fun go(level: Int) { game.start(level); onLevel(level) }

    Box(Modifier.fillMaxSize().background(Sky), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.safeDrawingPadding().widthIn(max = 540.dp).fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HudButton("↻", primary = false) { go(game.level) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("SORTIE DE BUS", color = InkSoft, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                    Text("Niveau ${game.level}", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                }
                HudButton("+1 place", primary = true, enabled = !game.bonusUsed) { game.useBonus() }
            }
            Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(22.dp))) {
                Canvas(
                    Modifier.fillMaxSize().pointerInput(Unit) {
                        detectTapGestures { o -> game.tapAt(o.x / density.toDouble(), o.y / density.toDouble()) }
                    }
                ) {
                    tick // redraw every frame
                    game.resize(size.width / density.toDouble(), size.height / density.toDouble())
                    withTransform({ scale(density, density, pivot = Offset.Zero) }) { drawGame(game, measurer) }
                }
                game.toast?.let {
                    Text(
                        it, color = Color.White, fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.align(Alignment.Center)
                            .background(Ink.copy(alpha = .92f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
                when (game.status) {
                    Status.WON -> Overlay(
                        "Bravo !", "${game.total} passagers transportés en ${game.elapsedSeconds()} s.",
                        listOf("Niveau suivant" to { go(game.level + 1) }),
                    )
                    Status.LOST -> Overlay(
                        "Bouchon !", "Plus de place libre et aucun bus pour le prochain passager.",
                        buildList {
                            if (!game.bonusUsed) add("Ajouter une place" to { game.useBonus(); Unit })
                            add("Réessayer" to { go(game.level) })
                        },
                    )
                    Status.PLAYING -> {}
                }
            }
        }
    }
}

@Composable
private fun HudButton(label: String, primary: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled, shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) Accent else Color.White,
            contentColor = if (primary) AccentInk else Ink,
        ),
    ) { Text(label, fontWeight = FontWeight.ExtraBold) }
}

@Composable
private fun Overlay(title: String, text: String, actions: List<Pair<String, () -> Unit>>) {
    Box(Modifier.fillMaxSize().background(Color(0x8C142337)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 300.dp).background(Color.White, RoundedCornerShape(22.dp)).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, color = Ink, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
            Text(text, color = InkSoft, textAlign = TextAlign.Center)
            actions.forEachIndexed { i, (label, action) ->
                Button(
                    onClick = action, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (i == 0) Accent else Color(0xFFEEF2F6),
                        contentColor = if (i == 0) AccentInk else Ink,
                    ),
                ) { Text(label, fontWeight = FontWeight.ExtraBold) }
            }
        }
    }
}

// ---------- Drawing (layout units = dp) ----------

private fun DrawScope.rr(color: Color, x: Double, y: Double, w: Double, h: Double, r: Double) {
    val rad = min(r, min(w / 2, h / 2)).toFloat()
    drawRoundRect(color, Offset(x.toFloat(), y.toFloat()), Size(w.toFloat(), h.toFloat()), CornerRadius(rad, rad))
}

private fun DrawScope.circle(color: Color, x: Double, y: Double, r: Double) =
    drawCircle(color, r.toFloat(), Offset(x.toFloat(), y.toFloat()))

private fun DrawScope.drawBus(x: Double, y: Double, ang: Double, len: Double, wid: Double, col: Int, seats: Boolean, cap: Int, riders: Int) {
    val p = COLORS[col]
    withTransform({
        translate(x.toFloat(), y.toFloat())
        rotate((ang * 180 / PI).toFloat(), pivot = Offset.Zero)
    }) {
        val r = wid * 0.28
        rr(Color(0x400F1928), -wid / 2 + wid * 0.08, -len / 2 + wid * 0.14, wid, len, r)
        rr(p.d, -wid / 2, -len / 2, wid, len, r)
        rr(p.c, -wid / 2, -len / 2, wid, len - wid * 0.1, r)
        val win = Color(0xD9D2EBFF)
        val n = max(2, Math.round(len / wid * 1.4).toInt())
        val gap = len * 0.62 / n
        for (i in 0 until n) {
            val yy = -len / 2 + len * 0.27 + i * gap
            rr(win, -wid / 2 + wid * 0.04, yy, wid * 0.09, gap * 0.7, 2.0)
            rr(win, wid / 2 - wid * 0.13, yy, wid * 0.09, gap * 0.7, 2.0)
        }
        rr(Color(0xFFD9EFFF), -wid / 2 + wid * 0.12, -len / 2 + wid * 0.07, wid * 0.76, wid * 0.24, wid * 0.08)
        rr(Color(0xB3FFFFFF), -wid / 2 + wid * 0.18, -len / 2 + wid * 0.1, wid * 0.22, wid * 0.08, 2.0)
        rr(p.l, -wid * 0.3, -len / 2 + wid * 0.42, wid * 0.6, len - wid * 0.62, wid * 0.14)
        if (seats) {
            val rows2 = cap / 2
            val top = -len / 2 + wid * 0.5
            val step = (len - wid * 0.78) / rows2
            val rad = min(wid * 0.13, step * 0.32)
            for (i in 0 until cap) {
                val cx = (if (i % 2 == 0) -1 else 1) * wid * 0.14
                val cy = top + step * (i / 2 + 0.5)
                circle(if (i < riders) Color.White else Color(0x38000000), cx, cy, rad)
                if (i < riders) circle(p.d, cx, cy, rad * 0.5)
            }
        } else {
            val aw = wid * 0.42
            val ah = min(len * 0.55, wid * 1.2)
            val cy = wid * 0.12
            val path = Path().apply {
                moveTo(0f, (cy - ah / 2).toFloat())
                lineTo((aw / 2).toFloat(), (cy - ah / 2 + aw * 0.55).toFloat())
                lineTo((aw * 0.17).toFloat(), (cy - ah / 2 + aw * 0.55).toFloat())
                lineTo((aw * 0.17).toFloat(), (cy + ah / 2).toFloat())
                lineTo((-aw * 0.17).toFloat(), (cy + ah / 2).toFloat())
                lineTo((-aw * 0.17).toFloat(), (cy - ah / 2 + aw * 0.55).toFloat())
                lineTo((-aw / 2).toFloat(), (cy - ah / 2 + aw * 0.55).toFloat())
                close()
            }
            drawPath(path, Color.White)
        }
        rr(Color(0xFFFFF6C2), -wid / 2 + wid * 0.1, -len / 2 + 1, wid * 0.14, wid * 0.05, 0.0)
        rr(Color(0xFFFFF6C2), wid / 2 - wid * 0.24, -len / 2 + 1, wid * 0.14, wid * 0.05, 0.0)
    }
}

private fun DrawScope.drawPerson(x: Double, yIn: Double, s: Double, col: Int, lift: Double) {
    val p = COLORS[col]
    drawOval(
        Color(0x2E000000),
        Offset((x - s * 0.75).toFloat(), (yIn + s * 0.8).toFloat()),
        Size((s * 1.5).toFloat(), (s * 0.5).toFloat()),
    )
    val y = yIn - lift
    rr(p.d, x - s * 0.62, y - s * 0.05, s * 1.24, s * 1.1, s * 0.5)
    rr(p.c, x - s * 0.55, y - s * 0.05, s * 1.1, s * 0.95, s * 0.45)
    circle(p.c, x, y - s * 0.55, s * 0.48)
    circle(Color(0x8CFFFFFF), x - s * 0.15, y - s * 0.7, s * 0.14)
}

private fun DrawScope.drawGame(g: Game, measurer: TextMeasurer) {
    val L = g.L
    val W = L.w; val H = L.h
    val now = g.time
    // station sidewalk
    rr(Color(0xFFE8EEF4), 0.0, 0.0, W, L.sY0, 0.0)
    var gx = 0.0
    while (gx < W) {
        drawLine(Color(0x1F506482), Offset(gx.toFloat(), 0f), Offset(gx.toFloat(), L.sY0.toFloat()), 1f); gx += 18
    }
    drawLine(Color(0xFFB8C4D2), Offset(10f, (L.qY + 13).toFloat()), Offset((W - 10).toFloat(), (L.qY + 13).toFloat()), 3f, StrokeCap.Round)
    // bus bay
    rr(Color(0xFF4B5565), 0.0, L.sY0, W, L.sH, 0.0)
    rr(Color(0xFF5A6576), 0.0, L.sY0, W, 4.0, 0.0)
    // parking lot
    val lotTop = L.sY0 + L.sH
    rr(Color(0xFFB9C3CF), 0.0, lotTop, W, H - lotTop, 0.0)
    var mx = 6.0
    while (mx < W) { rr(Color(0xFFF2C94C), mx, lotTop + 4, 14.0, 4.0, 0.0); mx += 26 }
    rr(Color(0xFFCFD7E0), L.gx0 - 4, L.gy0 - 4, L.gx1 - L.gx0 + 8, L.gy1 - L.gy0 + 8, 10.0)
    val line = Color(0xBFFFFFFF)
    for (c in 0..g.cols) {
        val x = (L.gx0 + c * L.cs).toFloat()
        drawLine(line, Offset(x, L.gy0.toFloat()), Offset(x, L.gy1.toFloat()), 1.5f)
    }
    for (r in 0..g.rows) {
        val y = (L.gy0 + r * L.cs).toFloat()
        drawLine(line, Offset(L.gx0.toFloat(), y), Offset(L.gx1.toFloat(), y), 1.5f)
    }
    // slots
    for (j in 0 until MAX_SLOTS) {
        val s = L.slotCenter(j)
        val x = s.x - L.slotW / 2; val y = L.sY0 + 8; val h = L.sH - 16
        if (j < g.active) {
            rr(Color(0xFF3A4250), x, y, L.slotW, h, 8.0)
            drawRoundRect(
                Color(0x8CFFFFFF), Offset((x + 3).toFloat(), (y + 3).toFloat()),
                Size((L.slotW - 6).toFloat(), (h - 6).toFloat()), CornerRadius(6f, 6f),
                style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f))),
            )
        } else {
            rr(Color(0x1AFFFFFF), x, y, L.slotW, h, 8.0)
            val lock = Color(0xFFD4DAE3)
            drawArc(
                lock, 180f, 180f, false, Offset((s.x - 7).toFloat(), (s.y - 13).toFloat()), Size(14f, 14f),
                style = Stroke(3f),
            )
            rr(lock, s.x - 10, s.y - 6, 20.0, 15.0, 3.0)
            label(measurer, "+1", s.x, s.y + 18, lock, 12, center = true)
        }
    }
    // parked buses
    for (b in g.buses) if (b.state == BusState.PARK) {
        val p = g.parkPose(b)
        val o = g.parkOffset(b)
        drawBus(p.x + o.x, p.y + o.y, p.ang, p.len, p.wid, b.color, false, 0, 0)
    }
    // buses waiting in slots
    for (b in g.buses) if (b.state == BusState.SLOT) {
        val s = L.slotCenter(b.slot)
        val (len, wid) = g.slotDims(b)
        val sh = if (b.shake > 0) sin(now * 1000 / 22) * 3 else 0.0
        drawBus(s.x + sh, s.y, b.ang, len, wid, b.color, true, b.cap, b.riders)
    }
    // moving buses on top
    for (b in g.buses) if (b.state == BusState.DRIVE || b.state == BusState.LEAVE) {
        val (sl, sw) = g.slotDims(b)
        if (b.state == BusState.DRIVE) {
            val e = b.k * b.k
            drawBus(b.x, b.y, b.ang, b.fromLen + (sl - b.fromLen) * e, b.fromWid + (sw - b.fromWid) * e, b.color, false, 0, 0)
        } else {
            drawBus(L.slotCenter(b.slot).x, b.y, b.ang, sl, sw, b.color, true, b.cap, b.riders)
        }
    }
    // queue
    val sp = L.queueSpacing(); val ps = sp * 0.42
    val maxShown = ((W - 60) / sp).toInt()
    val n = min(g.queue.size, maxShown)
    for (i in n - 1 downTo 0) {
        val lift = if (i == 0) abs(sin(now * 1000 / 160)) * 3 else 0.0
        drawPerson(g.queueX(i), L.qY, ps, g.queue[i], lift)
    }
    if (g.queue.size > n) label(measurer, "+${g.queue.size - n}", W - 10, L.qY - 8, InkSoft, 13, center = false)
    for (f in g.flying) {
        val s = L.slotCenter(f.to.slot)
        val k = min(1.0, f.t / f.dur)
        val x = f.x0 + (s.x - f.x0) * k
        val y = f.y0 + (s.y - f.y0) * k - sin(PI * k) * 26
        drawPerson(x, y, ps * (1 - 0.35 * k), f.color, 0.0)
    }
    // progress bar
    val done = g.total - g.queue.size
    val bw = W - 20
    rr(Color(0x1F1D2A3A), 10.0, 4.0, bw, 5.0, 3.0)
    rr(Accent, 10.0, 4.0, max(5.0, bw * done / max(1, g.total)), 5.0, 3.0)
}

private fun DrawScope.label(m: TextMeasurer, text: String, x: Double, y: Double, color: Color, size: Int, center: Boolean) {
    val res = m.measure(text, TextStyle(color = color, fontSize = (size / density / fontScale).sp, fontWeight = FontWeight.ExtraBold))
    val left = if (center) x - res.size.width / 2.0 else x - res.size.width
    drawText(res, topLeft = Offset(left.toFloat(), y.toFloat()))
}
