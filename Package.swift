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
            url: "https://github.com/aughtone/aughtone-phonenumber/releases/download/v0.0.3/AOPhoneNumberKit.xcframework.zip",
            checksum: "fa0d7ec4892c18fe07f551a72398fca027db84aed1bf004d9ae9a1e5b8bbb068"
        )
    ]
)
