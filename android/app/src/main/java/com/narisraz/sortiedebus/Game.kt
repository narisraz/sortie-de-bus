package com.narisraz.sortiedebus

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign

// Pure game logic, free of Android imports so it can be unit-tested on the JVM.
// Mirrors index.html (web) and Game.swift (Apple): same seed gives the same level everywhere.

const val MAX_SLOTS = 5
const val BASE_SLOTS = 4

enum class Dir(val dx: Int, val dy: Int, val angle: Double) {
    U(0, -1, 0.0), D(0, 1, PI), L(-1, 0, -PI / 2), R(1, 0, PI / 2);
    val vertical get() = this == U || this == D
}

enum class BusState { PARK, DRIVE, SLOT, LEAVE, GONE }
enum class Status { PLAYING, WON, LOST }

data class Pt(val x: Double, val y: Double)

class Rng(private var seed: Int) {
    fun next(): Double {
        seed += 0x6D2B79F5
        var t = (seed xor (seed ushr 15)) * (1 or seed)
        t = (t + ((t xor (t ushr 7)) * (61 or t))) xor t
        return ((t xor (t ushr 14)).toLong() and 0xffffffffL).toDouble() / 4294967296.0
    }
    fun int(n: Int) = floor(next() * n).toInt()
}

class Bus(val id: Int, val r: Int, val c: Int, val len: Int, val dir: Dir) {
    var color = 0
    var cap = 4
    var state = BusState.PARK
    var riders = 0
    var pending = 0
    var slot = -1
    var mystery = false
    var ice = 0
    var shake = 0.0
    var bumpT = -1.0
    var bumpDist = 0.0
    // animation (layout pixels)
    var x = 0.0; var y = 0.0; var ang = 0.0; var k = 0.0; var vy = 0.0
    var fromLen = 0.0; var fromWid = 0.0
    var pts: List<Pt> = emptyList(); var seg = 0; var pathLen = 1.0; var done = 0.0

    fun cells(): List<Pair<Int, Int>> = (0 until len).map { if (dir.vertical) Pair(r + it, c) else Pair(r, c + it) }

    fun path(rows: Int, cols: Int): List<Pair<Int, Int>> = when (dir) {
        Dir.U -> (r - 1 downTo 0).map { Pair(it, c) }
        Dir.D -> (r + len until rows).map { Pair(it, c) }
        Dir.L -> (c - 1 downTo 0).map { Pair(r, it) }
        Dir.R -> (c + len until cols).map { Pair(r, it) }
    }
}

class Level(val rows: Int, val cols: Int, val buses: List<Bus>, val queue: List<Int>, val solution: List<Int>, val cones: List<Pair<Int, Int>>)

const val COLOR_COUNT = 8
const val BUMP_DUR = 0.32
const val DAILY_LEVEL = 14

private fun <T> MutableList<T>.shuffleWith(rng: Rng): MutableList<T> {
    for (i in size - 1 downTo 1) {
        val j = rng.int(i + 1)
        val t = this[i]; this[i] = this[j]; this[j] = t
    }
    return this
}

