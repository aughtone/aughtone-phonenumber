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
            url: "https://github.com/aughtone/aughtone-phonenumber/releases/download/v0.0.2/AOPhoneNumberKit.xcframework.zip",
            checksum: "d38a96168cecbbcac510edd9b48952e78a575eaabed884c28a6c9c85e7e8b4e5"
        )
    ]
)
