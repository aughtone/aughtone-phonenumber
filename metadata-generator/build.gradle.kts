plugins {
    // Version omitted: the Kotlin Gradle plugin is already on the build classpath
    // via the root project's Kotlin Multiplatform plugin, so requesting a version
    // here would conflict.
    kotlin("jvm")
    application
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("io.github.aughtone.phonenumber.tools.GeneratorKt")
}

// Regenerates the embedded metadata Kotlin from the pinned metadata XML into the
// library's commonMain. Run: ./gradlew :metadata-generator:generateMetadata
tasks.register<JavaExec>("generateMetadata") {
    group = "build"
    description = "Generate embedded phone metadata Kotlin from the pinned metadata XML."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("io.github.aughtone.phonenumber.tools.GeneratorKt")
    val mainOut = rootProject.file(
        "phonenumber/src/commonMain/kotlin/io/github/aughtone/phonenumber/generated"
    )
    val testOut = rootProject.file(
        "phonenumber/src/commonTest/kotlin/io/github/aughtone/phonenumber/generated"
    )
    args(mainOut.absolutePath, testOut.absolutePath)
}
