import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import org.jetbrains.kotlin.gradle.tasks.KotlinCompileCommon
import java.security.MessageDigest

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.multiplatformLibrary)
    alias(libs.plugins.vanniktech.mavenPublish)
}

val hasWatchosSimulator = try {
    val output = providers.exec {
        commandLine("xcrun", "simctl", "list", "runtimes", "watchos", "--json")
    }.standardOutput.asText.get()
    output.contains("\"isAvailable\" : true")
} catch (e: Exception) {
    false
}

val hasTvosSimulator = try {
    val output = providers.exec {
        commandLine("xcrun", "simctl", "list", "runtimes", "tvos", "--json")
    }.standardOutput.asText.get()
    output.contains("\"isAvailable\" : true")
} catch (e: Exception) {
    false
}

group = libs.versions.group.get()
version = libs.versions.versionName.get()

//noinspection WrongGradleMethod
kotlin {
    jvmToolchain(17)

    jvm()

    android {
        namespace = libs.versions.namespace.get()
        compileSdk = libs.versions.android.compileSdk.get().toInt()
    }

    // See: https://kotlinlang.org/docs/js-project-setup.html
    js {
        browser {
            generateTypeScriptDefinitions()
            webpackTask {
                output.libraryTarget = "commonjs2"
            }
        }
        nodejs() // headless test runner (no browser needed for CI/`*NodeTest`)
        useEsModules() // Enables ES2015 modules
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
        }
        nodejs() // headless test runner; proves wasmJs runtime output is byte-identical
    }

    val xcf = XCFramework("AOPhoneNumberKit")
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
        macosArm64(),
        tvosArm64(),
        tvosSimulatorArm64(),
        watchosArm32(),
        watchosArm64(),
        watchosSimulatorArm64()
    ).forEach { target ->
        target.binaries.framework {
            baseName = "AOPhoneNumberKit"
            isStatic = true
            binaryOption(
                "bundleId",
                libs.versions.namespace.get()
            )
            binaryOption(
                "bundleShortVersionString",
                libs.versions.versionName.get()
            )
            xcf.add(this)
        }
    }

    linuxX64()
    linuxArm64()
    mingwX64()

    sourceSets {
        val wasmJsMain by getting {
            dependencies {
            }
        }
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
        // JVM-only: the reference implementation, for ground-truth correctness
        // checks (compare our E.164 output against libphonenumber Java).
        val jvmTest by getting {
            dependencies {
                implementation(libs.libphonenumber.java)
            }
        }
    }

    tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest>().configureEach {
        if (name.contains("watchosSimulator", ignoreCase = true) && !hasWatchosSimulator) {
            enabled = false
        }
        if (name.contains("tvosSimulator", ignoreCase = true) && !hasTvosSimulator) {
            enabled = false
        }
    }

    metadata {
        compilations.all {
            val compilationName = rootProject.name
            compileTaskProvider.configure {
                if (this is KotlinCompileCommon) {
                    moduleName = "${project.group}:${project.name}_$compilationName"
                }
            }
        }
    }
}

// Automatically sync Package.swift version and checksum when Gradle is synced or built
tasks.register("syncPackageSwift") {
    group = "publishing"
    description = "Syncs version and checksum in Package.swift"
    
    val versionValue = libs.versions.versionName.get()
    val packageFile = rootProject.file("Package.swift")
    val zipFile = project.layout.buildDirectory.file("XCFrameworks/release/AOPhoneNumberKit.xcframework.zip")
    
    doLast {
        if (!packageFile.exists()) return@doLast
        
        var content = packageFile.readText()
        
        // 1. Update Version
        content = content.replace(
            Regex("download/v[^/]*/"),
            "download/v${versionValue}/"
        )
        
        // 2. Update Checksum (if zip exists)
        val file = zipFile.get().asFile
        if (file.exists()) {
            val bytes = file.readBytes()
            val checksum = MessageDigest.getInstance("SHA-256")
                .digest(bytes)
                .joinToString("") { byte: Byte -> "%02x".format(byte) }
            
            content = content.replace(
                Regex("checksum: \".*\""),
                "checksum: \"$checksum\""
            )
            println("Updated Package.swift checksum to $checksum")
        } else {
            println("Zip not found at ${file.path}. Run 'assembleAOPhoneNumberKitXCFramework' first to update checksum.")
        }
        
        packageFile.writeText(content)
        println("Updated Package.swift version to $versionValue")
    }
}

// Hook into Gradle Sync and build process
afterEvaluate {
    // Sync version on every Gradle import
    tasks.named("prepareKotlinIdeaImport").configure {
        dependsOn("syncPackageSwift")
    }
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)

    if (!project.hasProperty("skip-signing")) {
        signAllPublications()
    }

    coordinates(group.toString(), "phonenumber", version.toString())

    pom {
        name = "Phone Number"
        description = "Aught One's Kotlin Multiplatform port of Google's libphonenumber"
        inceptionYear = "2026"
        url = "https://github.com/aughtone/aughtone-phonenumber"
        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0"
                distribution = "https://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }
        developers {
            developer {
                id = "bpappin"
                name = "Brill pappin"
                url = "https://github.com/bpappin"
            }
        }
        scm {
            url = "https://github.com/aughtone/aughtone-phonenumber"
            connection = "https://github.com/aughtone/aughtone-phonenumber.git"
            developerConnection = "git@github.com:aughtone/aughtone-phonenumber.git"
        }
    }
}
