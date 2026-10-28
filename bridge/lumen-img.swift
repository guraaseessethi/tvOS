// Image helpers for Lumen, using only macOS ImageIO/CoreGraphics (no third-party libraries).
//
//   lumen-img key  <in> <out.png> [mode]              app icon -> logo on a transparent background
//   lumen-img card <texture.jpg> <out.png> <w> <h> <r>  pastel texture -> rounded painted card
//   lumen-img photocard <photo> <out.png> <w> <h> <r>   photo -> filled, rounded card
//   lumen-img qr <text> <out.png> <size>                 QR code, ink on white
//
// `key` flood-fills the icon's background from its border (so enclosed shapes, like YouTube's
// white play triangle, survive), feathers the edge, trims to the logo and centres it on a
// 576x384 canvas (2x the 288x192 card it is drawn on). Logos whose remaining colour is near-white are drawn in ink (#1C1C1C) so they
// stay readable on the light pastels.

import CoreGraphics
import CoreImage
import Foundation
import ImageIO
import UniformTypeIdentifiers

func load(_ path: String) -> CGImage {
    guard let src = CGImageSourceCreateWithURL(URL(fileURLWithPath: path) as CFURL, nil),
          let img = CGImageSourceCreateImageAtIndex(src, 0, nil) else {
        FileHandle.standardError.write("cannot read \(path)\n".data(using: .utf8)!)
        exit(1)
    }
    return img
}

func save(_ img: CGImage, _ path: String) {
    let dest = CGImageDestinationCreateWithURL(URL(fileURLWithPath: path) as CFURL, UTType.png.identifier as CFString, 1, nil)!
    CGImageDestinationAddImage(dest, img, nil)
    CGImageDestinationFinalize(dest)
}

