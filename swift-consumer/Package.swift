// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "SwiftConsumer",
    platforms: [.macOS(.v15), .iOS(.v18)],
    targets: [
        .binaryTarget(
            name: "NetworkingMiddleKotlin",
            path: "build/NetworkingMiddleKotlin.xcframework"
        ),
        .testTarget(
            name: "NetworkingMiddleKotlinTests",
            dependencies: ["NetworkingMiddleKotlin"]
        ),
    ],
    swiftLanguageModes: [.v6]
)
