import Foundation

// Pure game logic. Mirrors Game.kt (Android) and index.html (web):
// the same level number gives the same level on every platform.

let maxSlots = 5
let baseSlots = 4
let colorCount = 8
let bumpDuration = 0.32
let dailyLevel = 14

enum Dir {
    case up, down, left, right
    var dx: Int { self == .left ? -1 : self == .right ? 1 : 0 }
    var dy: Int { self == .up ? -1 : self == .down ? 1 : 0 }
    var angle: Double {
        switch self {
        case .up: return 0
        case .down: return .pi
        case .left: return -.pi / 2
        case .right: return .pi / 2
        }
    }
    var vertical: Bool { self == .up || self == .down }
    var letter: String {
        switch self {
        case .up: return "U"
        case .down: return "D"
        case .left: return "L"
        case .right: return "R"
        }
    }
}

enum BusState { case park, drive, slot, leave, gone }
enum GameStatus { case playing, won, lost }

struct Rng {
    private var seed: UInt32
    init(_ s: Int) { seed = UInt32(truncatingIfNeeded: s) }
    mutating func next() -> Double {
        seed = seed &+ 0x6D2B79F5
        var t = (seed ^ (seed >> 15)) &* (1 | seed)
        t = (t &+ ((t ^ (t >> 7)) &* (61 | t))) ^ t
        return Double(t ^ (t >> 14)) / 4294967296.0
    }
    mutating func int(_ n: Int) -> Int { Int((next() * Double(n)).rounded(.down)) }
}

struct Pt { var x: Double; var y: Double }

struct Cell: Equatable { let r: Int; let c: Int }

final class Bus {
    let id: Int, r: Int, c: Int, len: Int, dir: Dir
    var color = 0
    var cap = 4
    var state = BusState.park
    var riders = 0
    var pending = 0
    var slot = -1
    var mystery = false
    var ice = 0
    var model = "urbain"
    var shake = 0.0
    var bumpT = -1.0
    var bumpDist = 0.0
    // animation, in layout points
    var x = 0.0, y = 0.0, ang = 0.0, k = 0.0, vy = 0.0
    var fromLen = 0.0, fromWid = 0.0
    var pts: [Pt] = []
    var seg = 0
    var pathLen = 1.0
    var done = 0.0

    init(id: Int, r: Int, c: Int, len: Int, dir: Dir) {
        self.id = id; self.r = r; self.c = c; self.len = len; self.dir = dir
    }

    var cells: [Cell] { (0..<len).map { dir.vertical ? Cell(r: r + $0, c: c) : Cell(r: r, c: c + $0) } }

    func path(rows: Int, cols: Int) -> [Cell] {
        switch dir {
        case .up: return stride(from: r - 1, through: 0, by: -1).map { Cell(r: $0, c: c) }
        case .down: return (r + len < rows) ? (r + len..<rows).map { Cell(r: $0, c: c) } : []
        case .left: return stride(from: c - 1, through: 0, by: -1).map { Cell(r: r, c: $0) }
        case .right: return (c + len < cols) ? (c + len..<cols).map { Cell(r: r, c: $0) } : []
        }
    }
}

struct Level {
    let rows: Int, cols: Int
    let buses: [Bus]
    let queue: [Int]
    let solution: [Int]
    let cones: [Cell]
}

private func shuffled<T>(_ a: [T], _ rng: inout Rng) -> [T] {
    var a = a
    var i = a.count - 1
    while i > 0 {
        let j = rng.int(i + 1)
        a.swapAt(i, j)
        i -= 1
    }
    return a
}

