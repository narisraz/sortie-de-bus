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

private struct BusColor {
    let ch: UInt32, dh: UInt32, lh: UInt32
    var c: Color { Color(hex: ch) }
    var d: Color { Color(hex: dh) }
    var l: Color { Color(hex: lh) }
}

private let palette: [BusColor] = [
    BusColor(ch: 0xF0453A, dh: 0xB02A22, lh: 0xFF8A80), // rouge
    BusColor(ch: 0x2F80ED, dh: 0x1C56A8, lh: 0x7FB3FF), // bleu
    BusColor(ch: 0x27AE60, dh: 0x1A7A43, lh: 0x7BE0A5), // vert
    BusColor(ch: 0xF7C325, dh: 0xC4910B, lh: 0xFFE27A), // jaune
    BusColor(ch: 0x9B51E0, dh: 0x6B2FA6, lh: 0xC99BFF), // violet
    BusColor(ch: 0xEC4F9C, dh: 0xA8306B, lh: 0xFF9CCB), // rose
    BusColor(ch: 0x1FC8DB, dh: 0x108C9A, lh: 0x8AF0FB), // cyan
    BusColor(ch: 0xF2802C, dh: 0xB5561A, lh: 0xFFB57F), // orange
]

private let sky = Color(hex: 0xCFE3F2)
private let ink = Color(hex: 0x1D2A3A)
private let inkSoft = Color(hex: 0x4A5B70)
private let accent = Color(hex: 0xFF8A1F)
private let accentInk = Color(hex: 0x5A2A00)

private let mysteryColor = BusColor(ch: 0x8D96A3, dh: 0x5F6875, lh: 0xB7BFCA)
private let skin = BusColor(ch: 0xFFE0C4, dh: 0xD79A6E, lh: 0xFFE0C4)

/// Today's challenge seed: the local date as yyyymmdd (e.g. 20261014).
private func dailySeed() -> Int {
    let d = Calendar.current.dateComponents([.year, .month, .day], from: Date())
    return d.year! * 10000 + d.month! * 100 + d.day!
}

private func starsText(_ n: Int) -> String { String(repeating: "★", count: n) + String(repeating: "☆", count: 3 - n) }

struct GameView: View {
    @AppStorage("level") private var savedLevel = 1
    @State private var game: Game?
    @State private var starsTotal = 0

    var body: some View {
        ZStack(alignment: .top) {
            sky.ignoresSafeArea()
            if let game {
                TimelineView(.animation) { tl in
                    content(game, tl.date)
                }
            }
        }
        .onAppear {
            if game == nil {
                let g = Game(level: max(1, savedLevel))
                g.onWin = { [weak g] in DispatchQueue.main.async { if let g { recordWin(g) } } }
                game = g
                starsTotal = computeStarsTotal()
            }
        }
    }

    private func go(_ game: Game, _ level: Int) { game.start(level) }

    private func restart(_ game: Game) {
        if game.daily { game.start(game.level, dailySeed: dailySeed()) } else { game.start(game.level) }
    }

    /// Best stars per level live in UserDefaults (the AppStorage store) as "stars_N".
    private func recordWin(_ game: Game) {
        if game.daily { return }
        savedLevel = game.level + 1
        let key = "stars_\(game.level)"
        let d = UserDefaults.standard
        if game.stars > d.integer(forKey: key) { d.set(game.stars, forKey: key) }
        starsTotal = computeStarsTotal()
    }

    private func computeStarsTotal() -> Int {
        let d = UserDefaults.standard
        return d.dictionaryRepresentation().keys.filter { $0.hasPrefix("stars_") }.reduce(0) { $0 + d.integer(forKey: $1) }
    }

