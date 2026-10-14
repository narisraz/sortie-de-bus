package com.narisraz.sortiedebus

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.SharedPreferences
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
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
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private fun rgb(hex: Int, a: Double = 1.0) =
    Color(((hex shr 16) and 255) / 255f, ((hex shr 8) and 255) / 255f, (hex and 255) / 255f, a.toFloat())

private class BusColor(val ch: Int, val dh: Int, val lh: Int) {
    val c = rgb(ch); val d = rgb(dh); val l = rgb(lh)
}

private val COLORS = listOf(
    BusColor(0xF0453A, 0xB02A22, 0xFF8A80), // rouge
    BusColor(0x2F80ED, 0x1C56A8, 0x7FB3FF), // bleu
    BusColor(0x27AE60, 0x1A7A43, 0x7BE0A5), // vert
    BusColor(0xF7C325, 0xC4910B, 0xFFE27A), // jaune
    BusColor(0x9B51E0, 0x6B2FA6, 0xC99BFF), // violet
    BusColor(0xEC4F9C, 0xA8306B, 0xFF9CCB), // rose
    BusColor(0x1FC8DB, 0x108C9A, 0x8AF0FB), // cyan
    BusColor(0xF2802C, 0xB5561A, 0xFFB57F), // orange
)

private val Sky = Color(0xFFCFE3F2)
private val Ink = Color(0xFF1D2A3A)
private val InkSoft = Color(0xFF4A5B70)
private val Accent = Color(0xFFFF8A1F)
private val AccentInk = Color(0xFF5A2A00)
private val MYSTERY = BusColor(0x8D96A3, 0x5F6875, 0xB7BFCA)
private val SKIN = BusColor(0xFFE0C4, 0xD79A6E, 0xFFE0C4)

/** Today's challenge seed: the local date as yyyymmdd (e.g. 20261014). */
private fun dailySeed(): Int {
    val d = java.time.LocalDate.now()
    return d.year * 10000 + d.monthValue * 100 + d.dayOfMonth
}

private fun starsText(n: Int) = "★".repeat(n) + "☆".repeat(3 - n)

private fun missesText(n: Int) = when (n) { 0 -> "aucun coup raté"; 1 -> "1 coup raté"; else -> "$n coups ratés" }

/** Best stars per level are stored as "stars_N"; the total is shown in the HUD. */
private fun starsTotal(prefs: SharedPreferences) =
    prefs.all.filterKeys { it.startsWith("stars_") }.values.sumOf { (it as? Int) ?: 0 }

@Composable
fun GameScreen(prefs: SharedPreferences) {
    fun savedLevel() = prefs.getInt("level", 1).coerceAtLeast(1)
    var total by remember { mutableIntStateOf(starsTotal(prefs)) }
    val game = remember {
        Game(savedLevel()).also { g ->
            g.onWin = {
                if (!g.daily) {
                    val key = "stars_${g.level}"
                    val best = max(prefs.getInt(key, 0), g.stars())
                    prefs.edit().putInt("level", g.level + 1).putInt(key, best).apply()
                    total = starsTotal(prefs)
                }
            }
        }
    }
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

    fun go(level: Int) = game.start(level)
    fun restart() = if (game.daily) game.start(game.level, dailySeed()) else game.start(game.level)

    Box(Modifier.fillMaxSize().background(Sky), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.safeDrawingPadding().widthIn(max = 540.dp).fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HudButton("↻", primary = false) { restart() }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("SORTIE DE BUS · ★ $total", color = InkSoft, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, maxLines = 1)
                    Text(
                        if (game.daily) "Défi du jour" else "Niveau ${game.level}",
                        color = Ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false,
                    )
                }
                HudButton(if (game.daily) "Niveaux" else "Défi", primary = false) {
                    if (game.daily) go(savedLevel()) else game.start(game.level, dailySeed())
                }
                Spacer(Modifier.width(6.dp))
                HudButton("+1", primary = true, enabled = !game.bonusUsed) { game.useBonus() }
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
                        "Bravo !", "${game.total} passagers en ${game.elapsedSeconds()} s, ${missesText(game.misses)}.",
                        listOf(if (game.daily) "Retour aux niveaux" to { go(savedLevel()) } else "Niveau suivant" to { go(game.level + 1) }),
                        stars = game.stars(),
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
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) Accent else Color.White,
            contentColor = if (primary) AccentInk else Ink,
        ),
    ) { Text(label, fontWeight = FontWeight.ExtraBold) }
}

