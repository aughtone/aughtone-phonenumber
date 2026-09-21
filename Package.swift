// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "AOPhoneNumberKit",
    platforms: [
        .iOS(.v13),
        .macOS(.v10_15),
        .tvOS(.v13),
        .watchOS(.v6)
    ],
    products: [
        .library(
            name: "AOPhoneNumberKit",
            targets: ["AOPhoneNumberKit"])
    ],
    targets: [
        .binaryTarget(
            name: "AOPhoneNumberKit",
            url: "https://github.com/aughtone/aughtone-phonenumber/releases/download/v0.0.4/AOPhoneNumberKit.xcframework.zip",
            checksum: "c39a99bb7db9a23f52cd5331d1bd9c38f6cd28b25e787d9465d93345fb4ddbe6"
        )
    ]
)
