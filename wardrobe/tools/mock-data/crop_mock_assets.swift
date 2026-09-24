import Foundation
import CoreGraphics
import ImageIO

func loadImage(_ path: String) -> CGImage {
    let url = URL(fileURLWithPath: path)
    guard let source = CGImageSourceCreateWithURL(url as CFURL, nil),
          let image = CGImageSourceCreateImageAtIndex(source, 0, nil) else {
        fatalError("Unable to load image: \(path)")
    }
    return image
}

func savePNG(_ image: CGImage, to path: String) {
    let url = URL(fileURLWithPath: path)
    try? FileManager.default.removeItem(at: url)
    guard let destination = CGImageDestinationCreateWithURL(url as CFURL, "public.png" as CFString, 1, nil) else {
        fatalError("Unable to create destination: \(path)")
    }
    CGImageDestinationAddImage(destination, image, nil)
    guard CGImageDestinationFinalize(destination) else {
        fatalError("Unable to write image: \(path)")
    }
}

let args = CommandLine.arguments
guard args.count == 3 else {
    fatalError("usage: crop_mock_assets.swift <source-dir> <output-dir>")
}
let sourceDir = args[1]
let outputDir = args[2]
try! FileManager.default.createDirectory(atPath: outputDir, withIntermediateDirectories: true)

let clothingSheets = [
    "exec-cccebaa4-05e3-420b-aa6d-4cf745186e1a.png",
    "exec-6f4b038b-809e-4dc3-9fb3-9af566b1b028.png"
]
var index = 1
for sheet in clothingSheets {
    let image = loadImage("\(sourceDir)/\(sheet)")
    for row in 0..<4 {
        for column in 0..<3 {
            let rect = CGRect(x: column * 362, y: row * 362, width: 362, height: 362)
            guard let crop = image.cropping(to: rect) else { fatalError("crop failed") }
            savePNG(crop, to: String(format: "\(outputDir)/item-%02d.png", index))
            index += 1
        }
    }
}

let accessory = loadImage("\(sourceDir)/exec-6085c354-1a92-46a8-8f23-0b2226e9cc10.png")
index = 25
for row in 0..<4 {
    for column in 0..<3 {
        let rect = CGRect(x: column * 410, y: row * 319, width: 410, height: 319)
        guard let crop = accessory.cropping(to: rect) else { fatalError("crop failed") }
        savePNG(crop, to: String(format: "\(outputDir)/item-%02d.png", index))
        index += 1
    }
}