/// Each bus is placed only if its exit path is clear of the buses already placed,
/// so leaving in reverse placement order always works: every level is solvable.
func generateLevel(_ level: Int, seed: Int? = nil) -> Level {
    var rng = Rng(seed ?? (level * 9973 + 17))
    let cols = min(5 + (level - 1) / 3, 8)
    let rows = min(6 + (level - 1) / 2, 10)
    let nColors = min(3 + (level - 1) / 2, colorCount)
    let win = level < 3 ? 2 : level < 8 ? 3 : 4
    let longP = min(0.15 + Double(level) * 0.03, 0.4)
    var occ = Array(repeating: Array(repeating: -1, count: cols), count: rows)
    // Cones block cells and exit paths like a parked bus that never leaves.
    var cones: [Cell] = []
    let nCones = level >= 6 ? min(1 + (level - 6) / 4, 4) : 0
    for _ in 0..<nCones {
        let r = rng.int(rows)
        let c = rng.int(cols)
        if occ[r][c] == -1 { occ[r][c] = -2; cones.append(Cell(r: r, c: c)) }
    }
    var buses: [Bus] = []
    let target = level < 5 ? rows * cols * 4 / 9 : rows * cols / 2
    var a = 0
    while a < 12000 && buses.count < target {
        a += 1
        let len = rng.next() < longP ? 3 : 2
        let vert = rng.next() < 0.62
        let dir: Dir = vert ? (rng.next() < 0.7 ? .up : .down) : (rng.next() < 0.5 ? .left : .right)
        let r = rng.int(rows - (vert ? len - 1 : 0))
        let c = rng.int(cols - (vert ? 0 : len - 1))
        let b = Bus(id: buses.count, r: r, c: c, len: len, dir: dir)
        if b.cells.contains(where: { occ[$0.r][$0.c] != -1 }) { continue }
        if b.path(rows: rows, cols: cols).contains(where: { occ[$0.r][$0.c] != -1 }) { continue }
        for cell in b.cells { occ[cell.r][cell.c] = b.id }
        buses.append(b)
    }
    let used = Array(shuffled(Array(0..<colorCount), &rng).prefix(nColors))
    for (i, b) in buses.enumerated() {
        b.color = used[(i + rng.int(nColors)) % nColors]
        b.cap = b.len == 3 ? 6 : 4
    }
    if level >= 4 {
        let p = min(0.15 + Double(level - 4) * 0.02, 0.35)
        for b in buses { b.mystery = rng.next() < p }
    }
    let order = Array(buses.reversed())
    // A frozen bus needs `ice` departures first; only buses with at least that many before them in the solution.
    if level >= 9 {
        for (i, b) in order.enumerated() where i >= 2 && rng.next() < 0.12 { b.ice = min(3, i) }
    }
    var queue: [Int] = []
    var i = 0
    while i < order.count {
        var group: [Int] = []
        for b in order[i..<min(i + win, order.count)] {
            group.append(contentsOf: Array(repeating: b.color, count: b.cap))
        }
        queue.append(contentsOf: shuffled(group, &rng))
        i += win
    }
    return Level(rows: rows, cols: cols, buses: buses, queue: queue, solution: order.map { $0.id }, cones: cones)
}

// Bus models are cosmetic only; picked from the bus id so no RNG call is added to the generator.
let models2 = ["minibus", "scolaire", "navette"], models3 = ["urbain", "articule", "imperiale", "autocar"]
func modelFor(_ b: Bus, level: Int) -> String {
    let m = b.len == 3 ? models3 : models2
    return m[(b.id * 7 + level * 3) % m.count]
}

final class Flyer {
    let color: Int, x0: Double, y0: Double
    let to: Bus
    var t = 0.0
    let dur = 0.26
    init(color: Int, x0: Double, y0: Double, to: Bus) { self.color = color; self.x0 = x0; self.y0 = y0; self.to = to }
}

/// Screen layout in points, recomputed when the canvas size changes.
struct Layout {
    var w = 0.0, h = 0.0
    var qY = 0.0, sY0 = 0.0, sH = 0.0, slotW = 0.0, slotGap = 0.0
    var cs = 1.0, gx0 = 0.0, gy0 = 0.0, gx1 = 0.0, gy1 = 0.0, lane = 0.0

    mutating func compute(w: Double, h: Double, rows: Int, cols: Int) {
        self.w = w; self.h = h
        let qH = max(46, min(70, h * 0.085))
        sH = max(86, min(150, h * 0.17))
        qY = 6 + qH * 0.55
        sY0 = 6 + qH
        slotW = min((w - 24) / Double(maxSlots) - 10, sH * 0.5)
        slotGap = (w - slotW * Double(maxSlots)) / Double(maxSlots + 1)
        let top = sY0 + sH + 14
        let gh = h - top - 10
        cs = min((w - 16) / (Double(cols) + 0.9), gh / (Double(rows) + 0.9))
        gx0 = (w - cs * Double(cols)) / 2
        gy0 = top + min(cs * 0.55, (gh - cs * Double(rows)) / 2)
        gx1 = gx0 + cs * Double(cols); gy1 = gy0 + cs * Double(rows)
        lane = cs * 0.42
    }

