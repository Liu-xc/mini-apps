// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "island",
    platforms: [
        .macOS(.v14)
    ],
    targets: [
        .executableTarget(
            name: "Island",
            path: "Sources/Island"
        ),
        .testTarget(
            name: "IslandTests",
            dependencies: ["Island"],
            path: "Tests/IslandTests"
        ),
    ]
)