// Each bus is placed only if its exit path is clear of the buses already placed,
// so leaving in reverse placement order always works: every level is solvable.
fun generateLevel(level: Int, seed: Int? = null): Level {
    val rng = Rng(seed ?: (level * 9973 + 17))
    val cols = min(5 + (level - 1) / 3, 8)
    val rows = min(6 + (level - 1) / 2, 10)
    val nColors = min(3 + (level - 1) / 2, COLOR_COUNT)
    val win = if (level < 3) 2 else if (level < 8) 3 else 4
    val longP = min(0.15 + level * 0.03, 0.4)
    val occ = Array(rows) { IntArray(cols) { -1 } }
    // Cones block cells and exit paths like a parked bus that never leaves.
    val cones = mutableListOf<Pair<Int, Int>>()
    val nCones = if (level >= 6) min(1 + (level - 6) / 4, 4) else 0
    repeat(nCones) {
        val r = rng.int(rows)
        val c = rng.int(cols)
        if (occ[r][c] == -1) { occ[r][c] = -2; cones.add(Pair(r, c)) }
    }
    val buses = mutableListOf<Bus>()
    val target = if (level < 5) rows * cols * 4 / 9 else rows * cols / 2
    var a = 0
    while (a < 12000 && buses.size < target) {
        a++
        val len = if (rng.next() < longP) 3 else 2
        val vert = rng.next() < 0.62
        val dir = if (vert) (if (rng.next() < 0.7) Dir.U else Dir.D) else (if (rng.next() < 0.5) Dir.L else Dir.R)
        val r = rng.int(rows - (if (vert) len - 1 else 0))
        val c = rng.int(cols - (if (vert) 0 else len - 1))
        val b = Bus(buses.size, r, c, len, dir)
        if (b.cells().any { (y, x) -> occ[y][x] != -1 }) continue
        if (b.path(rows, cols).any { (y, x) -> occ[y][x] != -1 }) continue
        b.cells().forEach { (y, x) -> occ[y][x] = b.id }
        buses.add(b)
    }
    val used = (0 until COLOR_COUNT).toMutableList().shuffleWith(rng).take(nColors)
    buses.forEachIndexed { i, b ->
        b.color = used[(i + rng.int(nColors)) % nColors]
        b.cap = if (b.len == 3) 6 else 4
    }
    if (level >= 4) {
        val p = min(0.15 + (level - 4) * 0.02, 0.35)
        buses.forEach { b -> b.mystery = rng.next() < p }
    }
    val order = buses.reversed()
    // A frozen bus needs `ice` departures first; only buses with at least that many before them in the solution.
    if (level >= 9) order.forEachIndexed { i, b -> if (i >= 2 && rng.next() < 0.12) b.ice = min(3, i) }
    val queue = mutableListOf<Int>()
    var i = 0
    while (i < order.size) {
        val group = mutableListOf<Int>()
        order.subList(i, min(i + win, order.size)).forEach { b -> repeat(b.cap) { group.add(b.color) } }
        queue.addAll(group.shuffleWith(rng))
        i += win
    }
    return Level(rows, cols, buses, queue, order.map { it.id }, cones)
}

class Flyer(val color: Int, val x0: Double, val y0: Double, val to: Bus) {
    var t = 0.0
    val dur = 0.26
}

/** Screen layout in pixels, recomputed by the renderer when the size changes. */
class Layout {
    var w = 0.0; var h = 0.0
    var qY = 0.0; var sY0 = 0.0; var sH = 0.0; var slotW = 0.0; var slotGap = 0.0
    var cs = 1.0; var gx0 = 0.0; var gy0 = 0.0; var gx1 = 0.0; var gy1 = 0.0; var lane = 0.0

    fun compute(w: Double, h: Double, rows: Int, cols: Int) {
        this.w = w; this.h = h
        val qH = max(46.0, min(70.0, h * 0.085))
        sH = max(86.0, min(150.0, h * 0.17))
        qY = 6 + qH * 0.55
        sY0 = 6 + qH
        slotW = min((w - 24) / MAX_SLOTS - 10, sH * 0.5)
        slotGap = (w - slotW * MAX_SLOTS) / (MAX_SLOTS + 1)
        val top = sY0 + sH + 14
        val gh = h - top - 10
        cs = min((w - 16) / (cols + 0.9), gh / (rows + 0.9))
        gx0 = (w - cs * cols) / 2
        gy0 = top + min(cs * 0.55, (gh - cs * rows) / 2)
        gx1 = gx0 + cs * cols; gy1 = gy0 + cs * rows
        lane = cs * 0.42
    }

    fun slotCenter(j: Int) = Pt(slotGap + slotW / 2 + j * (slotW + slotGap), sY0 + sH / 2)
    fun queueSpacing() = max(14.0, min(22.0, w / 22))
}

class Pose(val x: Double, val y: Double, val ang: Double, val len: Double, val wid: Double)

