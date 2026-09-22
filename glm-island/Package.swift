// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "glm-island",
    platforms: [
        .macOS(.v14)
    ],
    targets: [
        .executableTarget(
            name: "GlmIsland",
            path: "Sources/GlmIsland"
        ),
        .testTarget(
            name: "GlmIslandTests",
            dependencies: ["GlmIsland"],
            path: "Tests/GlmIslandTests"
        ),
    ]
)