    func slotCenter(_ j: Int) -> Pt {
        Pt(x: slotGap + slotW / 2 + Double(j) * (slotW + slotGap), y: sY0 + sH / 2)
    }
    var queueSpacing: Double { max(14, min(22, w / 22)) }
}

struct Pose { let x: Double, y: Double, ang: Double, len: Double, wid: Double }

final class Game {
    private(set) var level = 1
    private(set) var daily = false
    private(set) var cones: [Cell] = []
    private(set) var departed = 0
    private(set) var misses = 0
    private(set) var rows = 0, cols = 0
    private(set) var buses: [Bus] = []
    private(set) var queue: [Int] = []
    private(set) var total = 0
    private(set) var solution: [Int] = []
    private(set) var slots: [Bus?] = Array(repeating: nil, count: maxSlots)
    private(set) var active = baseSlots
    private(set) var bonusUsed = false
    private(set) var flying: [Flyer] = []
    private(set) var qOff = 0.0
    private(set) var time = 0.0
    private var nextBoard = 0.0
    private var endDelay = -1.0
    private var pendingStatus = GameStatus.playing
    private(set) var status = GameStatus.playing
    private(set) var over = false
    private(set) var startTime = 0.0
    private(set) var toast: String?
    private var toastUntil = 0.0
    private(set) var L = Layout()
    private var size = CGSize.zero
    private var lastDate: Date?
    /// Called once when a level is won (status becomes .won).
    var onWin: (() -> Void)?

    init(level: Int) { start(level) }

    /// Starts level `lv`, or the daily challenge when `dailySeed` is given (generated as level `dailyLevel`).
    func start(_ lv: Int, dailySeed: Int? = nil) {
        level = lv
        daily = dailySeed != nil
        let g = dailySeed.map { generateLevel(dailyLevel, seed: $0) } ?? generateLevel(lv)
        rows = g.rows; cols = g.cols; buses = g.buses; solution = g.solution; cones = g.cones
        for b in buses { b.model = modelFor(b, level: daily ? dailyLevel : lv) }
        departed = 0; misses = 0
        queue = g.queue; total = g.queue.count
        slots = Array(repeating: nil, count: maxSlots); active = baseSlots; bonusUsed = false
        flying = []; qOff = 0; nextBoard = 0
        over = false; status = .playing; endDelay = -1
        startTime = time; toast = nil
        if size.width > 0 { L.compute(w: Double(size.width), h: Double(size.height), rows: rows, cols: cols) }
    }

    func resize(_ s: CGSize) {
        if s == size { return }
        size = s
        L.compute(w: Double(s.width), h: Double(s.height), rows: rows, cols: cols)
    }

    /// Advances the simulation to a wall-clock date (called once per frame).
    func advance(to date: Date) {
        if let last = lastDate { update(date.timeIntervalSince(last)) }
        lastDate = date
    }

    func parkPose(_ b: Bus) -> Pose {
        let cs = L.cs
        let w = b.dir.vertical ? cs : cs * Double(b.len)
        let h = b.dir.vertical ? cs * Double(b.len) : cs
        return Pose(x: L.gx0 + Double(b.c) * cs + w / 2, y: L.gy0 + Double(b.r) * cs + h / 2,
                    ang: b.dir.angle, len: Double(b.len) * cs - cs * 0.14, wid: cs * 0.8)
    }

    func slotDims(_ b: Bus) -> (len: Double, wid: Double) {
        let len = L.sH * (b.len == 3 ? 0.86 : 0.7)
        return (len, min(L.slotW * 0.86, len * 0.5))
    }

    func queueX(_ i: Int) -> Double { 22 + Double(i) * L.queueSpacing + qOff }

    @discardableResult
    func useBonus() -> Bool {
        if bonusUsed { return false }
        bonusUsed = true; active = maxSlots
        if status == .lost { status = .playing; over = false; pendingStatus = .playing; endDelay = -1 }
        return true
    }