class Game(startLevel: Int) {
    var level = startLevel; private set
    var daily = false; private set
    var cones: List<Pair<Int, Int>> = emptyList(); private set
    var departed = 0; private set
    var misses = 0; private set
    var rows = 0; private set
    var cols = 0; private set
    var buses: List<Bus> = emptyList(); private set
    val queue = ArrayDeque<Int>()
    var total = 0; private set
    var solution: List<Int> = emptyList(); private set
    val slots = arrayOfNulls<Bus>(MAX_SLOTS)
    var active = BASE_SLOTS; private set
    var bonusUsed = false; private set
    val flying = mutableListOf<Flyer>()
    var qOff = 0.0
    var time = 0.0; private set
    private var nextBoard = 0.0
    private var endDelay = -1.0
    private var pendingStatus = Status.PLAYING
    var status = Status.PLAYING; private set
    var over = false; private set
    var startTime = 0.0; private set
    var toast: String? = null; private set
    private var toastUntil = 0.0
    val L = Layout()
    private var sizeW = 0.0; private var sizeH = 0.0
    /** Called once when a level is won (status becomes WON). */
    var onWin: (() -> Unit)? = null

    init { start(startLevel) }

    /** Starts level [lv], or the daily challenge when [dailySeed] is given (generated as level DAILY_LEVEL). */
    fun start(lv: Int, dailySeed: Int? = null) {
        level = lv
        daily = dailySeed != null
        val g = if (dailySeed != null) generateLevel(DAILY_LEVEL, dailySeed) else generateLevel(lv)
        rows = g.rows; cols = g.cols; buses = g.buses; solution = g.solution; cones = g.cones
        departed = 0; misses = 0
        queue.clear(); queue.addAll(g.queue); total = g.queue.size
        slots.fill(null); active = BASE_SLOTS; bonusUsed = false
        flying.clear(); qOff = 0.0; nextBoard = 0.0
        over = false; status = Status.PLAYING; endDelay = -1.0
        startTime = time; toast = null
        if (sizeW > 0) L.compute(sizeW, sizeH, rows, cols)
    }

    fun resize(w: Double, h: Double) {
        if (w == sizeW && h == sizeH) return
        sizeW = w; sizeH = h
        L.compute(w, h, rows, cols)
    }

    fun parkPose(b: Bus): Pose {
        val cs = L.cs
        val w = if (b.dir.vertical) cs else cs * b.len
        val h = if (b.dir.vertical) cs * b.len else cs
        return Pose(L.gx0 + b.c * cs + w / 2, L.gy0 + b.r * cs + h / 2, b.dir.angle, b.len * cs - cs * 0.14, cs * 0.8)
    }

    fun slotDims(b: Bus): Pair<Double, Double> {
        val len = L.sH * (if (b.len == 3) 0.86 else 0.7)
        return Pair(len, min(L.slotW * 0.86, len * 0.5))
    }

    fun queueX(i: Int) = 22 + i * L.queueSpacing() + qOff

    fun useBonus(): Boolean {
        if (bonusUsed) return false
        bonusUsed = true; active = MAX_SLOTS
        if (status == Status.LOST) { status = Status.PLAYING; over = false; pendingStatus = Status.PLAYING; endDelay = -1.0 }
        return true
    }

    private fun showToast(msg: String) { toast = msg; toastUntil = time + 1.3 }

    /** Handles a tap at layout coordinates. */
    fun tapAt(px: Double, py: Double) {
        if (over) return
        val c = floor((px - L.gx0) / L.cs).toInt()
        val r = floor((py - L.gy0) / L.cs).toInt()
        if (c < 0 || r < 0 || c >= cols || r >= rows) return
        buses.firstOrNull { it.state == BusState.PARK && it.cells().any { (y, x) -> y == r && x == c } }?.let { tap(it) }
    }