@Composable
private fun Overlay(title: String, text: String, actions: List<Pair<String, () -> Unit>>, stars: Int? = null) {
    Box(Modifier.fillMaxSize().background(Color(0x8C142337)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 300.dp).background(Color.White, RoundedCornerShape(22.dp)).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, color = Ink, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
            if (stars != null) Text(starsText(stars), color = Accent, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 4.sp)
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

// ---------- Drawing (layout units = dp). Mirrors the canvas drawing in index.html. ----------

private fun Double.f() = toFloat()

private fun rrPath(x: Double, y: Double, w: Double, h: Double, r: Double): Path {
    val rad = max(0.0, min(r, min(w / 2, h / 2))).f()
    return Path().apply { addRoundRect(RoundRect(x.f(), y.f(), (x + w).f(), (y + h).f(), CornerRadius(rad, rad))) }
}

private fun DrawScope.rr(color: Color, x: Double, y: Double, w: Double, h: Double, r: Double) =
    drawPath(rrPath(x, y, w, h, r), color)

private fun DrawScope.circle(color: Color, x: Double, y: Double, r: Double) =
    drawCircle(color, r.f(), Offset(x.f(), y.f()))

private fun DrawScope.line(color: Color, x0: Double, y0: Double, x1: Double, y1: Double, w: Double) =
    drawLine(color, Offset(x0.f(), y0.f()), Offset(x1.f(), y1.f()), w.f())

private fun linear(vararg stops: Pair<Float, Color>, x0: Double, y0: Double, x1: Double, y1: Double) =
    Brush.linearGradient(*stops, start = Offset(x0.f(), y0.f()), end = Offset(x1.f(), y1.f()))

private fun poly(vararg p: Double) = Path().apply {
    moveTo(p[0].f(), p[1].f())
    for (i in 2 until p.size step 2) lineTo(p[i].f(), p[i + 1].f())
    close()
}

/** Bus-local drawing: origin at (x, y), rotated by `ang` (radians). */
private fun DrawScope.at(x: Double, y: Double, ang: Double, block: DrawScope.() -> Unit) =
    withTransform({ translate(x.f(), y.f()); rotate((ang * 180 / PI).f(), pivot = Offset.Zero) }, block)

/** Fills `path` with a soft drop shadow; the offset is in screen space like a canvas shadow. */
private fun DrawScope.shadowed(path: Path, fill: Color, shadow: Color, radius: Double, dx: Double, dy: Double) {
    drawIntoCanvas { cv ->
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = fill.toArgb()
            setShadowLayer(radius.f(), dx.f(), dy.f(), shadow.toArgb())
        }
        cv.nativeCanvas.drawPath(path.asAndroidPath(), p)
    }
}

/** Lightens (k > 0) or darkens (k < 0) a colour, like `shade` in index.html. */
private fun shade(hex: Int, k: Double): Color {
    fun f(c: Int): Double = max(0.0, min(255.0, Math.round(c + if (k < 0) c * k else (255 - c) * k).toDouble()))
    return Color((f((hex shr 16) and 255) / 255).f(), (f((hex shr 8) and 255) / 255).f(), (f(hex and 255) / 255).f())
}

/** Tileable noise texture drawn with the game's RNG (seed 7), like `noisePattern` in index.html. */
private fun noiseBrush(base: Int, spread: Double, size: Int, dots: Int): ShaderBrush {
    val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    c.drawColor(base or (0xFF shl 24))
    val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    val rng = Rng(7)
    repeat(dots) {
        val v = floor((rng.next() - 0.5) * spread).toInt()
        p.color = if (v > 0) android.graphics.Color.argb(v, 255, 255, 255) else android.graphics.Color.argb(-v, 0, 0, 0)
        val x = rng.next() * size; val y = rng.next() * size
        val w = 1 + rng.next() * 1.5; val h = 1 + rng.next() * 1.5
        c.drawRect(x.f(), y.f(), (x + w).f(), (y + h).f(), p)
    }
    return ShaderBrush(ImageShader(bmp.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
}

private val ASPHALT by lazy { noiseBrush(0x59636F, 46.0, 128, 2600) }
private val PAVING by lazy { noiseBrush(0xE6ECF2, 22.0, 96, 900) }

private fun footprint(x: Double, y: Double, ang: Double, len: Double, wid: Double, r: Double): Path {
    val ap = android.graphics.Path(rrPath(-wid / 2, -len / 2, wid, len, r).asAndroidPath())
    ap.transform(android.graphics.Matrix().apply { setRotate((ang * 180 / PI).f()); postTranslate(x.f(), y.f()) })
    return ap.asComposePath()
}

/** Window glass with a highlight; returns the highlight path (the web version strokes it for some models). */
private fun DrawScope.glass(x: Double, y: Double, w: Double, h: Double, rad: Double, dark: Boolean = false): Path {
    drawPath(rrPath(x, y, w, h, rad), linear(0f to rgb(if (dark) 0x4D6F93 else 0x9FD2FF), 1f to rgb(if (dark) 0x16222F else 0x2C4560), x0 = 0.0, y0 = y, x1 = 0.0, y1 = y + h))
    val hl = rrPath(x + w * 0.1, y + h * 0.15, w * 0.26, max(1.5, h * 0.22), 2.0)
    drawPath(hl, Color.White.copy(alpha = 0.5f))
    return hl
}

private fun DrawScope.hood(len: Double, wid: Double, p: BusColor, r: Double, depth: Double) {
    val front = -len / 2
    rr(shade(p.ch, -0.14), -wid / 2 + 1, front + 1, wid - 2, wid * depth, r * 0.8)
    val st = rgb(0, 0.18)
    line(st, -wid * 0.2, front + wid * depth * 0.35, wid * 0.2, front + wid * depth * 0.35, 1.0)
    line(st, -wid / 2 + 2, front + wid * depth, wid / 2 - 2, front + wid * depth, 1.0)
}

/** Roof details per model, in bus-local coords (front = -len/2); `seated` hides roof clutter so riders show. */
private fun DrawScope.roofDetails(model: String, len: Double, wid: Double, p: BusColor, r: Double, seated: Boolean) {
    val front = -len / 2; val back = len / 2
    when (model) {
        "minibus" -> {
            hood(len, wid, p, r, 0.24)
            glass(-wid * 0.38, front + wid * 0.28, wid * 0.76, wid * 0.2, wid * 0.07)
            if (!seated) {
                val st = rgb(0x1E2430, 0.55); val lw = max(1.5, wid * 0.04)
                val y0 = front + wid * 0.62; val y1 = back - wid * 0.18
                for (sx in listOf(-1.0, 1.0)) line(st, sx * wid * 0.3, y0, sx * wid * 0.3, y1, lw)
                for (k in 0 until 4) { val yy = y0 + (y1 - y0) * k / 3; line(st, -wid * 0.3, yy, wid * 0.3, yy, lw) }
            }
        }
        "scolaire" -> {
            hood(len, wid, p, r, 0.3)
            glass(-wid * 0.4, front + wid * 0.33, wid * 0.8, wid * 0.18, wid * 0.04)
            for (sx in listOf(-1.0, 1.0)) rr(rgb(0x1E2430), sx * wid * 0.41 - wid * 0.035, front + wid * 0.58, wid * 0.07, len - wid * 0.7, 0.0)
            for (sx in listOf(-1.0, 1.0)) circle(rgb(0xFFB020), sx * wid * 0.32, front + wid * 0.56, wid * 0.05)
            if (!seated) rr(Color.White.copy(alpha = 0.22f), -wid * 0.15, back - wid * 0.42, wid * 0.3, wid * 0.22, 3.0)
        }
        "navette" -> {
            glass(-wid * 0.4, front + wid * 0.05, wid * 0.8, wid * 0.3, wid * 0.14)
            if (!seated) {
                val hl = glass(-wid * 0.27, front + wid * 0.5, wid * 0.54, len - wid * 0.82, wid * 0.12, dark = true)
                drawPath(hl, Color.White.copy(alpha = 0.3f), style = Stroke(1f))
            }
        }
        "articule" -> {
            glass(-wid * 0.38, front + wid * 0.06, wid * 0.76, wid * 0.24, wid * 0.08)
            val panel = Color.White.copy(alpha = 0.18f)
            rr(panel, -wid * 0.3, front + wid * 0.42, wid * 0.6, len * 0.5 - wid * 0.62, wid * 0.12)
            rr(panel, -wid * 0.3, len * 0.08 + wid * 0.24, wid * 0.6, back - len * 0.08 - wid * 0.44, wid * 0.12)
            val jy = len * 0.08 - wid * 0.06; val jh = wid * 0.3
            rr(rgb(0x3A4250), -wid / 2, jy - jh / 2, wid, jh, 0.0)
            for (k in 1 until 5) { val yy = jy - jh / 2 + jh * k / 5; line(Color.White.copy(alpha = 0.25f), -wid / 2, yy, wid / 2, yy, 1.0) }
        }
        "imperiale" -> {
            glass(-wid * 0.4, front + wid * 0.05, wid * 0.8, wid * 0.16, wid * 0.06)
            if (!seated) {
                val y0 = front + wid * 0.32; val y1 = back - wid * 0.12
                val deck = rrPath(-wid * 0.42, y0, wid * 0.84, y1 - y0, wid * 0.12)
                drawPath(deck, rgb(0x2B3340))
                drawPath(deck, Color.White.copy(alpha = 0.75f), style = Stroke(1.2f))
                val n = floor((y1 - y0 - wid * 0.1) / (wid * 0.22)).toInt()
                for (k in 0 until n) for (sx in listOf(-1.0, 1.0)) {
                    rr(p.d, sx * wid * 0.2 - wid * 0.13, y0 + wid * 0.08 + k * wid * 0.22, wid * 0.26, wid * 0.13, 2.0)
                }
            }
        }
        "autocar" -> {
            glass(-wid * 0.42, front + wid * 0.04, wid * 0.84, wid * 0.34, wid * 0.16, dark = true)
            drawPath(poly(-wid / 2, len * 0.05, wid / 2, -len * 0.08, wid / 2, -len * 0.01, -wid / 2, len * 0.12), Color.White.copy(alpha = 0.85f))
            if (!seated) glass(-wid * 0.08, front + wid * 0.5, wid * 0.16, len - wid * 0.75, wid * 0.08, dark = true)
        }
        else -> { // urbain
            glass(-wid * 0.38, front + wid * 0.06, wid * 0.76, wid * 0.24, wid * 0.08)
            val panel = rrPath(-wid * 0.3, front + wid * 0.42, wid * 0.6, len - wid * 0.62, wid * 0.14)
            drawPath(panel, Color.White.copy(alpha = 0.18f))
            drawPath(panel, rgb(0, 0.12), style = Stroke(1f))
            if (!seated) {
                rr(shade(p.lh, 0.35), -wid * 0.26, back - wid * 0.62, wid * 0.52, wid * 0.4, 3.0)
                for (k in 1 until 4) {
                    val xx = -wid * 0.26 + wid * 0.52 * k / 4
                    line(rgb(0, 0.18), xx, back - wid * 0.58, xx, back - wid * 0.26, 1.0)
                }
            }
        }
    }
}

private fun DrawScope.head(x: Double, y: Double, rad: Double, p: BusColor) {
    val brush = Brush.radialGradient(0.08f to p.l, 1f to p.d, center = Offset((x - rad * 0.35).f(), (y - rad * 0.4).f()), radius = (rad * 1.2).f())
    drawCircle(brush, rad.f(), Offset(x.f(), y.f()))
}

/** Polished 2D bus: soft shadow, wheels, extruded walls with a window band, lit roof, model details. */
private fun DrawScope.drawBus(
    x: Double, y: Double, ang: Double, len: Double, wid: Double, col: Int, model: String, m: TextMeasurer,
    seats: Boolean = false, cap: Int = 0, riders: Int = 0, ice: Int = 0,
) {
    val p = if (col < 0) MYSTERY else COLORS[col]
    val h = max(4.0, wid * 0.24); val r = wid * 0.26
    // soft shadow (the web canvas shadow is in device pixels; these values match it at 2x)
    shadowed(footprint(x, y, ang, len * 0.96, wid * 0.94, r), rgb(0x0A121E, 0.35), rgb(0x0A121E, 0.45), wid * 0.2, wid * 0.06, (h + wid * 0.12) / 2)
    // wheels
    at(x, y + h, ang) {
        for (fy in listOf(-len / 2 + wid * 0.32, len / 2 - wid * 0.42)) for (sx in listOf(-1.0, 1.0)) {
            rr(rgb(0x1E2430), sx * wid / 2 - wid * 0.09, fy, wid * 0.18, wid * 0.26, 3.0)
        }
    }
    // extruded walls: body colour, dark window band, darker skirt
    val steps = kotlin.math.ceil(h).toInt(); val wallTop = shade(p.dh, -0.18)
    for (k in steps downTo 1) {
        val f = k.toDouble() / steps
        drawPath(footprint(x, y + k, ang, len, wid, r), if (f > 0.72) wallTop else if (f > 0.3) rgb(0x22303F) else p.d)
    }
    drawPath(footprint(x, y + h * 0.5, ang, len, wid, r), Color(160 / 255f, 210 / 255f, 1f, 0.35f), style = Stroke(1f))

    at(x, y, ang) {
        drawPath(rrPath(-wid / 2, -len / 2, wid, len, r), linear(0f to p.l, 0.35f to p.c, 1f to shade(p.ch, -0.12), x0 = -wid / 2, y0 = 0.0, x1 = wid / 2, y1 = 0.0))
        roofDetails(model, len, wid, p, r, seats)
        drawPath(rrPath(-wid / 2 + 1, -len / 2 + 1, wid - 2, len - 2, r), Color.White.copy(alpha = 0.35f), style = Stroke(1.2f))
        if (seats) {
            val rows2 = cap / 2
            val top = -len / 2 + wid * 0.5; val step = (len - wid * 0.78) / rows2; val rad = min(wid * 0.14, step * 0.34)
            for (i in 0 until cap) {
                val cx = (if (i % 2 == 1) 1 else -1) * wid * 0.15; val cy = top + step * (i / 2 + 0.5)
                if (i < riders) head(cx, cy, rad, p) else circle(rgb(0, 0.2), cx, cy, rad)
            }
        } else {
            val aw = wid * 0.44; val ah = min(len * 0.55, wid * 1.2); val cy = wid * 0.14
            val arrow = poly(
                0.0, cy - ah / 2, aw / 2, cy - ah / 2 + aw * 0.55, aw * 0.17, cy - ah / 2 + aw * 0.55,
                aw * 0.17, cy + ah / 2, -aw * 0.17, cy + ah / 2, -aw * 0.17, cy - ah / 2 + aw * 0.55, -aw / 2, cy - ah / 2 + aw * 0.55,
            )
            // shadow offset straight down on screen, expressed in bus-local coords
            shadowed(arrow, Color.White, rgb(0, 0.3), 1.5, 0.75 * sin(ang), 0.75 * cos(ang))
        }
    }
    // Badges at the back of the bus are drawn upright whatever the bus direction.
    fun upright(by: Double) = Pair(x - by * sin(ang), y + by * cos(ang))
    if (col < 0) {
        val (cx, cy) = upright(len / 2 - wid * 0.42)
        centered(m, "?", cx, cy, Color.White, (wid * 0.44).roundToInt().toDouble())
    }
    at(x, y, ang) { for (sx in listOf(-1.0, 1.0)) circle(rgb(0xFFF6C2), sx * wid * 0.34, -len / 2 + 1.5, wid * 0.06) }
    if (ice > 0) {
        at(x, y, ang) {
            val shell = rrPath(-wid / 2 - 3, -len / 2 - 3, wid + 6, len + 6 + h, wid * 0.3)
            drawPath(shell, linear(0f to rgb(0xEBFAFF, 0.7), 0.5f to rgb(0xBEE6FF, 0.35), 1f to rgb(0xEBFAFF, 0.65), x0 = -wid / 2, y0 = -len / 2, x1 = wid / 2, y1 = len / 2))
            drawPath(shell, Color.White.copy(alpha = 0.95f), style = Stroke(1.5f))
            for ((px, py, s) in listOf(Triple(-wid * 0.2, -len * 0.18, 0.18), Triple(wid * 0.18, len * 0.05, 0.13))) {
                for (a in listOf(0.0, PI / 3, -PI / 3)) {
                    line(Color.White.copy(alpha = 0.8f), px - sin(a) * wid * s, py - cos(a) * wid * s, px + sin(a) * wid * s, py + cos(a) * wid * s, 1.6)
                }
            }
        }
        val (cx, cy) = upright(len / 2 - wid * 0.38)
        circle(rgb(0x1D6FA5), cx, cy, wid * 0.24)
        drawCircle(Color.White, (wid * 0.24).f(), Offset(cx.f(), cy.f()), style = Stroke(2f))
        centered(m, "$ice", cx, cy, Color.White, (wid * 0.3).roundToInt().toDouble())
    }
}

private fun DrawScope.drawCone(x: Double, y: Double, cs: Double) {
    val s = cs * 0.34
    drawOval(rgb(0x0A121E, 0.28), Offset((x + s * 0.3 - s * 1.15).f(), (y + s * 0.95 - s * 0.38).f()), Size((s * 2.3).f(), (s * 0.76).f()))
    rr(rgb(0x2B313C), x - s, y + s * 0.5, s * 2, s * 0.5, 4.0)
    rr(rgb(0x3D4553), x - s, y + s * 0.5, s * 2, s * 0.18, 4.0)
    val body = Path().apply {
        moveTo(x.f(), (y - s * 1.25).f())
        lineTo((x + s * 0.72).f(), (y + s * 0.62).f())
        quadraticTo(x.f(), (y + s * 0.78).f(), (x - s * 0.72).f(), (y + s * 0.62).f())
        close()
    }
    drawPath(body, linear(0f to rgb(0xFFB066), 0.4f to rgb(0xFF7A1A), 1f to rgb(0xC4520B), x0 = x - s * 0.7, y0 = 0.0, x1 = x + s * 0.7, y1 = 0.0))
    drawPath(poly(x - s * 0.33, y - s * 0.28, x + s * 0.33, y - s * 0.28, x + s * 0.46, y + s * 0.08, x - s * 0.46, y + s * 0.08), Color.White.copy(alpha = 0.95f))
}

private fun DrawScope.drawPerson(x: Double, yIn: Double, s: Double, col: Int, lift: Double) {
    val p = COLORS[col]
    drawOval(rgb(0x0A121E, 0.22), Offset((x + s * 0.15 - s * 0.8).f(), (yIn + s * 1.08 - s * 0.26).f()), Size((s * 1.6).f(), (s * 0.52).f()))
    val y = yIn - lift
    drawPath(rrPath(x - s * 0.6, y - s * 0.05, s * 1.2, s * 1.12, s * 0.52), linear(0f to p.l, 0.45f to p.c, 1f to p.d, x0 = x - s * 0.6, y0 = 0.0, x1 = x + s * 0.6, y1 = 0.0))
    head(x, y - s * 0.58, s * 0.48, SKIN)
    val hr = s * 0.42
    drawArc(p.d, 189f, 162f, false, Offset((x - hr).f(), (y - s * 0.72 - hr).f()), Size((hr * 2).f(), (hr * 2).f()))
}

private fun DrawScope.drawGame(g: Game, measurer: TextMeasurer) {
    val L = g.L
    val W = L.w; val H = L.h
    val now = g.time
    // sidewalk with shelter strip and curb
    drawRect(PAVING, Offset.Zero, Size(W.f(), L.sY0.f()))
    var gx = 0.0
    while (gx < W) { line(rgb(0x506482, 0.10), gx, 0.0, gx, L.sY0, 1.0); gx += 22 }
    rr(rgb(0xC9D2DC), 0.0, L.sY0 - 6, W, 6.0, 0.0)
    // asphalt for the bay and the lot
    drawRect(ASPHALT, Offset(0f, L.sY0.f()), Size(W.f(), (H - L.sY0).f()))
    rr(rgb(0, 0.12), 0.0, L.sY0, W, 4.0, 0.0)
    val lotTop = L.sY0 + L.sH
    var mx = 6.0
    while (mx < W) { rr(rgb(0xF2C94C), mx, lotTop + 2, 16.0, 4.0, 0.0); mx += 28 }
    // painted grid
    rr(rgb(0, 0.12), L.gx0 - 6, L.gy0 - 6, L.gx1 - L.gx0 + 12, L.gy1 - L.gy0 + 12, 12.0)
    val dash = PathEffect.dashPathEffect(floatArrayOf((L.cs * 0.28).f(), (L.cs * 0.14).f()))
    val grid = Color.White.copy(alpha = 0.55f)
    for (c in 0..g.cols) {
        val x = (L.gx0 + c * L.cs).f()
        drawLine(grid, Offset(x, L.gy0.f()), Offset(x, L.gy1.f()), 1.5f, pathEffect = dash)
    }
    for (r in 0..g.rows) {
        val y = (L.gy0 + r * L.cs).f()
        drawLine(grid, Offset(L.gx0.f(), y), Offset(L.gx1.f(), y), 1.5f, pathEffect = dash)
    }
    // slots
    for (j in 0 until MAX_SLOTS) {
        val s = L.slotCenter(j)
        val x = s.x - L.slotW / 2; val y = L.sY0 + 8; val h = L.sH - 16
        if (j < g.active) {
            rr(rgb(0, 0.14), x, y, L.slotW, h, 8.0)
            val corners = Path().apply {
                moveTo(x.f(), (y + 10).f()); lineTo(x.f(), y.f()); lineTo((x + 10).f(), y.f())
                moveTo((x + L.slotW - 10).f(), y.f()); lineTo((x + L.slotW).f(), y.f()); lineTo((x + L.slotW).f(), (y + 10).f())
            }
            drawPath(corners, rgb(0xF2C94C), style = Stroke(3f))
            line(Color.White.copy(alpha = 0.7f), x, y + h, x + L.slotW, y + h, 2.0)
        } else {
            rr(Color.White.copy(alpha = 0.08f), x, y, L.slotW, h, 8.0)
            val lock = Color(0xFFD4DAE3)
            drawArc(
                lock, 180f, 180f, false, Offset((s.x - 7).f(), (s.y - 13).f()), Size(14f, 14f),
                style = Stroke(3f),
            )
            rr(lock, s.x - 10, s.y - 6, 20.0, 15.0, 3.0)
            label(measurer, "+1", s.x, s.y + 15, lock, 12, center = true)
        }
    }
    // cones and parked buses, back to front so the raised walls overlap correctly
    val items = mutableListOf<Pair<Double, Any>>()
    for (cone in g.cones) items.add(L.gy0 + (cone.first + 0.5) * L.cs to cone)
    for (b in g.buses) if (b.state == BusState.PARK) items.add(g.parkPose(b).y to b)
    items.sortBy { it.first }
    for ((iy, it) in items) {
        if (it is Pair<*, *>) { drawCone(L.gx0 + ((it.second as Int) + 0.5) * L.cs, iy, L.cs); continue }
        val b = it as Bus
        val p = g.parkPose(b)
        val o = g.parkOffset(b)
        drawBus(p.x + o.x, p.y + o.y, p.ang, p.len, p.wid, if (b.mystery) -1 else b.color, b.model, measurer, ice = max(0, b.ice - g.departed))
    }
    // buses waiting in slots
    for (b in g.buses) if (b.state == BusState.SLOT) {
        val s = L.slotCenter(b.slot)
        val (len, wid) = g.slotDims(b)
        val sh = if (b.shake > 0) sin(now * 1000 / 22) * 3 else 0.0
        drawBus(s.x + sh, s.y, b.ang, len, wid, b.color, b.model, measurer, seats = true, cap = b.cap, riders = b.riders)
    }
    // moving buses on top
    for (b in g.buses) if (b.state == BusState.DRIVE || b.state == BusState.LEAVE) {
        val (sl, sw) = g.slotDims(b)
        if (b.state == BusState.DRIVE) {
            val e = b.k * b.k
            drawBus(b.x, b.y, b.ang, b.fromLen + (sl - b.fromLen) * e, b.fromWid + (sw - b.fromWid) * e, b.color, b.model, measurer)
        } else {
            drawBus(L.slotCenter(b.slot).x, b.y, b.ang, sl, sw, b.color, b.model, measurer, seats = true, cap = b.cap, riders = b.riders)
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

private fun DrawScope.centered(m: TextMeasurer, text: String, x: Double, y: Double, color: Color, size: Double) {
    val res = m.measure(text, TextStyle(color = color, fontSize = (size / density / fontScale).toFloat().sp, fontWeight = FontWeight.ExtraBold))
    drawText(res, topLeft = Offset((x - res.size.width / 2.0).toFloat(), (y - res.size.height / 2.0).toFloat()))
}

private fun DrawScope.label(m: TextMeasurer, text: String, x: Double, y: Double, color: Color, size: Int, center: Boolean) {
    val res = m.measure(text, TextStyle(color = color, fontSize = (size / density / fontScale).sp, fontWeight = FontWeight.ExtraBold))
    val left = if (center) x - res.size.width / 2.0 else x - res.size.width
    drawText(res, topLeft = Offset(left.toFloat(), y.toFloat()))
}
