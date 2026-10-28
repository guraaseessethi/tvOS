// Bakes each home wallpaper into one image: the sharp painting, its blur faded in from
// y=497 to y=691 (blur_mask.png), the top-left scrim twice (1 + 0.6) and the bottom scrim,
// both tinted with the theme's deep colour (spec 3.2, 6.1). One full-screen layer instead of
// seventeen keeps the TV's render loop light.
//
//   gen-home-walls <assets/images> <out dir>   ->  wall_<key>_home.jpg (1920x1080)

import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

let src = CommandLine.arguments[1], out = CommandLine.arguments[2]
try? FileManager.default.createDirectory(atPath: out, withIntermediateDirectories: true)

let themes: [(String, UInt32)] = [("starry", 0x14184A), ("sunflower", 0x703E00), ("irises", 0x2C2460),
                                  ("blossom", 0x0A464E), ("snow", 0x2E3068)]
let W = 1920, H = 1080
let space = CGColorSpace(name: CGColorSpace.sRGB)!

func load(_ p: String) -> CGImage { CGImageSourceCreateImageAtIndex(CGImageSourceCreateWithURL(URL(fileURLWithPath: p) as CFURL, nil)!, 0, nil)! }
func layer() -> CGContext {
    let c = CGContext(data: nil, width: W, height: H, bitsPerComponent: 8, bytesPerRow: 0, space: space,
                      bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
    c.interpolationQuality = .high
    return c
}
let full = CGRect(x: 0, y: 0, width: W, height: H)

// A white-alpha image recoloured to `color`, keeping its alpha.
func tinted(_ img: CGImage, _ color: CGColor) -> CGImage {
    let c = layer()
    c.draw(img, in: full)
    c.setBlendMode(.sourceIn)
    c.setFillColor(color); c.fill(full)
    return c.makeImage()!
}

let mask = load(src + "/ui/blur_mask.png")
let scrimTL = load(src + "/ui/scrim_topleft.png")
let scrimB = load(src + "/ui/scrim_bottom.png")

for (key, hex) in themes {
    let deep = CGColor(colorSpace: space, components: [CGFloat((hex >> 16) & 255) / 255, CGFloat((hex >> 8) & 255) / 255, CGFloat(hex & 255) / 255, 1])!
    // Blur limited to the mask's alpha ramp.
    let b = layer()
    b.draw(load(src + "/wall/wall_\(key)_blur.jpg"), in: full)
    b.setBlendMode(.destinationIn)
    b.draw(mask, in: full)
    let blur = b.makeImage()!

    let c = layer()
    c.draw(load(src + "/wall/wall_\(key).jpg"), in: full)
    c.draw(blur, in: full)
    let tl = tinted(scrimTL, deep)
    c.draw(tl, in: full)
    c.setAlpha(0.6); c.draw(tl, in: full); c.setAlpha(1)
    c.draw(tinted(scrimB, deep), in: full)

    let path = out + "/wall_\(key)_home.jpg"
    let d = CGImageDestinationCreateWithURL(URL(fileURLWithPath: path) as CFURL, UTType.jpeg.identifier as CFString, 1, nil)!
    CGImageDestinationAddImage(d, c.makeImage()!, [kCGImageDestinationLossyCompressionQuality: 0.88] as CFDictionary)
    CGImageDestinationFinalize(d)
}
print("home wallpapers written to \(out)")
