// SPDX-License-Identifier: GPL-3.0-or-later
plugins {
    alias(libs.plugins.kotlin.jvm)
    application
    alias(libs.plugins.detekt)
    alias(libs.plugins.spotless)
    alias(libs.plugins.tapmoc)
}

group = "br.usp.poli.lta.cereda"
version = rootProject.version

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
    // The whitespace steps come before ktlint: a file that ends with an extra
    // blank line makes a ktlint rule fail with an exception, and the step
    // that fixes the file is the one that has to run first.
    kotlinGradle {
        licenseHeader(LicenseDescriptor.COMMENT, LicenseDescriptor.DELIMITER)
        target("build.gradle.kts")
        trimTrailingWhitespace()
        leadingTabsToSpaces()
        endWithNewline()
        replaceRegex(TrailingNewlines.STEP, TrailingNewlines.BLANK_LINES, TrailingNewlines.SINGLE)
        ktlint()
    }
    kotlin {
        target("src/**/*.kt")
        licenseHeader(LicenseDescriptor.COMMENT)
        trimTrailingWhitespace()
        leadingTabsToSpaces()
        endWithNewline()
        replaceRegex(TrailingNewlines.STEP, TrailingNewlines.BLANK_LINES, TrailingNewlines.SINGLE)
        ktlint()
            .editorConfigOverride(
                mapOf(
                    "max_line_length" to 120,
                ),
            )
    }
}

private object LicenseDescriptor {
    const val COMMENT = "// SPDX-License-Identifier: GPL-3.0-or-later"
    const val DELIMITER = "(package|import|plugins) "
}

/** The text the trailing blank lines of a file are normalised to. */
private object TrailingNewlines {
    /** The name the step reports itself under. */
    const val STEP = "trailing-blank-lines"

    /** The blank lines at the end of a file, which ktlint cannot digest. */
    const val BLANK_LINES = """\n+\z"""

    /** The single newline every file ends with. */
    const val SINGLE = "\n"
}

tasks.named("check") {
    dependsOn("detekt")
}

application {
    mainClass.set(
        findProperty("main")?.toString() ?: "br.usp.poli.lta.cereda.kdaptive.examples.RegularLanguageExampleKt",
    )
}
