import SwiftUI

extension Color {
    init(hex: UInt32, alpha: Double = 1) {
        self.init(.sRGB,
                  red: Double((hex >> 16) & 0xFF) / 255,
                  green: Double((hex >> 8) & 0xFF) / 255,
                  blue: Double(hex & 0xFF) / 255,
                  opacity: alpha)
    }
}

private struct BusColor { let c: Color; let d: Color; let l: Color }

private let palette: [BusColor] = [
    BusColor(c: Color(hex: 0xF0453A), d: Color(hex: 0xB02A22), l: Color(hex: 0xFF8A80)), // rouge
    BusColor(c: Color(hex: 0x2F80ED), d: Color(hex: 0x1C56A8), l: Color(hex: 0x7FB3FF)), // bleu
    BusColor(c: Color(hex: 0x27AE60), d: Color(hex: 0x1A7A43), l: Color(hex: 0x7BE0A5)), // vert
    BusColor(c: Color(hex: 0xF7C325), d: Color(hex: 0xC4910B), l: Color(hex: 0xFFE27A)), // jaune
    BusColor(c: Color(hex: 0x9B51E0), d: Color(hex: 0x6B2FA6), l: Color(hex: 0xC99BFF)), // violet
    BusColor(c: Color(hex: 0xEC4F9C), d: Color(hex: 0xA8306B), l: Color(hex: 0xFF9CCB)), // rose
    BusColor(c: Color(hex: 0x1FC8DB), d: Color(hex: 0x108C9A), l: Color(hex: 0x8AF0FB)), // cyan
    BusColor(c: Color(hex: 0xF2802C), d: Color(hex: 0xB5561A), l: Color(hex: 0xFFB57F)), // orange
]

private let sky = Color(hex: 0xCFE3F2)
private let ink = Color(hex: 0x1D2A3A)
private let inkSoft = Color(hex: 0x4A5B70)
private let accent = Color(hex: 0xFF8A1F)
private let accentInk = Color(hex: 0x5A2A00)

struct GameView: View {
    @AppStorage("level") private var savedLevel = 1
    @State private var game: Game?

    var body: some View {
        ZStack(alignment: .top) {
            sky.ignoresSafeArea()
            if let game {
                TimelineView(.animation) { tl in
                    content(game, tl.date)
                }
            }
        }
        .onAppear { if game == nil { game = Game(level: max(1, savedLevel)) } }
    }

    private func go(_ game: Game, _ level: Int) {
        game.start(level)
        savedLevel = level
    }

    private func lostActions(_ game: Game) -> [(String, () -> Void)] {
        var acts: [(String, () -> Void)] = []
        if !game.bonusUsed { acts.append(("Ajouter une place", { _ = game.useBonus() })) }
        acts.append(("Réessayer", { go(game, game.level) }))
        return acts
    }

    @ViewBuilder
    private func content(_ game: Game, _ date: Date) -> some View {
        VStack(spacing: 8) {
            HStack {
                HudButton(label: "↻", primary: false) { go(game, game.level) }
                VStack(spacing: 0) {
                    Text("SORTIE DE BUS").font(.system(size: 11, weight: .heavy, design: .rounded))
                        .tracking(1).foregroundStyle(inkSoft)
                    Text("Niveau \(game.level)").font(.system(size: 26, weight: .heavy, design: .rounded))
                        .foregroundStyle(ink)
                }
                .frame(maxWidth: .infinity)
                HudButton(label: "+1 place", primary: true) { _ = game.useBonus() }
                    .disabled(game.bonusUsed).opacity(game.bonusUsed ? 0.45 : 1)
            }
            ZStack {
                Canvas { ctx, size in
                    game.advance(to: date)
                    game.resize(size)
                    drawGame(ctx, game)
                }
                .contentShape(Rectangle())
                .gesture(SpatialTapGesture().onEnded { v in game.tap(at: v.location) })

                if let t = game.toast {
                    Text(t).font(.system(size: 15, weight: .heavy, design: .rounded)).foregroundStyle(.white)
                        .padding(.horizontal, 14).padding(.vertical, 8)
                        .background(ink.opacity(0.92), in: RoundedRectangle(cornerRadius: 12))
                }
                switch game.status {
                case .won:
                    Overlay(title: "Bravo !",
                            text: "\(game.total) passagers transportés en \(game.elapsedSeconds) s.",
                            actions: [("Niveau suivant", { go(game, game.level + 1) })])
                case .lost:
                    Overlay(title: "Bouchon !",
                            text: "Plus de place libre et aucun bus pour le prochain passager.",
                            actions: lostActions(game))
                case .playing:
                    EmptyView()
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: 22))
            .shadow(color: Color(hex: 0x142337, alpha: 0.18), radius: 9, y: 6)
        }
        .frame(maxWidth: 540)
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
    }
}

