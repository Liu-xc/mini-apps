// swift-tools-version:6.0
import PackageDescription

let package = Package(
    name: "Posthouse",
    platforms: [.macOS(.v14)],
    targets: [
        .executableTarget(
            name: "Posthouse",
            path: "Sources/Posthouse"
        ),
        .testTarget(
            name: "PosthouseTests",
            dependencies: ["Posthouse"],
            path: "Tests/PosthouseTests"
        )
    ],
    swiftLanguageModes: [.v5]
)