    private func showToast(_ msg: String) { toast = msg; toastUntil = time + 1.3 }

    func tap(at p: CGPoint) {
        if over { return }
        let c = Int(((Double(p.x) - L.gx0) / L.cs).rounded(.down))
        let r = Int(((Double(p.y) - L.gy0) / L.cs).rounded(.down))
        if c < 0 || r < 0 || c >= cols || r >= rows { return }
        if let b = buses.first(where: { $0.state == .park && $0.cells.contains(Cell(r: r, c: c)) }) { tap(b) }
    }

    func tap(_ b: Bus) {
        if over || b.state != .park || b.bumpT >= 0 { return }
        if b.ice > departed {
            let n = b.ice - departed
            b.shake = 0.4; misses += 1
            showToast(n == 1 ? "Bus gelé : encore 1 départ" : "Bus gelé : encore \(n) départs")
            return
        }
        var occ: [[Bus?]] = Array(repeating: Array(repeating: nil, count: cols), count: rows)
        for o in buses where o.state == .park { for cell in o.cells { occ[cell.r][cell.c] = o } }
        var free = 0
        var blocked = false
        var blocker: Bus?
        for cell in b.path(rows: rows, cols: cols) {
            if cones.contains(cell) { blocked = true; break }
            if let o = occ[cell.r][cell.c] { blocker = o; blocked = true; break }
            free += 1
        }
        if blocked {
            b.bumpT = 0; b.bumpDist = Double(free) * L.cs + L.cs * 0.1
            blocker?.shake = 0.47
            misses += 1
            return
        }
        guard let j = (0..<active).first(where: { slots[$0] == nil }) else {
            b.shake = 0.4; misses += 1; showToast("Toutes les places sont prises"); return
        }
        slots[j] = b; b.slot = j
        b.mystery = false; departed += 1
        let p = parkPose(b)
        b.x = p.x; b.y = p.y; b.ang = p.ang; b.state = .drive; b.k = 0
        b.fromLen = p.len; b.fromWid = p.wid
        b.pts = buildPath(b, p); b.seg = 0
    }

    private func buildPath(_ b: Bus, _ p: Pose) -> [Pt] {
        let s = L.slotCenter(b.slot)
        let topLane = L.gy0 - L.lane
        var pts: [Pt] = []
        switch b.dir {
        case .up: pts.append(Pt(x: p.x, y: topLane))
        case .left: pts += [Pt(x: L.gx0 - L.lane, y: p.y), Pt(x: L.gx0 - L.lane, y: topLane)]
        case .right: pts += [Pt(x: L.gx1 + L.lane, y: p.y), Pt(x: L.gx1 + L.lane, y: topLane)]
        case .down:
            let sx = p.x < L.w / 2 ? L.gx0 - L.lane : L.gx1 + L.lane
            pts += [Pt(x: p.x, y: L.gy1 + L.lane), Pt(x: sx, y: L.gy1 + L.lane), Pt(x: sx, y: topLane)]
        }
        pts += [Pt(x: s.x, y: topLane), s]
        var len = 0.0, px = p.x, py = p.y
        for q in pts { len += hypot(q.x - px, q.y - py); px = q.x; py = q.y }
        b.pathLen = max(1, len); b.done = 0
        return pts
    }

    private func angTo(_ a: Double, _ target: Double, _ step: Double) -> Double {
        var d = target - a
        while d > .pi { d -= 2 * .pi }
        while d < -.pi { d += 2 * .pi }
        return abs(d) <= step ? target : a + (d > 0 ? step : -step)
    }

