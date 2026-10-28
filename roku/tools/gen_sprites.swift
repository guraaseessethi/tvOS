// Draws Lumen's game art with CoreGraphics: characters with ink outlines and soft shading,
// obstacles, and seamless parallax strips. Output: PNGs in the folder given as argv[1].
//
//   dog_run_0..3.png, dog_jump.png   176x132  a beagle-style pup (gallop frames + jump)
//   shadow.png                       120x24   soft ground shadow
//   bird_0..2.png                    112x96   a round red bird, wing up / mid / down
//   column_up.png, column_down.png   92x440   rounded painted columns, cap at the gap end
//   haystack.png                     128x92
//   star.png 72x72, drop.png 56x68, basket.png 168x84
//   wheat_vg.png 1400x84, town_night.png 1400x130, hills_night.png 1400x80   (strips tile horizontally)

import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

let out = CommandLine.arguments[1]
try? FileManager.default.createDirectory(atPath: out, withIntermediateDirectories: true)

let INK = CGColor(srgbRed: 0.11, green: 0.11, blue: 0.11, alpha: 1)
func rgb(_ hex: UInt32, _ a: CGFloat = 1) -> CGColor {
    CGColor(srgbRed: CGFloat((hex >> 16) & 255) / 255, green: CGFloat((hex >> 8) & 255) / 255, blue: CGFloat(hex & 255) / 255, alpha: a)
}

// Deterministic randomness so every build draws the same art.
var seed: UInt64 = 0x5EED
func rnd() -> CGFloat { seed = seed &* 6364136223846793005 &+ 1442695040888963407; return CGFloat((seed >> 33) % 1_000_000) / 1_000_000 }
func rnd(_ a: CGFloat, _ b: CGFloat) -> CGFloat { a + (b - a) * rnd() }