    fun tap(b: Bus) {
        if (over || b.state != BusState.PARK || b.bumpT >= 0) return
        if (b.ice > departed) {
            val n = b.ice - departed
            b.shake = 0.4; misses++
            showToast(if (n == 1) "Bus gelé : encore 1 départ" else "Bus gelé : encore $n départs")
            return
        }
        val occ = Array(rows) { arrayOfNulls<Bus>(cols) }
        buses.filter { it.state == BusState.PARK }.forEach { o -> o.cells().forEach { (y, x) -> occ[y][x] = o } }
        val coneAt = Array(rows) { BooleanArray(cols) }
        cones.forEach { (y, x) -> coneAt[y][x] = true }
        var free = 0
        var blocked = false
        var blocker: Bus? = null
        for ((y, x) in b.path(rows, cols)) {
            if (coneAt[y][x]) { blocked = true; break }
            val o = occ[y][x]
            if (o != null) { blocker = o; blocked = true; break }
            free++
        }
        if (blocked) {
            b.bumpT = 0.0; b.bumpDist = free * L.cs + L.cs * 0.1
            blocker?.shake = 0.47 // starts after a short delay, see update()
            misses++
            return
        }
        val j = (0 until active).firstOrNull { slots[it] == null }
        if (j == null) { b.shake = 0.4; misses++; showToast("Toutes les places sont prises"); return }
        slots[j] = b; b.slot = j
        b.mystery = false; departed++
        val p = parkPose(b)
        b.x = p.x; b.y = p.y; b.ang = p.ang; b.state = BusState.DRIVE; b.k = 0.0
        b.fromLen = p.len; b.fromWid = p.wid
        b.pts = buildPath(b, p); b.seg = 0
    }

    private fun buildPath(b: Bus, p: Pose): List<Pt> {
        val s = L.slotCenter(b.slot)
        val topLane = L.gy0 - L.lane
        val pts = mutableListOf<Pt>()
        when (b.dir) {
            Dir.U -> pts.add(Pt(p.x, topLane))
            Dir.L -> { pts.add(Pt(L.gx0 - L.lane, p.y)); pts.add(Pt(L.gx0 - L.lane, topLane)) }
            Dir.R -> { pts.add(Pt(L.gx1 + L.lane, p.y)); pts.add(Pt(L.gx1 + L.lane, topLane)) }
            Dir.D -> {
                val sx = if (p.x < L.w / 2) L.gx0 - L.lane else L.gx1 + L.lane
                pts.add(Pt(p.x, L.gy1 + L.lane)); pts.add(Pt(sx, L.gy1 + L.lane)); pts.add(Pt(sx, topLane))
            }
        }
        pts.add(Pt(s.x, topLane)); pts.add(Pt(s.x, s.y))
        var len = 0.0; var px = p.x; var py = p.y
        for (q in pts) { len += hypot(q.x - px, q.y - py); px = q.x; py = q.y }
        b.pathLen = max(1.0, len); b.done = 0.0
        return pts
    }

    private fun angTo(a: Double, target: Double, step: Double): Double {
        var d = target - a
        while (d > PI) d -= 2 * PI
        while (d < -PI) d += 2 * PI
        return if (abs(d) <= step) target else a + sign(d) * step
    }

