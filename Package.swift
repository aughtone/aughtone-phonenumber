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
            checksum: "5b3eb2a3742e0506b0b8913b54b5d8275a54e0c64bf1fd6b6d8defaa7335840c"
        )
    ]
)