func rgbaContext(_ w: Int, _ h: Int) -> CGContext {
    CGContext(data: nil, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4,
              space: CGColorSpace(name: CGColorSpace.sRGB)!,
              bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
}

// mode: "color" keeps logo colours (near-white logos go ink); "mono" draws the whole logo in ink;
// "mono-red" draws it in ink but keeps strong reds and the light shapes inside them (YouTube's badge).
func key(_ inPath: String, _ outPath: String, _ mode: String) {
    // Roku's icons are only ~290x218. Upscale 4x with high-quality interpolation first, so the
    // background removal below produces smooth, anti-aliased edges instead of jagged pixels.
    let src = load(inPath)
    let w = src.width * 4, h = src.height * 4
    let ctx = rgbaContext(w, h)
    ctx.interpolationQuality = .high
    ctx.draw(src, in: CGRect(x: 0, y: 0, width: w, height: h))
    let px = ctx.data!.bindMemory(to: UInt8.self, capacity: w * h * 4)

    func rgb(_ i: Int) -> (Double, Double, Double) { (Double(px[i * 4]), Double(px[i * 4 + 1]), Double(px[i * 4 + 2])) }
    func dist(_ a: (Double, Double, Double), _ b: (Double, Double, Double)) -> Double {
        let dr = a.0 - b.0, dg = a.1 - b.1, db = a.2 - b.2
        return (dr * dr + dg * dg + db * db).squareRoot()
    }

    // Background = median of the border ring.
    var border: [Int] = []
    for x in 0..<w { border.append(x); border.append((h - 1) * w + x) }
    for y in 0..<h { border.append(y * w); border.append(y * w + w - 1) }
    func median(_ c: Int) -> Double { border.map { Double(px[$0 * 4 + c]) }.sorted()[border.count / 2] }
    let bg = (median(0), median(1), median(2))

    // Flood fill from the border through pixels close to the background colour.
    let tol = 46.0
    var isBg = [Bool](repeating: false, count: w * h)
    var stack = border.filter { dist(rgb($0), bg) < tol }
    for i in stack { isBg[i] = true }
    while let i = stack.popLast() {
        let x = i % w, y = i / w
        for (nx, ny) in [(x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)] where nx >= 0 && ny >= 0 && nx < w && ny < h {
            let j = ny * w + nx
            if !isBg[j] && dist(rgb(j), bg) < tol { isBg[j] = true; stack.append(j) }
        }
    }

    // Alpha: 0 in the background, feathered by colour distance on the boundary, 1 inside.
    var alpha = [Double](repeating: 1, count: w * h)
    for i in 0..<(w * h) {
        if isBg[i] { alpha[i] = 0; continue }
        let x = i % w, y = i / w
        var edge = false
        for (nx, ny) in [(x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)] where nx >= 0 && ny >= 0 && nx < w && ny < h {
            if isBg[ny * w + nx] { edge = true }
        }
        if edge { alpha[i] = min(1, max(0, (dist(rgb(i), bg) - 12) / 90)) }
    }

    // Near-white logos (mostly light pixels) are drawn in ink to stay readable on pastels.
    var light = 0.0, solid = 0.0
    for i in 0..<(w * h) where alpha[i] > 0.5 {
        let c = rgb(i)
        if (0.2126 * c.0 + 0.7152 * c.1 + 0.0722 * c.2) / 255 > 0.82 { light += 1 }
        solid += 1
    }
    let inkLogo = mode == "mono" || (mode == "color" && solid > 0 && light / solid > 0.45)

    // An ink logo is a silhouette, and logos cut from a dark background have dark letter
    // counters, so background-coloured holes go too. Light backgrounds keep enclosed light
    // shapes (YouTube's white play triangle).
    let bgLum = (0.2126 * bg.0 + 0.7152 * bg.1 + 0.0722 * bg.2) / 255
    if mode != "mono-red" && (inkLogo || bgLum < 0.25) {
        for i in 0..<(w * h) where !isBg[i] && dist(rgb(i), bg) < tol { alpha[i] = 0 }
    }

    // Some icons have leftovers at the edges (Netflix's grey corner wedges). Drop small blobs
    // that touch the icon's border so they don't count as part of the logo.
    var seen = [Bool](repeating: false, count: w * h)
    for start in 0..<(w * h) where alpha[start] > 0.1 && !seen[start] {
        var blob: [Int] = [], queue = [start], touchesEdge = false
        seen[start] = true
        while let i = queue.popLast() {
            blob.append(i)
            let x = i % w, y = i / w
            if x == 0 || y == 0 || x == w - 1 || y == h - 1 { touchesEdge = true }
            for (nx, ny) in [(x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)] where nx >= 0 && ny >= 0 && nx < w && ny < h {
                let j = ny * w + nx
                if alpha[j] > 0.1 && !seen[j] { seen[j] = true; queue.append(j) }
            }
        }
        if touchesEdge && blob.count < w * h / 30 {
            for i in blob { alpha[i] = 0 }
        }
    }

    var minX = w, minY = h, maxX = -1, maxY = -1
    for i in 0..<(w * h) where alpha[i] > 0.1 {
        let x = i % w, y = i / w
        minX = min(minX, x); maxX = max(maxX, x); minY = min(minY, y); maxY = max(maxY, y)
    }
    if maxX < 0 { minX = 0; minY = 0; maxX = w - 1; maxY = h - 1 }

    let cw = maxX - minX + 1, ch = maxY - minY + 1
    let logo = rgbaContext(cw, ch)
    let lp = logo.data!.bindMemory(to: UInt8.self, capacity: cw * ch * 4)
    for y in 0..<ch {
        for x in 0..<cw {
            let i = (y + minY) * w + (x + minX), o = (y * cw + x) * 4
            let a = alpha[i]
            var (r, g, b) = rgb(i)
            if mode == "mono-red" {
                let red = r > 110 && r > g * 1.4 && r > b * 1.4
                let innerLight = dist((r, g, b), bg) < tol
                if !red && !innerLight { r = 28; g = 28; b = 28 }
            } else if inkLogo { r = 28; g = 28; b = 28 }
            lp[o] = UInt8(r * a); lp[o + 1] = UInt8(g * a); lp[o + 2] = UInt8(b * a); lp[o + 3] = UInt8(a * 255)
        }
    }
    let trimmed = logo.makeImage()!

    // Centre on a 2x card canvas (576x384, shown at 288x192), fitting a 424x232 box.
    let W = 576, H = 384
    let s = min(424.0 / Double(cw), 232.0 / Double(ch))
    let dw = Double(cw) * s, dh = Double(ch) * s
    let canvas = rgbaContext(W, H)
    canvas.interpolationQuality = .high
    canvas.draw(trimmed, in: CGRect(x: (Double(W) - dw) / 2, y: (Double(H) - dh) / 2, width: dw, height: dh))
    save(canvas.makeImage()!, outPath)
}

func card(_ inPath: String, _ outPath: String, _ w: Int, _ h: Int, _ r: Double) {
    let tex = load(inPath)
    let ctx = rgbaContext(w, h)
    ctx.interpolationQuality = .high
    ctx.addPath(CGPath(roundedRect: CGRect(x: 0, y: 0, width: w, height: h), cornerWidth: r, cornerHeight: r, transform: nil))
    ctx.clip()
    ctx.draw(tex, in: CGRect(x: 0, y: 0, width: w, height: h))
    save(ctx.makeImage()!, outPath)
}

// A photo cropped to fill a w x h card with rounded corners (Memories card slideshow).
func photoCard(_ inPath: String, _ outPath: String, _ w: Int, _ h: Int, _ r: Double) {
    let img = load(inPath)
    let s = max(Double(w) / Double(img.width), Double(h) / Double(img.height))
    let dw = Double(img.width) * s, dh = Double(img.height) * s
    let ctx = rgbaContext(w, h)
    ctx.interpolationQuality = .high
    ctx.addPath(CGPath(roundedRect: CGRect(x: 0, y: 0, width: w, height: h), cornerWidth: r, cornerHeight: r, transform: nil))
    ctx.clip()
    ctx.draw(img, in: CGRect(x: (Double(w) - dw) / 2, y: (Double(h) - dh) / 2, width: dw, height: dh))
    save(ctx.makeImage()!, outPath)
}

// A crisp QR code (ink on white, quiet zone included), for pairing the phone.
func qr(_ text: String, _ outPath: String, _ size: Int) {
    let f = CIFilter(name: "CIQRCodeGenerator")!
    f.setValue(text.data(using: .utf8), forKey: "inputMessage")
    f.setValue("M", forKey: "inputCorrectionLevel")
    let code = f.outputImage!
    let scale = floor(Double(size) / code.extent.width)
    let big = code.transformed(by: CGAffineTransform(scaleX: scale, y: scale))
    let cg = CIContext().createCGImage(big, from: big.extent)!
    let ctx = rgbaContext(size, size)
    ctx.setFillColor(CGColor(srgbRed: 1, green: 1, blue: 1, alpha: 1)); ctx.fill(CGRect(x: 0, y: 0, width: size, height: size))
    ctx.interpolationQuality = .none
    let o = (Double(size) - big.extent.width) / 2
    ctx.draw(cg, in: CGRect(x: o, y: o, width: big.extent.width, height: big.extent.height))
    save(ctx.makeImage()!, outPath)
}

let args = CommandLine.arguments
if args.count >= 4 && args[1] == "key" {
    key(args[2], args[3], args.count >= 5 ? args[4] : "color")
} else if args.count >= 5 && args[1] == "qr" {
    qr(args[2], args[3], Int(args[4])!)
} else if args.count >= 7 && args[1] == "photocard" {
    photoCard(args[2], args[3], Int(args[4])!, Int(args[5])!, Double(args[6])!)
} else if args.count >= 7 && args[1] == "card" {
    card(args[2], args[3], Int(args[4])!, Int(args[5])!, Double(args[6])!)
} else {
    print("usage: lumen-img key <in> <out.png> [color|mono|mono-red] | card <tex> <out.png> <w> <h> <r> | photocard <photo> <out.png> <w> <h> <r>")
    exit(2)
}