    fun update(dtIn: Double) {
        val dt = min(0.05, dtIn)
        time += dt
        if (toast != null && time > toastUntil) toast = null
        revealMysteries()
        qOff *= 0.0001.pow(dt)
        if (abs(qOff) < 0.2) qOff = 0.0
        for (b in buses) {
            if (b.shake > 0) b.shake = max(0.0, b.shake - dt)
            if (b.bumpT >= 0) { b.bumpT += dt; if (b.bumpT >= BUMP_DUR) b.bumpT = -1.0 }
            when (b.state) {
                BusState.DRIVE -> {
                    var move = L.cs * 17 * dt
                    while (move > 0 && b.seg < b.pts.size) {
                        val t = b.pts[b.seg]
                        val dx = t.x - b.x; val dy = t.y - b.y; val d = hypot(dx, dy)
                        if (d > 0.01) b.ang = angTo(b.ang, atan2(dx, -dy), 16 * dt)
                        if (d <= move) { b.x = t.x; b.y = t.y; move -= d; b.done += d; b.seg++ }
                        else { b.x += dx / d * move; b.y += dy / d * move; b.done += move; move = 0.0 }
                    }
                    b.k = min(1.0, b.done / b.pathLen)
                    if (b.seg >= b.pts.size) { b.state = BusState.SLOT; b.ang = 0.0; b.k = 1.0 }
                }
                BusState.SLOT -> b.ang = angTo(b.ang, 0.0, 10 * dt)
                BusState.LEAVE -> {
                    b.vy += 1800 * dt
                    b.y -= b.vy * dt
                    if (b.y < -100) b.state = BusState.GONE
                }
                else -> {}
            }
        }
        for (f in flying) f.t += dt
        val landed = flying.filter { it.t >= it.dur }
        flying.removeAll(landed)
        for (f in landed) {
            val b = f.to
            b.pending--; b.riders++
            if (b.riders >= b.cap) { b.state = BusState.LEAVE; slots[b.slot] = null; b.vy = 0.0 }
        }
        if (!over && queue.isNotEmpty() && time >= nextBoard) {
            val col = queue.first()
            val best = slots.filterNotNull()
                .filter { it.state == BusState.SLOT && it.color == col && it.riders + it.pending < it.cap }
                .maxByOrNull { it.riders + it.pending }
            if (best != null) {
                val fx = queueX(0)
                queue.removeFirst(); best.pending++
                qOff += L.queueSpacing()
                flying.add(Flyer(col, fx, L.qY, best))
                nextBoard = time + 0.075
            }
        }
        checkEnd()
        if (endDelay >= 0) {
            endDelay -= dt
            if (endDelay < 0) {
                status = pendingStatus
                if (status == Status.WON) onWin?.invoke()
            }
        }
    }

    // A mystery bus shows its colour once nothing stands in its way.
    private fun revealMysteries() {
        if (buses.none { it.mystery && it.state == BusState.PARK }) return
        val busy = Array(rows) { BooleanArray(cols) }
        buses.filter { it.state == BusState.PARK }.forEach { o -> o.cells().forEach { (y, x) -> busy[y][x] = true } }
        cones.forEach { (y, x) -> busy[y][x] = true }
        for (b in buses) if (b.mystery && b.state == BusState.PARK && b.path(rows, cols).none { (y, x) -> busy[y][x] }) b.mystery = false
    }

    /** 3 stars, minus one at 2 misses, one at 5 misses and one if the extra slot was used; at least 1. */
    fun stars(): Int {
        var s = 3
        if (misses >= 2) s--
        if (misses >= 5) s--
        if (bonusUsed) s--
        return max(1, s)
    }

    private fun checkEnd() {
        if (over) return
        if (queue.isEmpty() && flying.isEmpty() && buses.all { it.state == BusState.GONE || it.state == BusState.LEAVE }) {
            over = true; pendingStatus = Status.WON; endDelay = 0.65
            return
        }
        if (queue.isEmpty() || flying.isNotEmpty()) return
        if (buses.any { it.state == BusState.DRIVE }) return
        for (j in 0 until active) if (slots[j] == null) return
        val col = queue.first()
        if (slots.any { it != null && it.color == col && it.riders + it.pending < it.cap }) return
        over = true; pendingStatus = Status.LOST; endDelay = 0.45
    }

    /** Offset of a parked bus while it bumps into a blocker or shakes. */
    fun parkOffset(b: Bus): Pt {
        var x = 0.0; var y = 0.0
        if (b.bumpT >= 0) {
            val k = kotlin.math.sin(PI * b.bumpT / BUMP_DUR)
            x += b.dir.dx * b.bumpDist * k; y += b.dir.dy * b.bumpDist * k
        }
        val sh = min(b.shake, 0.4)
        if (sh > 0) x += kotlin.math.sin(time * 1000 / 22) * 3 * (sh / 0.4)
        return Pt(x, y)
    }

    fun elapsedSeconds() = (time - startTime).toInt()
}
