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
            url: "https://github.com/aughtone/aughtone-phonenumber/releases/download/v0.0.1/AOPhoneNumberKit.xcframework.zip",
            checksum: "e8e939ae736599d5c221471d90afd9804c134607e7bc5a40e4cd25f37b197850"
        )
    ]
)