private struct HudButton: View {
    let label: String
    let primary: Bool
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(label).font(.system(size: 16, weight: .heavy, design: .rounded))
                .foregroundStyle(primary ? accentInk : ink)
                .padding(.horizontal, 12).frame(minWidth: 44, minHeight: 40)
                .background(primary ? accent : .white, in: RoundedRectangle(cornerRadius: 14))
                .shadow(color: .black.opacity(0.18), radius: 0, y: 3)
        }
        .buttonStyle(.plain)
    }
}

private struct Overlay: View {
    let title: String
    let text: String
    let actions: [(String, () -> Void)]
    var body: some View {
        ZStack {
            Color(hex: 0x142337, alpha: 0.55)
            VStack(spacing: 12) {
                Text(title).font(.system(size: 32, weight: .heavy, design: .rounded)).foregroundStyle(ink)
                Text(text).multilineTextAlignment(.center).foregroundStyle(inkSoft)
                ForEach(Array(actions.enumerated()), id: \.offset) { i, a in
                    Button(action: a.1) {
                        Text(a.0).font(.system(size: 16, weight: .heavy, design: .rounded))
                            .foregroundStyle(i == 0 ? accentInk : ink)
                            .frame(maxWidth: .infinity, minHeight: 44)
                            .background(i == 0 ? accent : Color(hex: 0xEEF2F6), in: RoundedRectangle(cornerRadius: 14))
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(22)
            .frame(maxWidth: 300)
            .background(.white, in: RoundedRectangle(cornerRadius: 22))
            .padding(24)
        }
    }
}

// MARK: - Drawing (layout units = points)

private func rr(_ ctx: GraphicsContext, _ color: Color, _ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double) {
    let rad = max(0, min(r, w / 2, h / 2))
    ctx.fill(Path(roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerRadius: rad), with: .color(color))
}

private func circle(_ ctx: GraphicsContext, _ color: Color, _ x: Double, _ y: Double, _ r: Double) {
    ctx.fill(Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2)), with: .color(color))
}

private func drawBus(_ base: GraphicsContext, x: Double, y: Double, ang: Double, len: Double, wid: Double,
                     color: Int, seats: Bool, cap: Int = 0, riders: Int = 0) {
    let p = palette[color]
    var ctx = base
    ctx.translateBy(x: x, y: y)
    ctx.rotate(by: .radians(ang))
    let r = wid * 0.28
    rr(ctx, Color(hex: 0x0F1928, alpha: 0.25), -wid / 2 + wid * 0.08, -len / 2 + wid * 0.14, wid, len, r)
    rr(ctx, p.d, -wid / 2, -len / 2, wid, len, r)
    rr(ctx, p.c, -wid / 2, -len / 2, wid, len - wid * 0.1, r)
    let win = Color(hex: 0xD2EBFF, alpha: 0.85)
    let n = max(2, Int((len / wid * 1.4).rounded()))
    let gap = len * 0.62 / Double(n)
    for i in 0..<n {
        let yy = -len / 2 + len * 0.27 + Double(i) * gap
        rr(ctx, win, -wid / 2 + wid * 0.04, yy, wid * 0.09, gap * 0.7, 2)
        rr(ctx, win, wid / 2 - wid * 0.13, yy, wid * 0.09, gap * 0.7, 2)
    }
    rr(ctx, Color(hex: 0xD9EFFF), -wid / 2 + wid * 0.12, -len / 2 + wid * 0.07, wid * 0.76, wid * 0.24, wid * 0.08)
    rr(ctx, Color(hex: 0xFFFFFF, alpha: 0.7), -wid / 2 + wid * 0.18, -len / 2 + wid * 0.1, wid * 0.22, wid * 0.08, 2)
    rr(ctx, p.l, -wid * 0.3, -len / 2 + wid * 0.42, wid * 0.6, len - wid * 0.62, wid * 0.14)
    if seats {
        let rows2 = cap / 2
        let top = -len / 2 + wid * 0.5
        let step = (len - wid * 0.78) / Double(rows2)
        let rad = min(wid * 0.13, step * 0.32)
        for i in 0..<cap {
            let cx = (i % 2 == 0 ? -1.0 : 1.0) * wid * 0.14
            let cy = top + step * (Double(i / 2) + 0.5)
            circle(ctx, i < riders ? .white : Color(hex: 0x000000, alpha: 0.22), cx, cy, rad)
            if i < riders { circle(ctx, p.d, cx, cy, rad * 0.5) }
        }
    } else {
        let aw = wid * 0.42, ah = min(len * 0.55, wid * 1.2), cy = wid * 0.12
        var path = Path()
        path.move(to: CGPoint(x: 0, y: cy - ah / 2))
        path.addLine(to: CGPoint(x: aw / 2, y: cy - ah / 2 + aw * 0.55))
        path.addLine(to: CGPoint(x: aw * 0.17, y: cy - ah / 2 + aw * 0.55))
        path.addLine(to: CGPoint(x: aw * 0.17, y: cy + ah / 2))
        path.addLine(to: CGPoint(x: -aw * 0.17, y: cy + ah / 2))
        path.addLine(to: CGPoint(x: -aw * 0.17, y: cy - ah / 2 + aw * 0.55))
        path.addLine(to: CGPoint(x: -aw / 2, y: cy - ah / 2 + aw * 0.55))
        path.closeSubpath()
        ctx.fill(path, with: .color(.white))
    }
    rr(ctx, Color(hex: 0xFFF6C2), -wid / 2 + wid * 0.1, -len / 2 + 1, wid * 0.14, wid * 0.05, 0)
    rr(ctx, Color(hex: 0xFFF6C2), wid / 2 - wid * 0.24, -len / 2 + 1, wid * 0.14, wid * 0.05, 0)
}

private func drawPerson(_ ctx: GraphicsContext, x: Double, y yIn: Double, s: Double, color: Int, lift: Double) {
    let p = palette[color]
    ctx.fill(Path(ellipseIn: CGRect(x: x - s * 0.75, y: yIn + s * 0.8, width: s * 1.5, height: s * 0.5)),
             with: .color(Color(hex: 0x000000, alpha: 0.18)))
    let y = yIn - lift
    rr(ctx, p.d, x - s * 0.62, y - s * 0.05, s * 1.24, s * 1.1, s * 0.5)
    rr(ctx, p.c, x - s * 0.55, y - s * 0.05, s * 1.1, s * 0.95, s * 0.45)
    circle(ctx, p.c, x, y - s * 0.55, s * 0.48)
    circle(ctx, Color(hex: 0xFFFFFF, alpha: 0.55), x - s * 0.15, y - s * 0.7, s * 0.14)
}

private func line(_ ctx: GraphicsContext, _ color: Color, _ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double, _ w: Double) {
    var p = Path()
    p.move(to: CGPoint(x: x0, y: y0)); p.addLine(to: CGPoint(x: x1, y: y1))
    ctx.stroke(p, with: .color(color), style: StrokeStyle(lineWidth: w, lineCap: .round))
}

private func drawGame(_ ctx: GraphicsContext, _ g: Game) {
    let L = g.L
    let W = L.w, H = L.h, now = g.time
    // station sidewalk
    rr(ctx, Color(hex: 0xE8EEF4), 0, 0, W, L.sY0, 0)
    var gx = 0.0
    while gx < W { line(ctx, Color(hex: 0x506482, alpha: 0.12), gx, 0, gx, L.sY0, 1); gx += 18 }
    line(ctx, Color(hex: 0xB8C4D2), 10, L.qY + 13, W - 10, L.qY + 13, 3)
    // bus bay
    rr(ctx, Color(hex: 0x4B5565), 0, L.sY0, W, L.sH, 0)
    rr(ctx, Color(hex: 0x5A6576), 0, L.sY0, W, 4, 0)
    // parking lot
    let lotTop = L.sY0 + L.sH
    rr(ctx, Color(hex: 0xB9C3CF), 0, lotTop, W, H - lotTop, 0)
    var mx = 6.0
    while mx < W { rr(ctx, Color(hex: 0xF2C94C), mx, lotTop + 4, 14, 4, 0); mx += 26 }
    rr(ctx, Color(hex: 0xCFD7E0), L.gx0 - 4, L.gy0 - 4, L.gx1 - L.gx0 + 8, L.gy1 - L.gy0 + 8, 10)
    let grid = Color(hex: 0xFFFFFF, alpha: 0.75)
    for c in 0...g.cols { let x = L.gx0 + Double(c) * L.cs; line(ctx, grid, x, L.gy0, x, L.gy1, 1.5) }
    for r in 0...g.rows { let y = L.gy0 + Double(r) * L.cs; line(ctx, grid, L.gx0, y, L.gx1, y, 1.5) }
    // slots
    for j in 0..<maxSlots {
        let s = L.slotCenter(j)
        let x = s.x - L.slotW / 2, y = L.sY0 + 8, h = L.sH - 16
        if j < g.active {
            rr(ctx, Color(hex: 0x3A4250), x, y, L.slotW, h, 8)
            ctx.stroke(Path(roundedRect: CGRect(x: x + 3, y: y + 3, width: L.slotW - 6, height: h - 6), cornerRadius: 6),
                       with: .color(Color(hex: 0xFFFFFF, alpha: 0.55)),
                       style: StrokeStyle(lineWidth: 2, dash: [6, 5]))
        } else {
            rr(ctx, Color(hex: 0xFFFFFF, alpha: 0.1), x, y, L.slotW, h, 8)
            let lock = Color(hex: 0xD4DAE3)
            var arc = Path()
            arc.addArc(center: CGPoint(x: s.x, y: s.y - 6), radius: 7, startAngle: .degrees(180), endAngle: .degrees(0), clockwise: false)
            ctx.stroke(arc, with: .color(lock), lineWidth: 3)
            rr(ctx, lock, s.x - 10, s.y - 6, 20, 15, 3)
            ctx.draw(Text("+1").font(.system(size: 12, weight: .heavy, design: .rounded)).foregroundColor(lock),
                     at: CGPoint(x: s.x, y: s.y + 22))
        }
    }
    // parked buses
    for b in g.buses where b.state == .park {
        let p = g.parkPose(b), o = g.parkOffset(b)
        drawBus(ctx, x: p.x + o.x, y: p.y + o.y, ang: p.ang, len: p.len, wid: p.wid, color: b.color, seats: false)
    }
    // buses waiting in slots
    for b in g.buses where b.state == .slot {
        let s = L.slotCenter(b.slot), d = g.slotDims(b)
        let sh = b.shake > 0 ? sin(now * 1000 / 22) * 3 : 0
        drawBus(ctx, x: s.x + sh, y: s.y, ang: b.ang, len: d.len, wid: d.wid, color: b.color, seats: true, cap: b.cap, riders: b.riders)
    }
    // moving buses on top
    for b in g.buses where b.state == .drive || b.state == .leave {
        let d = g.slotDims(b)
        if b.state == .drive {
            let e = b.k * b.k
            drawBus(ctx, x: b.x, y: b.y, ang: b.ang, len: b.fromLen + (d.len - b.fromLen) * e,
                    wid: b.fromWid + (d.wid - b.fromWid) * e, color: b.color, seats: false)
        } else {
            drawBus(ctx, x: L.slotCenter(b.slot).x, y: b.y, ang: b.ang, len: d.len, wid: d.wid,
                    color: b.color, seats: true, cap: b.cap, riders: b.riders)
        }
    }
    // queue
    let sp = L.queueSpacing, ps = sp * 0.42
    let maxShown = max(0, Int((W - 60) / sp))
    let n = min(g.queue.count, maxShown)
    for i in stride(from: n - 1, through: 0, by: -1) {
        let lift = i == 0 ? abs(sin(now * 1000 / 160)) * 3 : 0
        drawPerson(ctx, x: g.queueX(i), y: L.qY, s: ps, color: g.queue[i], lift: lift)
    }
    if g.queue.count > n {
        ctx.draw(Text("+\(g.queue.count - n)").font(.system(size: 13, weight: .heavy, design: .rounded)).foregroundColor(inkSoft),
                 at: CGPoint(x: W - 10, y: L.qY), anchor: .trailing)
    }
    for f in g.flying {
        let s = L.slotCenter(f.to.slot), k = min(1, f.t / f.dur)
        let x = f.x0 + (s.x - f.x0) * k
        let y = f.y0 + (s.y - f.y0) * k - sin(.pi * k) * 26
        drawPerson(ctx, x: x, y: y, s: ps * (1 - 0.35 * k), color: f.color, lift: 0)
    }
    // progress bar
    let done = Double(g.total - g.queue.count), bw = W - 20
    rr(ctx, Color(hex: 0x1D2A3A, alpha: 0.12), 10, 4, bw, 5, 3)
    rr(ctx, accent, 10, 4, max(5, bw * done / Double(max(1, g.total))), 5, 3)
}