    private func misses(_ n: Int) -> String { n == 0 ? "aucun coup raté" : n == 1 ? "1 coup raté" : "\(n) coups ratés" }

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
                HudButton(label: "↻", primary: false) { restart(game) }
                VStack(spacing: 0) {
                    Text("SORTIE DE BUS · ★ \(starsTotal)").font(.system(size: 11, weight: .heavy, design: .rounded))
                        .tracking(1).foregroundStyle(inkSoft).lineLimit(1)
                    Text(game.daily ? "Défi du jour" : "Niveau \(game.level)")
                        .font(.system(size: 24, weight: .heavy, design: .rounded))
                        .foregroundStyle(ink).lineLimit(1).minimumScaleFactor(0.6)
                }
                .frame(maxWidth: .infinity)
                HudButton(label: game.daily ? "Niveaux" : "Défi", primary: false) {
                    if game.daily { go(game, max(1, savedLevel)) } else { game.start(game.level, dailySeed: dailySeed()) }
                }
                HudButton(label: "+1", primary: true) { _ = game.useBonus() }
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
                    Overlay(title: "Bravo !", stars: game.stars,
                            text: "\(game.total) passagers en \(game.elapsedSeconds) s, \(misses(game.misses)).",
                            actions: [game.daily ? ("Retour aux niveaux", { go(game, max(1, savedLevel)) })
                                                 : ("Niveau suivant", { go(game, game.level + 1) })])
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
    var stars: Int? = nil
    let text: String
    let actions: [(String, () -> Void)]
    var body: some View {
        ZStack {
            Color(hex: 0x142337, alpha: 0.55)
            VStack(spacing: 12) {
                Text(title).font(.system(size: 32, weight: .heavy, design: .rounded)).foregroundStyle(ink)
                if let stars {
                    Text(starsText(stars)).font(.system(size: 30, weight: .heavy)).tracking(4).foregroundStyle(accent)
                }
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

// MARK: - Drawing (layout units = points). Mirrors the canvas drawing in index.html.

private func rrPath(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double) -> Path {
    let rad = max(0, min(r, w / 2, h / 2))
    return Path(roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerRadius: rad, style: .circular)
}

private func rr(_ ctx: GraphicsContext, _ color: Color, _ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double) {
    ctx.fill(rrPath(x, y, w, h, r), with: .color(color))
}

private func circle(_ ctx: GraphicsContext, _ color: Color, _ x: Double, _ y: Double, _ r: Double) {
    ctx.fill(Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2)), with: .color(color))
}

private func seg(_ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double) -> Path {
    var p = Path()
    p.move(to: CGPoint(x: x0, y: y0)); p.addLine(to: CGPoint(x: x1, y: y1))
    return p
}

private func line(_ ctx: GraphicsContext, _ color: Color, _ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double, _ w: Double) {
    ctx.stroke(seg(x0, y0, x1, y1), with: .color(color), lineWidth: w)
}

private func linear(_ stops: [(Double, Color)], _ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double) -> GraphicsContext.Shading {
    .linearGradient(Gradient(stops: stops.map { Gradient.Stop(color: $0.1, location: $0.0) }),
                    startPoint: CGPoint(x: x0, y: y0), endPoint: CGPoint(x: x1, y: y1))
}

/// Lightens (k > 0) or darkens (k < 0) a colour, like `shade` in index.html.
private func shade(_ hex: UInt32, _ k: Double) -> Color {
    func f(_ c: UInt32) -> Double {
        let v = Double(c)
        return max(0, min(255, (v + (k < 0 ? v * k : (255 - v) * k)).rounded())) / 255
    }
    return Color(.sRGB, red: f((hex >> 16) & 255), green: f((hex >> 8) & 255), blue: f(hex & 255))
}

/// Tileable noise texture drawn with the game's RNG (seed 7), like `noisePattern` in index.html.
private func noiseImage(_ base: UInt32, spread: Double, size: Int, dots: Int) -> Image {
    let space = CGColorSpace(name: CGColorSpace.sRGB)!
    let cg = CGContext(data: nil, width: size, height: size, bitsPerComponent: 8, bytesPerRow: 0, space: space,
                       bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
    let comp = { (s: UInt32) in CGFloat((base >> s) & 255) / 255 }
    cg.setFillColor(CGColor(srgbRed: comp(16), green: comp(8), blue: comp(0), alpha: 1))
    cg.fill(CGRect(x: 0, y: 0, width: size, height: size))
    var rng = Rng(7)
    for _ in 0..<dots {
        let v = ((rng.next() - 0.5) * spread).rounded(.down)
        cg.setFillColor(v > 0 ? CGColor(srgbRed: 1, green: 1, blue: 1, alpha: CGFloat(v / 255)) : CGColor(srgbRed: 0, green: 0, blue: 0, alpha: CGFloat(-v / 255)))
        let x = rng.next() * Double(size), y = rng.next() * Double(size)
        let w = 1 + rng.next() * 1.5, h = 1 + rng.next() * 1.5
        cg.fill(CGRect(x: x, y: y, width: w, height: h))
    }
    return Image(decorative: cg.makeImage()!, scale: 1)
}

private let asphalt = noiseImage(0x59636F, spread: 46, size: 128, dots: 2600)
private let paving = noiseImage(0xE6ECF2, spread: 22, size: 96, dots: 900)

private func tiled(_ img: Image) -> GraphicsContext.Shading {
    .tiledImage(img, origin: .zero, sourceRect: CGRect(x: 0, y: 0, width: 1, height: 1), scale: 1)
}

private func footprint(_ x: Double, _ y: Double, _ ang: Double, _ len: Double, _ wid: Double, _ r: Double) -> Path {
    rrPath(-wid / 2, -len / 2, wid, len, r).applying(CGAffineTransform(translationX: x, y: y).rotated(by: ang))
}

/// Window glass with a highlight; returns the highlight path (the web version strokes it for some models).
@discardableResult
private func glass(_ ctx: GraphicsContext, _ x: Double, _ y: Double, _ w: Double, _ h: Double, _ rad: Double, dark: Bool = false) -> Path {
    ctx.fill(rrPath(x, y, w, h, rad), with: linear([(0, Color(hex: dark ? 0x4D6F93 : 0x9FD2FF)), (1, Color(hex: dark ? 0x16222F : 0x2C4560))], 0, y, 0, y + h))
    let hl = rrPath(x + w * 0.1, y + h * 0.15, w * 0.26, max(1.5, h * 0.22), 2)
    ctx.fill(hl, with: .color(.white.opacity(0.5)))
    return hl
}

private func hood(_ ctx: GraphicsContext, _ len: Double, _ wid: Double, _ P: BusColor, _ r: Double, _ depth: Double) {
    let F = -len / 2
    rr(ctx, shade(P.ch, -0.14), -wid / 2 + 1, F + 1, wid - 2, wid * depth, r * 0.8)
    let st = Color(hex: 0, alpha: 0.18)
    line(ctx, st, -wid * 0.2, F + wid * depth * 0.35, wid * 0.2, F + wid * depth * 0.35, 1)
    line(ctx, st, -wid / 2 + 2, F + wid * depth, wid / 2 - 2, F + wid * depth, 1)
}

/// Roof details per model, in bus-local coords (front = -len/2); `seated` hides roof clutter so riders show.
private func roofDetails(_ ctx: GraphicsContext, _ model: String, _ len: Double, _ wid: Double, _ P: BusColor, _ r: Double, _ seated: Bool) {
    let F = -len / 2, Bk = len / 2
    switch model {
    case "minibus":
        hood(ctx, len, wid, P, r, 0.24)
        glass(ctx, -wid * 0.38, F + wid * 0.28, wid * 0.76, wid * 0.2, wid * 0.07)
        if !seated {
            let st = Color(hex: 0x1E2430, alpha: 0.55), lw = max(1.5, wid * 0.04)
            let y0 = F + wid * 0.62, y1 = Bk - wid * 0.18
            for sx in [-1.0, 1.0] { line(ctx, st, sx * wid * 0.3, y0, sx * wid * 0.3, y1, lw) }
            for k in 0..<4 { let yy = y0 + (y1 - y0) * Double(k) / 3; line(ctx, st, -wid * 0.3, yy, wid * 0.3, yy, lw) }
        }
    case "scolaire":
        hood(ctx, len, wid, P, r, 0.3)
        glass(ctx, -wid * 0.4, F + wid * 0.33, wid * 0.8, wid * 0.18, wid * 0.04)
        for sx in [-1.0, 1.0] { rr(ctx, Color(hex: 0x1E2430), sx * wid * 0.41 - wid * 0.035, F + wid * 0.58, wid * 0.07, len - wid * 0.7, 0) }
        for sx in [-1.0, 1.0] { circle(ctx, Color(hex: 0xFFB020), sx * wid * 0.32, F + wid * 0.56, wid * 0.05) }
        if !seated { rr(ctx, .white.opacity(0.22), -wid * 0.15, Bk - wid * 0.42, wid * 0.3, wid * 0.22, 3) }
    case "navette":
        glass(ctx, -wid * 0.4, F + wid * 0.05, wid * 0.8, wid * 0.3, wid * 0.14)
        if !seated {
            let hl = glass(ctx, -wid * 0.27, F + wid * 0.5, wid * 0.54, len - wid * 0.82, wid * 0.12, dark: true)
            ctx.stroke(hl, with: .color(.white.opacity(0.3)), lineWidth: 1)
        }
    case "articule":
        glass(ctx, -wid * 0.38, F + wid * 0.06, wid * 0.76, wid * 0.24, wid * 0.08)
        let panel = Color.white.opacity(0.18)
        rr(ctx, panel, -wid * 0.3, F + wid * 0.42, wid * 0.6, len * 0.5 - wid * 0.62, wid * 0.12)
        rr(ctx, panel, -wid * 0.3, len * 0.08 + wid * 0.24, wid * 0.6, Bk - len * 0.08 - wid * 0.44, wid * 0.12)
        let jy = len * 0.08 - wid * 0.06, jh = wid * 0.3
        rr(ctx, Color(hex: 0x3A4250), -wid / 2, jy - jh / 2, wid, jh, 0)
        for k in 1..<5 { let yy = jy - jh / 2 + jh * Double(k) / 5; line(ctx, .white.opacity(0.25), -wid / 2, yy, wid / 2, yy, 1) }
    case "imperiale":
        glass(ctx, -wid * 0.4, F + wid * 0.05, wid * 0.8, wid * 0.16, wid * 0.06)
        if !seated {
            let y0 = F + wid * 0.32, y1 = Bk - wid * 0.12
            let deck = rrPath(-wid * 0.42, y0, wid * 0.84, y1 - y0, wid * 0.12)
            ctx.fill(deck, with: .color(Color(hex: 0x2B3340)))
            ctx.stroke(deck, with: .color(.white.opacity(0.75)), lineWidth: 1.2)
            let n = Int(((y1 - y0 - wid * 0.1) / (wid * 0.22)).rounded(.down))
            for k in 0..<max(0, n) {
                for sx in [-1.0, 1.0] { rr(ctx, P.d, sx * wid * 0.2 - wid * 0.13, y0 + wid * 0.08 + Double(k) * wid * 0.22, wid * 0.26, wid * 0.13, 2) }
            }
        }
    case "autocar":
        glass(ctx, -wid * 0.42, F + wid * 0.04, wid * 0.84, wid * 0.34, wid * 0.16, dark: true)
        var stripe = Path()
        stripe.move(to: CGPoint(x: -wid / 2, y: len * 0.05)); stripe.addLine(to: CGPoint(x: wid / 2, y: -len * 0.08))
        stripe.addLine(to: CGPoint(x: wid / 2, y: -len * 0.01)); stripe.addLine(to: CGPoint(x: -wid / 2, y: len * 0.12))
        stripe.closeSubpath()
        ctx.fill(stripe, with: .color(.white.opacity(0.85)))
        if !seated { glass(ctx, -wid * 0.08, F + wid * 0.5, wid * 0.16, len - wid * 0.75, wid * 0.08, dark: true) }
    default: // urbain
        glass(ctx, -wid * 0.38, F + wid * 0.06, wid * 0.76, wid * 0.24, wid * 0.08)
        let panel = rrPath(-wid * 0.3, F + wid * 0.42, wid * 0.6, len - wid * 0.62, wid * 0.14)
        ctx.fill(panel, with: .color(.white.opacity(0.18)))
        ctx.stroke(panel, with: .color(Color(hex: 0, alpha: 0.12)), lineWidth: 1)
        if !seated {
            rr(ctx, shade(P.lh, 0.35), -wid * 0.26, Bk - wid * 0.62, wid * 0.52, wid * 0.4, 3)
            for k in 1..<4 {
                let xx = -wid * 0.26 + wid * 0.52 * Double(k) / 4
                line(ctx, Color(hex: 0, alpha: 0.18), xx, Bk - wid * 0.58, xx, Bk - wid * 0.26, 1)
            }
        }
    }
}

private func head(_ ctx: GraphicsContext, _ x: Double, _ y: Double, _ rad: Double, _ P: BusColor) {
    ctx.fill(Path(ellipseIn: CGRect(x: x - rad, y: y - rad, width: rad * 2, height: rad * 2)),
             with: .radialGradient(Gradient(colors: [P.l, P.d]), center: CGPoint(x: x - rad * 0.35, y: y - rad * 0.4),
                                   startRadius: rad * 0.1, endRadius: rad * 1.2))
}

/// Polished 2D bus: soft shadow, wheels, extruded walls with a window band, lit roof, model details.
private func drawBus(_ base: GraphicsContext, x: Double, y: Double, ang: Double, len: Double, wid: Double, color: Int,
                     model: String, seats: Bool = false, cap: Int = 0, riders: Int = 0, ice: Int = 0) {
    let P = color < 0 ? mysteryColor : palette[color]
    let h = max(4, wid * 0.24), r = wid * 0.26
    // soft shadow (the web canvas shadow is in device pixels; these values match it at 2x)
    var sc = base
    sc.addFilter(.shadow(color: Color(hex: 0x0A121E, alpha: 0.45), radius: wid * 0.11, x: wid * 0.06, y: (h + wid * 0.12) / 2))
    sc.fill(footprint(x, y, ang, len * 0.96, wid * 0.94, r), with: .color(Color(hex: 0x0A121E, alpha: 0.35)))
    // wheels
    var wc = base
    wc.translateBy(x: x, y: y + h); wc.rotate(by: .radians(ang))
    for fy in [-len / 2 + wid * 0.32, len / 2 - wid * 0.42] {
        for sx in [-1.0, 1.0] { rr(wc, Color(hex: 0x1E2430), sx * wid / 2 - wid * 0.09, fy, wid * 0.18, wid * 0.26, 3) }
    }
    // extruded walls: body colour, dark window band, darker skirt
    let steps = Int(h.rounded(.up)), wallTop = shade(P.dh, -0.18)
    for k in stride(from: steps, through: 1, by: -1) {
        let f = Double(k) / Double(steps)
        base.fill(footprint(x, y + Double(k), ang, len, wid, r), with: .color(f > 0.72 ? wallTop : f > 0.3 ? Color(hex: 0x22303F) : P.d))
    }
    base.stroke(footprint(x, y + h * 0.5, ang, len, wid, r), with: .color(Color(.sRGB, red: 160 / 255, green: 210 / 255, blue: 1, opacity: 0.35)), lineWidth: 1)

    var ctx = base
    ctx.translateBy(x: x, y: y); ctx.rotate(by: .radians(ang))
    ctx.fill(rrPath(-wid / 2, -len / 2, wid, len, r),
             with: linear([(0, P.l), (0.35, P.c), (1, shade(P.ch, -0.12))], -wid / 2, 0, wid / 2, 0))
    roofDetails(ctx, model, len, wid, P, r, seats)
    ctx.stroke(rrPath(-wid / 2 + 1, -len / 2 + 1, wid - 2, len - 2, r), with: .color(.white.opacity(0.35)), lineWidth: 1.2)
    if seats {
        let rows2 = cap / 2
        let top = -len / 2 + wid * 0.5, step = (len - wid * 0.78) / Double(rows2), rad = min(wid * 0.14, step * 0.34)
        for i in 0..<cap {
            let cx = (i % 2 == 1 ? 1.0 : -1.0) * wid * 0.15, cy = top + step * (Double(i / 2) + 0.5)
            if i < riders { head(ctx, cx, cy, rad, P) } else { circle(ctx, Color(hex: 0, alpha: 0.2), cx, cy, rad) }
        }
    } else {
        let aw = wid * 0.44, ah = min(len * 0.55, wid * 1.2), cy = wid * 0.14
        var path = Path()
        path.move(to: CGPoint(x: 0, y: cy - ah / 2))
        path.addLine(to: CGPoint(x: aw / 2, y: cy - ah / 2 + aw * 0.55))
        path.addLine(to: CGPoint(x: aw * 0.17, y: cy - ah / 2 + aw * 0.55))
        path.addLine(to: CGPoint(x: aw * 0.17, y: cy + ah / 2))
        path.addLine(to: CGPoint(x: -aw * 0.17, y: cy + ah / 2))
        path.addLine(to: CGPoint(x: -aw * 0.17, y: cy - ah / 2 + aw * 0.55))
        path.addLine(to: CGPoint(x: -aw / 2, y: cy - ah / 2 + aw * 0.55))
        path.closeSubpath()
        var ac = ctx
        ac.addFilter(.shadow(color: Color(hex: 0, alpha: 0.3), radius: 0.75, x: 0, y: 0.75))
        ac.fill(path, with: .color(.white))
    }
    // Badges at the back of the bus are drawn upright whatever the bus direction.
    func upright(_ by: Double) -> CGPoint { CGPoint(x: x - by * sin(ang), y: y + by * cos(ang)) }
    if color < 0 {
        base.draw(Text("?").font(.system(size: (wid * 0.44).rounded(), weight: .heavy, design: .rounded)).foregroundColor(.white),
                  at: upright(len / 2 - wid * 0.42))
    }
    for sx in [-1.0, 1.0] { circle(ctx, Color(hex: 0xFFF6C2), sx * wid * 0.34, -len / 2 + 1.5, wid * 0.06) }
    if ice > 0 {
        let shell = rrPath(-wid / 2 - 3, -len / 2 - 3, wid + 6, len + 6 + h, wid * 0.3)
        ctx.fill(shell, with: linear([(0, Color(hex: 0xEBFAFF, alpha: 0.7)), (0.5, Color(hex: 0xBEE6FF, alpha: 0.35)), (1, Color(hex: 0xEBFAFF, alpha: 0.65))],
                                     -wid / 2, -len / 2, wid / 2, len / 2))
        ctx.stroke(shell, with: .color(.white.opacity(0.95)), lineWidth: 1.5)
        for (px, py, s) in [(-wid * 0.2, -len * 0.18, 0.18), (wid * 0.18, len * 0.05, 0.13)] {
            for a in [0, Double.pi / 3, -Double.pi / 3] {
                line(ctx, .white.opacity(0.8), px - sin(a) * wid * s, py - cos(a) * wid * s, px + sin(a) * wid * s, py + cos(a) * wid * s, 1.6)
            }
        }
        let c = upright(len / 2 - wid * 0.38)
        let badge = Path(ellipseIn: CGRect(x: c.x - wid * 0.24, y: c.y - wid * 0.24, width: wid * 0.48, height: wid * 0.48))
        base.fill(badge, with: .color(Color(hex: 0x1D6FA5)))
        base.stroke(badge, with: .color(.white), lineWidth: 2)
        base.draw(Text("\(ice)").font(.system(size: (wid * 0.3).rounded(), weight: .heavy, design: .rounded)).foregroundColor(.white), at: c)
    }
}

private func drawCone(_ ctx: GraphicsContext, _ x: Double, _ y: Double, _ cs: Double) {
    let s = cs * 0.34
    ctx.fill(Path(ellipseIn: CGRect(x: x + s * 0.3 - s * 1.15, y: y + s * 0.95 - s * 0.38, width: s * 2.3, height: s * 0.76)),
             with: .color(Color(hex: 0x0A121E, alpha: 0.28)))
    rr(ctx, Color(hex: 0x2B313C), x - s, y + s * 0.5, s * 2, s * 0.5, 4)
    rr(ctx, Color(hex: 0x3D4553), x - s, y + s * 0.5, s * 2, s * 0.18, 4)
    var body = Path()
    body.move(to: CGPoint(x: x, y: y - s * 1.25))
    body.addLine(to: CGPoint(x: x + s * 0.72, y: y + s * 0.62))
    body.addQuadCurve(to: CGPoint(x: x - s * 0.72, y: y + s * 0.62), control: CGPoint(x: x, y: y + s * 0.78))
    body.closeSubpath()
    ctx.fill(body, with: linear([(0, Color(hex: 0xFFB066)), (0.4, Color(hex: 0xFF7A1A)), (1, Color(hex: 0xC4520B))], x - s * 0.7, 0, x + s * 0.7, 0))
    var band = Path()
    band.move(to: CGPoint(x: x - s * 0.33, y: y - s * 0.28)); band.addLine(to: CGPoint(x: x + s * 0.33, y: y - s * 0.28))
    band.addLine(to: CGPoint(x: x + s * 0.46, y: y + s * 0.08)); band.addLine(to: CGPoint(x: x - s * 0.46, y: y + s * 0.08))
    band.closeSubpath()
    ctx.fill(band, with: .color(.white.opacity(0.95)))
}

private func drawPerson(_ ctx: GraphicsContext, x: Double, y yIn: Double, s: Double, color: Int, lift: Double) {
    let P = palette[color]
    ctx.fill(Path(ellipseIn: CGRect(x: x + s * 0.15 - s * 0.8, y: yIn + s * 1.08 - s * 0.26, width: s * 1.6, height: s * 0.52)),
             with: .color(Color(hex: 0x0A121E, alpha: 0.22)))
    let y = yIn - lift
    ctx.fill(rrPath(x - s * 0.6, y - s * 0.05, s * 1.2, s * 1.12, s * 0.52),
             with: linear([(0, P.l), (0.45, P.c), (1, P.d)], x - s * 0.6, 0, x + s * 0.6, 0))
    head(ctx, x, y - s * 0.58, s * 0.48, skin)
    var hair = Path()
    hair.addArc(center: CGPoint(x: x, y: y - s * 0.72), radius: s * 0.42, startAngle: .radians(.pi * 1.05), endAngle: .radians(.pi * 1.95), clockwise: false)
    hair.closeSubpath()
    ctx.fill(hair, with: .color(P.d))
}

private func drawGame(_ ctx: GraphicsContext, _ g: Game) {
    let L = g.L
    let W = L.w, H = L.h, now = g.time
    // sidewalk with shelter strip and curb
    ctx.fill(Path(CGRect(x: 0, y: 0, width: W, height: L.sY0)), with: tiled(paving))
    var gx = 0.0
    while gx < W { line(ctx, Color(hex: 0x506482, alpha: 0.10), gx, 0, gx, L.sY0, 1); gx += 22 }
    rr(ctx, Color(hex: 0xC9D2DC), 0, L.sY0 - 6, W, 6, 0)
    // asphalt for the bay and the lot
    ctx.fill(Path(CGRect(x: 0, y: L.sY0, width: W, height: H - L.sY0)), with: tiled(asphalt))
    rr(ctx, Color(hex: 0, alpha: 0.12), 0, L.sY0, W, 4, 0)
    let lotTop = L.sY0 + L.sH
    var mx = 6.0
    while mx < W { rr(ctx, Color(hex: 0xF2C94C), mx, lotTop + 2, 16, 4, 0); mx += 28 }
    // painted grid
    rr(ctx, Color(hex: 0, alpha: 0.12), L.gx0 - 6, L.gy0 - 6, L.gx1 - L.gx0 + 12, L.gy1 - L.gy0 + 12, 12)
    let dash = StrokeStyle(lineWidth: 1.5, dash: [L.cs * 0.28, L.cs * 0.14])
    let grid = Color.white.opacity(0.55)
    for c in 0...g.cols { let x = L.gx0 + Double(c) * L.cs; ctx.stroke(seg(x, L.gy0, x, L.gy1), with: .color(grid), style: dash) }
    for r in 0...g.rows { let y = L.gy0 + Double(r) * L.cs; ctx.stroke(seg(L.gx0, y, L.gx1, y), with: .color(grid), style: dash) }
    // slots
    for j in 0..<maxSlots {
        let s = L.slotCenter(j)
        let x = s.x - L.slotW / 2, y = L.sY0 + 8, h = L.sH - 16
        if j < g.active {
            rr(ctx, Color(hex: 0, alpha: 0.14), x, y, L.slotW, h, 8)
            var corners = Path()
            corners.move(to: CGPoint(x: x, y: y + 10)); corners.addLine(to: CGPoint(x: x, y: y)); corners.addLine(to: CGPoint(x: x + 10, y: y))
            corners.move(to: CGPoint(x: x + L.slotW - 10, y: y)); corners.addLine(to: CGPoint(x: x + L.slotW, y: y)); corners.addLine(to: CGPoint(x: x + L.slotW, y: y + 10))
            ctx.stroke(corners, with: .color(Color(hex: 0xF2C94C)), lineWidth: 3)
            line(ctx, .white.opacity(0.7), x, y + h, x + L.slotW, y + h, 2)
        } else {
            rr(ctx, Color(hex: 0xFFFFFF, alpha: 0.08), x, y, L.slotW, h, 8)
            let lock = Color(hex: 0xD4DAE3)
            var arc = Path()
            arc.addArc(center: CGPoint(x: s.x, y: s.y - 6), radius: 7, startAngle: .degrees(180), endAngle: .degrees(0), clockwise: false)
            ctx.stroke(arc, with: .color(lock), lineWidth: 3)
            rr(ctx, lock, s.x - 10, s.y - 6, 20, 15, 3)
            ctx.draw(Text("+1").font(.system(size: 12, weight: .heavy, design: .rounded)).foregroundColor(lock),
                     at: CGPoint(x: s.x, y: s.y + 22))
        }
    }
    // cones and parked buses, back to front so the raised walls overlap correctly
    var items: [(y: Double, cone: Cell?, bus: Bus?)] = g.cones.map { (L.gy0 + (Double($0.r) + 0.5) * L.cs, $0, nil) }
    for b in g.buses where b.state == .park { items.append((g.parkPose(b).y, nil, b)) }
    items.sort { $0.y < $1.y }
    for it in items {
        if let cone = it.cone { drawCone(ctx, L.gx0 + (Double(cone.c) + 0.5) * L.cs, it.y, L.cs); continue }
        guard let b = it.bus else { continue }
        let p = g.parkPose(b), o = g.parkOffset(b)
        drawBus(ctx, x: p.x + o.x, y: p.y + o.y, ang: p.ang, len: p.len, wid: p.wid, color: b.mystery ? -1 : b.color,
                model: b.model, ice: max(0, b.ice - g.departed))
    }
    // buses waiting in slots
    for b in g.buses where b.state == .slot {
        let s = L.slotCenter(b.slot), d = g.slotDims(b)
        let sh = b.shake > 0 ? sin(now * 1000 / 22) * 3 : 0
        drawBus(ctx, x: s.x + sh, y: s.y, ang: b.ang, len: d.len, wid: d.wid, color: b.color, model: b.model,
                seats: true, cap: b.cap, riders: b.riders)
    }
    // moving buses on top
    for b in g.buses where b.state == .drive || b.state == .leave {
        let d = g.slotDims(b)
        if b.state == .drive {
            let e = b.k * b.k
            drawBus(ctx, x: b.x, y: b.y, ang: b.ang, len: b.fromLen + (d.len - b.fromLen) * e,
                    wid: b.fromWid + (d.wid - b.fromWid) * e, color: b.color, model: b.model)
        } else {
            drawBus(ctx, x: L.slotCenter(b.slot).x, y: b.y, ang: b.ang, len: d.len, wid: d.wid,
                    color: b.color, model: b.model, seats: true, cap: b.cap, riders: b.riders)
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
