// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "MacApp",
    platforms: [
        .macOS(.v14)
    ],
    products: [
        .library(name: "MacGamepadCore", targets: ["MacGamepadCore"]),
        .executable(name: "MacApp", targets: ["MacApp"]),
    ],
    targets: [
        .target(
            name: "MacGamepadCore",
            dependencies: [],
            path: "Sources/MacGamepadCore"
        ),
        .executableTarget(
            name: "MacApp",
            dependencies: ["MacGamepadCore"],
            path: "Sources/MacApp"
        ),
        .testTarget(
            name: "MacGamepadCoreTests",
            dependencies: ["MacGamepadCore"],
            path: "Tests/MacGamepadCoreTests"
        ),
    ]
)