    func update(_ dtIn: Double) {
        let dt = min(0.05, max(0, dtIn))
        time += dt
        if toast != nil && time > toastUntil { toast = nil }
        revealMysteries()
        qOff *= pow(0.0001, dt)
        if abs(qOff) < 0.2 { qOff = 0 }
        for b in buses {
            if b.shake > 0 { b.shake = max(0, b.shake - dt) }
            if b.bumpT >= 0 { b.bumpT += dt; if b.bumpT >= bumpDuration { b.bumpT = -1 } }
            switch b.state {
            case .drive:
                var move = L.cs * 17 * dt
                while move > 0 && b.seg < b.pts.count {
                    let t = b.pts[b.seg]
                    let dx = t.x - b.x, dy = t.y - b.y, d = hypot(dx, dy)
                    if d > 0.01 { b.ang = angTo(b.ang, atan2(dx, -dy), 16 * dt) }
                    if d <= move { b.x = t.x; b.y = t.y; move -= d; b.done += d; b.seg += 1 }
                    else { b.x += dx / d * move; b.y += dy / d * move; b.done += move; move = 0 }
                }
                b.k = min(1, b.done / b.pathLen)
                if b.seg >= b.pts.count { b.state = .slot; b.ang = 0; b.k = 1 }
            case .slot:
                b.ang = angTo(b.ang, 0, 10 * dt)
            case .leave:
                b.vy += 1800 * dt
                b.y -= b.vy * dt
                if b.y < -100 { b.state = .gone }
            default: break
            }
        }
        for f in flying { f.t += dt }
        let landed = flying.filter { $0.t >= $0.dur }
        flying.removeAll { $0.t >= $0.dur }
        for f in landed {
            let b = f.to
            b.pending -= 1; b.riders += 1
            if b.riders >= b.cap { b.state = .leave; slots[b.slot] = nil; b.vy = 0 }
        }
        if !over, let col = queue.first, time >= nextBoard {
            let best = slots.compactMap { $0 }
                .filter { $0.state == .slot && $0.color == col && $0.riders + $0.pending < $0.cap }
                .max { $0.riders + $0.pending < $1.riders + $1.pending }
            if let best {
                let fx = queueX(0)
                queue.removeFirst(); best.pending += 1
                qOff += L.queueSpacing
                flying.append(Flyer(color: col, x0: fx, y0: L.qY, to: best))
                nextBoard = time + 0.075
            }
        }
        checkEnd()
        if endDelay >= 0 {
            endDelay -= dt
            if endDelay < 0 {
                status = pendingStatus
                if status == .won { onWin?() }
            }
        }
    }

    /// A mystery bus shows its colour once nothing stands in its way.
    private func revealMysteries() {
        if !buses.contains(where: { $0.mystery && $0.state == .park }) { return }
        var busy = Array(repeating: Array(repeating: false, count: cols), count: rows)
        for o in buses where o.state == .park { for cell in o.cells { busy[cell.r][cell.c] = true } }
        for cell in cones { busy[cell.r][cell.c] = true }
        for b in buses where b.mystery && b.state == .park {
            if !b.path(rows: rows, cols: cols).contains(where: { busy[$0.r][$0.c] }) { b.mystery = false }
        }
    }

    /// 3 stars, minus one at 2 misses, one at 5 misses and one if the extra slot was used; at least 1.
    var stars: Int {
        var s = 3
        if misses >= 2 { s -= 1 }
        if misses >= 5 { s -= 1 }
        if bonusUsed { s -= 1 }
        return max(1, s)
    }

    private func checkEnd() {
        if over { return }
        if queue.isEmpty && flying.isEmpty && buses.allSatisfy({ $0.state == .gone || $0.state == .leave }) {
            over = true; pendingStatus = .won; endDelay = 0.65
            return
        }
        guard let col = queue.first, flying.isEmpty else { return }
        if buses.contains(where: { $0.state == .drive }) { return }
        for j in 0..<active where slots[j] == nil { return }
        if slots.contains(where: { $0 != nil && $0!.color == col && $0!.riders + $0!.pending < $0!.cap }) { return }
        over = true; pendingStatus = .lost; endDelay = 0.45
    }

    /// Offset of a parked bus while it bumps into a blocker or shakes.
    func parkOffset(_ b: Bus) -> Pt {
        var x = 0.0, y = 0.0
        if b.bumpT >= 0 {
            let k = sin(.pi * b.bumpT / bumpDuration)
            x += Double(b.dir.dx) * b.bumpDist * k; y += Double(b.dir.dy) * b.bumpDist * k
        }
        let sh = min(b.shake, 0.4)
        if sh > 0 { x += sin(time * 1000 / 22) * 3 * (sh / 0.4) }
        return Pt(x: x, y: y)
    }

    var elapsedSeconds: Int { Int(time - startTime) }
}