func canvas(_ w: Int, _ h: Int, _ draw: (CGContext) -> Void) -> CGImage {
    let c = CGContext(data: nil, width: w, height: h, bitsPerComponent: 8, bytesPerRow: 0,
                      space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
    c.translateBy(x: 0, y: CGFloat(h)); c.scaleBy(x: 1, y: -1)   // top-left origin
    c.setLineCap(.round); c.setLineJoin(.round)
    draw(c)
    return c.makeImage()!
}
func save(_ img: CGImage, _ name: String) {
    let d = CGImageDestinationCreateWithURL(URL(fileURLWithPath: out + "/" + name) as CFURL, UTType.png.identifier as CFString, 1, nil)!
    CGImageDestinationAddImage(d, img, nil); CGImageDestinationFinalize(d)
}
func fillStroke(_ c: CGContext, _ p: CGPath, _ fill: CGColor, _ lw: CGFloat = 3) {
    c.addPath(p); c.setFillColor(fill); c.fillPath()
    c.addPath(p); c.setStrokeColor(INK); c.setLineWidth(lw); c.strokePath()
}
func ellipse(_ x: CGFloat, _ y: CGFloat, _ w: CGFloat, _ h: CGFloat) -> CGPath { CGPath(ellipseIn: CGRect(x: x, y: y, width: w, height: h), transform: nil) }
func rrect(_ x: CGFloat, _ y: CGFloat, _ w: CGFloat, _ h: CGFloat, _ r: CGFloat) -> CGPath {
    CGPath(roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerWidth: r, cornerHeight: r, transform: nil)
}

// MARK: Dog ----------------------------------------------------------------------------------

// A beagle-style pup: tan head and shoulders, dark saddle, white muzzle, chest and legs.
let TAN = rgb(0xC9853F), SADDLE = rgb(0x4E3220), WHITE = rgb(0xFBF6EE), WHITE_SHADE = rgb(0xE4D9C8)
let EAR = rgb(0x6E3F22), TONGUE = rgb(0xF27A8A)

func leg(_ c: CGContext, hip: CGPoint, angle: CGFloat, color: CGColor) {
    c.saveGState()
    c.translateBy(x: hip.x, y: hip.y); c.rotate(by: angle * .pi / 180)
    fillStroke(c, rrect(-8, -6, 16, 38, 8), color)
    fillStroke(c, ellipse(-10, 26, 22, 13), color)    // paw
    c.restoreGState()
}

func dog(front: (CGFloat, CGFloat), back: (CGFloat, CGFloat), bob: CGFloat, ear: CGFloat) -> CGImage {
    canvas(176, 132) { c in
        c.translateBy(x: 0, y: bob)
        leg(c, hip: CGPoint(x: 56, y: 84), angle: back.1, color: WHITE_SHADE)
        leg(c, hip: CGPoint(x: 112, y: 84), angle: front.1, color: WHITE_SHADE)
        // tail, white tip, held high
        let tail = CGMutablePath()
        tail.move(to: CGPoint(x: 44, y: 62)); tail.addQuadCurve(to: CGPoint(x: 26, y: 22), control: CGPoint(x: 22, y: 52))
        c.addPath(tail); c.setStrokeColor(INK); c.setLineWidth(14); c.strokePath()
        c.addPath(tail); c.setStrokeColor(TAN); c.setLineWidth(8); c.strokePath()
        fillStroke(c, ellipse(19, 14, 15, 15), WHITE, 2.5)
        // body: white base, dark saddle, tan shoulders
        let body = rrect(36, 50, 100, 44, 22)
        c.addPath(body); c.setFillColor(WHITE); c.fillPath()
        c.saveGState(); c.addPath(body); c.clip()
        c.addPath(ellipse(96, 44, 48, 44)); c.setFillColor(TAN); c.fillPath()
        c.addPath(ellipse(36, 38, 80, 38)); c.setFillColor(SADDLE); c.fillPath()
        c.addPath(ellipse(52, 44, 40, 10)); c.setFillColor(rgb(0x6B4730)); c.fillPath()   // saddle sheen
        c.addPath(ellipse(40, 82, 96, 20)); c.setFillColor(WHITE_SHADE); c.fillPath()      // belly shade
        c.restoreGState()
        c.addPath(body); c.setStrokeColor(INK); c.setLineWidth(3); c.strokePath()
        leg(c, hip: CGPoint(x: 48, y: 86), angle: back.0, color: WHITE)
        leg(c, hip: CGPoint(x: 104, y: 86), angle: front.0, color: WHITE)
        // head
        let head = ellipse(102, 14, 56, 56)
        c.addPath(head); c.setFillColor(TAN); c.fillPath()
        c.saveGState(); c.addPath(head); c.clip()
        c.addPath(rrect(126, 8, 12, 40, 6)); c.setFillColor(WHITE); c.fillPath()           // white blaze
        c.restoreGState()
        c.addPath(head); c.setStrokeColor(INK); c.setLineWidth(3); c.strokePath()
        fillStroke(c, ellipse(130, 40, 40, 30), WHITE)                                     // muzzle
        fillStroke(c, ellipse(158, 40, 14, 11), INK, 2)                                    // nose
        c.addPath(ellipse(161, 42, 5, 3)); c.setFillColor(rgb(0xFFFFFF, 0.8)); c.fillPath()
        fillStroke(c, ellipse(146, 58, 12, 15), TONGUE, 2.5)                               // tongue
        let smile = CGMutablePath(); smile.move(to: CGPoint(x: 140, y: 58)); smile.addQuadCurve(to: CGPoint(x: 164, y: 54), control: CGPoint(x: 152, y: 64))
        c.addPath(smile); c.setStrokeColor(INK); c.setLineWidth(2.5); c.strokePath()
        c.addPath(ellipse(124, 28, 13, 14)); c.setFillColor(INK); c.fillPath()            // eye
        c.addPath(ellipse(129, 30, 5, 5)); c.setFillColor(rgb(0xFFFFFF)); c.fillPath()
        c.addPath(ellipse(126, 37, 2.5, 2.5)); c.setFillColor(rgb(0xFFFFFF)); c.fillPath()
        c.addPath(ellipse(134, 46, 11, 7)); c.setFillColor(rgb(0xF29B8B, 0.6)); c.fillPath() // blush
        // long floppy ear, swinging with the gallop
        c.saveGState(); c.translateBy(x: 112, y: 26); c.rotate(by: ear * .pi / 180)
        let e = CGMutablePath()
        e.move(to: CGPoint(x: 0, y: 0)); e.addCurve(to: CGPoint(x: -6, y: 46), control1: CGPoint(x: -20, y: 6), control2: CGPoint(x: -22, y: 40))
        e.addCurve(to: CGPoint(x: 12, y: 2), control1: CGPoint(x: 8, y: 52), control2: CGPoint(x: 16, y: 20))
        e.closeSubpath()
        fillStroke(c, e, EAR)
        c.restoreGState()
    }
}

let runFrames: [((CGFloat, CGFloat), (CGFloat, CGFloat), CGFloat, CGFloat)] = [
    ((30, -20), (-24, 28), 0, 8), ((12, -4), (-6, 12), -3, 0), ((-24, 28), (30, -20), 0, -8), ((-6, 12), (12, -4), -3, 0),
]
for (i, f) in runFrames.enumerated() { save(dog(front: f.0, back: f.1, bob: f.2, ear: f.3), "dog_run_\(i).png") }
save(dog(front: (-48, -36), back: (46, 34), bob: -2, ear: 18), "dog_jump.png")

// Soft ground shadow drawn under the characters.
save(canvas(120, 24) { c in
    for i in 0..<8 {
        let k = CGFloat(i) / 8
        c.addPath(ellipse(4 + k * 40, 2 + k * 8, 112 - k * 80, 20 - k * 16)); c.setFillColor(rgb(0x000000, 0.05)); c.fillPath()
    }
}, "shadow.png")

// MARK: Red bird -----------------------------------------------------------------------------

let RED = rgb(0xE5383B), RED_DARK = rgb(0xB5172B), BELLY = rgb(0xF7A08A), BEAK = rgb(0xFF9F1C)

func bird(wing: CGFloat) -> CGImage {
    canvas(112, 96) { c in
        // tail feathers
        for (dy, rot) in [(-8.0, -20.0), (0.0, 0.0), (8.0, 20.0)] as [(CGFloat, CGFloat)] {
            c.saveGState(); c.translateBy(x: 28, y: 52 + dy); c.rotate(by: rot * .pi / 180)
            fillStroke(c, rrect(-22, -6, 26, 12, 6), RED_DARK, 2.5); c.restoreGState()
        }
        // crest
        for (x, r) in [(48.0, -25.0), (56.0, 5.0)] as [(CGFloat, CGFloat)] {
            c.saveGState(); c.translateBy(x: x, y: 24); c.rotate(by: r * .pi / 180)
            fillStroke(c, ellipse(-5, -14, 10, 18), RED, 2.5); c.restoreGState()
        }
        // body
        let body = ellipse(22, 18, 66, 64)
        c.addPath(body); c.setFillColor(RED); c.fillPath()
        c.saveGState(); c.addPath(body); c.clip()
        c.addPath(ellipse(46, 50, 46, 36)); c.setFillColor(BELLY); c.fillPath()
        c.addPath(ellipse(30, 22, 34, 18)); c.setFillColor(rgb(0xFF6B6B, 0.8)); c.fillPath()  // sheen
        c.restoreGState()
        c.addPath(body); c.setStrokeColor(INK); c.setLineWidth(3); c.strokePath()
        // beak
        let beak = CGMutablePath()
        beak.move(to: CGPoint(x: 82, y: 44)); beak.addLine(to: CGPoint(x: 106, y: 52)); beak.addLine(to: CGPoint(x: 82, y: 60)); beak.closeSubpath()
        fillStroke(c, beak, BEAK, 2.5)
        let lip = CGMutablePath(); lip.move(to: CGPoint(x: 84, y: 52)); lip.addLine(to: CGPoint(x: 100, y: 52))
        c.addPath(lip); c.setStrokeColor(INK); c.setLineWidth(2); c.strokePath()
        // eye + brow
        fillStroke(c, ellipse(62, 28, 20, 20), rgb(0xFFFFFF), 2.5)
        c.addPath(ellipse(70, 33, 10, 11)); c.setFillColor(INK); c.fillPath()
        c.addPath(ellipse(74, 35, 4, 4)); c.setFillColor(rgb(0xFFFFFF)); c.fillPath()
        let brow = CGMutablePath(); brow.move(to: CGPoint(x: 62, y: 24)); brow.addLine(to: CGPoint(x: 80, y: 28))
        c.addPath(brow); c.setStrokeColor(INK); c.setLineWidth(3.5); c.strokePath()
        c.addPath(ellipse(76, 50, 9, 6)); c.setFillColor(rgb(0xFF9AA2, 0.8)); c.fillPath()    // cheek
        // wing
        c.saveGState(); c.translateBy(x: 46, y: 56); c.rotate(by: wing * .pi / 180)
        fillStroke(c, ellipse(-22, -10, 40, 22), RED_DARK, 2.5)
        let fold = CGMutablePath(); fold.move(to: CGPoint(x: -14, y: 0)); fold.addLine(to: CGPoint(x: 8, y: 2))
        c.addPath(fold); c.setStrokeColor(rgb(0x7E0F1E)); c.setLineWidth(2); c.strokePath()
        c.restoreGState()
    }
}
for (i, w) in [CGFloat(-38), 0, 32].enumerated() { save(bird(wing: w), "bird_\(i).png") }

// MARK: Obstacles ----------------------------------------------------------------------------

// Rounded painted columns with a wider cap at the gap end, in swirling blue-teal brushwork.
func column(capAtTop: Bool) -> CGImage {
    canvas(92, 440) { c in
        if !capAtTop { c.translateBy(x: 0, y: 440); c.scaleBy(x: 1, y: -1) }
        let shaft = rrect(12, 20, 68, 440, 12), cap = rrect(2, 0, 88, 40, 14)
        for (shape, base) in [(shaft, rgb(0x1F4E6B)), (cap, rgb(0x2A6386))] {
            c.addPath(shape); c.setFillColor(base); c.fillPath()
            c.saveGState(); c.addPath(shape); c.clip()
            for _ in 0..<(shape === cap ? 26 : 120) {
                let x = rnd(0, 92), y = rnd(0, 440)
                let s = CGMutablePath(); s.move(to: CGPoint(x: x, y: y))
                s.addQuadCurve(to: CGPoint(x: x + rnd(10, 22), y: y + rnd(-6, 6)), control: CGPoint(x: x + 8, y: y - rnd(4, 8)))
                c.addPath(s); c.setStrokeColor([rgb(0x2F6E8F), rgb(0x3D88A8), rgb(0x16384D), rgb(0x5FA3BF)][Int(rnd() * 4) % 4])
                c.setLineWidth(rnd(3, 5)); c.strokePath()
            }
            c.addPath(CGPath(rect: CGRect(x: 0, y: 0, width: 26, height: 440), transform: nil)); c.setFillColor(rgb(0xFFFFFF, 0.10)); c.fillPath() // light side
            c.restoreGState()
            c.addPath(shape); c.setStrokeColor(INK); c.setLineWidth(3); c.strokePath()
        }
        c.addPath(rrect(8, 6, 76, 6, 3)); c.setFillColor(rgb(0xFBF7EC, 0.55)); c.fillPath()   // cream rim highlight
    }
}
save(column(capAtTop: true), "column_up.png")
save(column(capAtTop: false), "column_down.png")

save(canvas(128, 92) { c in
    let p = CGMutablePath()
    p.move(to: CGPoint(x: 6, y: 90)); p.addCurve(to: CGPoint(x: 64, y: 6), control1: CGPoint(x: 4, y: 40), control2: CGPoint(x: 30, y: 6))
    p.addCurve(to: CGPoint(x: 122, y: 90), control1: CGPoint(x: 98, y: 6), control2: CGPoint(x: 124, y: 40)); p.closeSubpath()
    c.addPath(p); c.setFillColor(rgb(0xB9772A)); c.fillPath()
    c.saveGState(); c.addPath(p); c.clip()
    for _ in 0..<70 {
        let x = rnd(4, 124), y = rnd(6, 90)
        let s = CGMutablePath(); s.move(to: CGPoint(x: x, y: y)); s.addLine(to: CGPoint(x: x + rnd(-6, 6), y: y - rnd(8, 16)))
        c.addPath(s); c.setStrokeColor([rgb(0xE9B949), rgb(0x8E5420), rgb(0xD9A24A)][Int(rnd() * 3) % 3]); c.setLineWidth(rnd(2, 3.5)); c.strokePath()
    }
    c.addPath(rrect(0, 54, 128, 10, 4)); c.setFillColor(rgb(0x5C3418)); c.fillPath()   // rope band
    c.restoreGState()
    c.addPath(p); c.setStrokeColor(INK); c.setLineWidth(3); c.strokePath()
}, "haystack.png")

// MARK: Star Catcher -------------------------------------------------------------------------

save(canvas(72, 72) { c in
    let p = CGMutablePath()
    for i in 0..<10 {
        let r: CGFloat = i % 2 == 0 ? 31 : 14, a = CGFloat(i) * .pi / 5 - .pi / 2
        let pt = CGPoint(x: 36 + r * cos(a), y: 38 + r * sin(a))
        if i == 0 { p.move(to: pt) } else { p.addLine(to: pt) }
    }
    p.closeSubpath()
    fillStroke(c, p, rgb(0xFFDE03))
    c.addPath(ellipse(26, 22, 10, 7)); c.setFillColor(rgb(0xFFFFFF, 0.75)); c.fillPath()
}, "star.png")

save(canvas(56, 68) { c in
    let p = CGMutablePath()
    p.move(to: CGPoint(x: 28, y: 4)); p.addCurve(to: CGPoint(x: 28, y: 64), control1: CGPoint(x: 6, y: 34), control2: CGPoint(x: 2, y: 64))
    p.addCurve(to: CGPoint(x: 28, y: 4), control1: CGPoint(x: 54, y: 64), control2: CGPoint(x: 50, y: 34)); p.closeSubpath()
    c.addPath(p); c.setFillColor(rgb(0x2C2460)); c.fillPath()
    c.addPath(p); c.setStrokeColor(INK); c.setLineWidth(3); c.strokePath()
    c.addPath(ellipse(16, 36, 8, 12)); c.setFillColor(rgb(0xFFFFFF, 0.55)); c.fillPath()
}, "drop.png")

save(canvas(168, 84) { c in
    let p = CGMutablePath()
    p.move(to: CGPoint(x: 6, y: 20)); p.addLine(to: CGPoint(x: 162, y: 20)); p.addLine(to: CGPoint(x: 140, y: 80)); p.addLine(to: CGPoint(x: 28, y: 80)); p.closeSubpath()
    c.addPath(p); c.setFillColor(rgb(0xC98B3E)); c.fillPath()
    c.saveGState(); c.addPath(p); c.clip()
    for row in stride(from: CGFloat(28), to: 80, by: 12) {
        c.addPath(CGPath(rect: CGRect(x: 0, y: row, width: 168, height: 4), transform: nil)); c.setFillColor(rgb(0xA86E2C)); c.fillPath()
    }
    for col in stride(from: CGFloat(10), to: 168, by: 16) {
        let s = CGMutablePath(); s.move(to: CGPoint(x: col, y: 20)); s.addLine(to: CGPoint(x: col + (84 - col) * 0.14, y: 80))
        c.addPath(s); c.setStrokeColor(rgb(0xE2A85C)); c.setLineWidth(3); c.strokePath()
    }
    c.restoreGState()
    c.addPath(p); c.setStrokeColor(INK); c.setLineWidth(3); c.strokePath()
    fillStroke(c, rrect(2, 12, 164, 16, 8), rgb(0x8B5A2B))   // rim
}, "basket.png")

// MARK: Backgrounds --------------------------------------------------------------------------

// Seamless strips: shapes use sine waves whose period divides the 1400px width.
func strip(_ name: String, _ w: Int, _ h: Int, _ draw: (CGContext) -> Void) { save(canvas(w, h, draw), name) }
func wave(_ x: CGFloat, _ parts: [(CGFloat, CGFloat, CGFloat)]) -> CGFloat {
    parts.reduce(0) { $0 + $1.0 * sin(2 * .pi * $1.1 * x / 1400 + $1.2) }
}

// Foreground wheat in the sunflower painting's palette: dense ochre strokes with pink and green flecks.
strip("wheat_vg.png", 1400, 84) { c in
    let top = CGMutablePath(); top.move(to: CGPoint(x: 0, y: 84))
    for x in stride(from: CGFloat(0), through: 1400, by: 4) { top.addLine(to: CGPoint(x: x, y: 14 + wave(x, [(5, 14, 0.3), (3, 37, 1.0)]))) }
    top.addLine(to: CGPoint(x: 1400, y: 84)); top.closeSubpath()
    c.addPath(top); c.setFillColor(rgb(0xE0A43A)); c.fillPath()
    c.saveGState(); c.addPath(top); c.clip()
    for _ in 0..<1500 {
        let x = rnd(0, 1400), y = rnd(0, 90)
        let s = CGMutablePath(); s.move(to: CGPoint(x: x, y: y)); s.addQuadCurve(to: CGPoint(x: x + rnd(-4, 4), y: y - rnd(10, 20)), control: CGPoint(x: x + rnd(-6, 6), y: y - 6))
        let pal = [rgb(0xF6C453), rgb(0xE9A92F), rgb(0xC98320), rgb(0xFBE29A), rgb(0xF39AA8), rgb(0x9CC46B)]
        let r = rnd(); let i = r < 0.86 ? Int(rnd() * 4) % 4 : (r < 0.94 ? 4 : 5)
        c.addPath(s); c.setStrokeColor(pal[i]); c.setLineWidth(rnd(2.5, 4.5)); c.strokePath()
    }
    c.restoreGState()
}

strip("town_night.png", 1400, 130) { c in
    c.setFillColor(rgb(0x10143A))
    var x: CGFloat = 0
    var houses: [(CGFloat, CGFloat, CGFloat)] = []
    while x < 1400 {
        let w = rnd(46, 90), h = rnd(34, 70)
        if x + w > 1400 { break }
        houses.append((x, w, h)); x += w + rnd(4, 26)
    }
    for (hx, w, h) in houses {
        let p = CGMutablePath()
        p.move(to: CGPoint(x: hx, y: 130)); p.addLine(to: CGPoint(x: hx, y: 130 - h)); p.addLine(to: CGPoint(x: hx + w / 2, y: 130 - h - rnd(14, 26)))
        p.addLine(to: CGPoint(x: hx + w, y: 130 - h)); p.addLine(to: CGPoint(x: hx + w, y: 130)); p.closeSubpath()
        c.addPath(p); c.fillPath()
        for _ in 0..<Int(rnd(1, 4)) {
            c.addPath(CGPath(rect: CGRect(x: hx + rnd(8, w - 18), y: 130 - h + rnd(10, h - 18), width: 9, height: 11), transform: nil))
            c.setFillColor(rgb(0xFFD95A)); c.fillPath(); c.setFillColor(rgb(0x10143A))
        }
    }
    // church spire
    let sp = CGMutablePath(); sp.move(to: CGPoint(x: 700, y: 130)); sp.addLine(to: CGPoint(x: 700, y: 40)); sp.addLine(to: CGPoint(x: 712, y: 4)); sp.addLine(to: CGPoint(x: 724, y: 40)); sp.addLine(to: CGPoint(x: 724, y: 130)); sp.closeSubpath()
    c.addPath(sp); c.fillPath()
}

strip("hills_night.png", 1400, 80) { c in
    let p = CGMutablePath(); p.move(to: CGPoint(x: 0, y: 80))
    for x in stride(from: CGFloat(0), through: 1400, by: 4) { p.addLine(to: CGPoint(x: x, y: 30 + wave(x, [(12, 2, 0.5), (6, 6, 1.1)]))) }
    p.addLine(to: CGPoint(x: 1400, y: 80)); p.closeSubpath()
    c.addPath(p); c.setFillColor(rgb(0x0B2233)); c.fillPath()
    c.saveGState(); c.addPath(p); c.clip()
    for _ in 0..<220 {
        let x = rnd(0, 1400), y = rnd(20, 80)
        let s = CGMutablePath(); s.move(to: CGPoint(x: x, y: y)); s.addQuadCurve(to: CGPoint(x: x + rnd(14, 26), y: y), control: CGPoint(x: x + 8, y: y - 6))
        c.addPath(s); c.setStrokeColor(rgb(0x1E4A5A, 0.8)); c.setLineWidth(3); c.strokePath()
    }
    c.restoreGState()
}

print("sprites written to \(out)")
