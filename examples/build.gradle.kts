// SPDX-License-Identifier: GPL-3.0-or-later
plugins {
    alias(libs.plugins.kotlin.jvm)
    application
    alias(libs.plugins.detekt)
    alias(libs.plugins.spotless)
    alias(libs.plugins.tapmoc)
}

group = "br.usp.poli.lta.cereda"
version = "1.2.0"

tapmoc {
    java(11)
    kotlin("2.4.0")
    checkDependencies()
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":"))
    // Backend chosen at runtime only: examples must print logs with zero config.
    runtimeOnly(libs.slf4j.simple)

    testImplementation(kotlin("test"))
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

detekt {
    config.from(files("../detekt-config.yml"))
    buildUponDefaultConfig = false
}

// Same formatting gates as the root module: ktlint, 120 columns, SPDX header.
spotless {
    kotlinGradle {
        licenseHeader(LicenseDescriptor.COMMENT, LicenseDescriptor.DELIMITER)
        target("build.gradle.kts")
        ktlint()
        trimTrailingWhitespace()
        leadingTabsToSpaces()
        endWithNewline()
    }
    kotlin {
        target("src/**/*.kt")
        licenseHeader(LicenseDescriptor.COMMENT)
        ktlint()
            .editorConfigOverride(
                mapOf(
                    "max_line_length" to 120,
                ),
            )
        trimTrailingWhitespace()
        leadingTabsToSpaces()
        endWithNewline()
    }
}

private object LicenseDescriptor {
    const val COMMENT = "// SPDX-License-Identifier: GPL-3.0-or-later"
    const val DELIMITER = "(package|import|plugins) "
}

tasks.named("check") {
    dependsOn("detekt")
}

application {
    mainClass.set(findProperty("main")?.toString() ?: "br.usp.poli.lta.cereda.kdaptive.examples.RegularLanguageExampleKt")
}
