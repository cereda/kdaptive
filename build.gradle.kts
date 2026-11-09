// SPDX-License-Identifier: GPL-3.0-or-later
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.dokka.gradle.tasks.DokkaGeneratePublicationTask

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.detekt)
    alias(libs.plugins.dokka)
    alias(libs.plugins.kover)
    alias(libs.plugins.spotless)
    alias(libs.plugins.tapmoc)
}

group = "br.usp.poli.lta.cereda"
version = "1.0.0"

tapmoc {
    java(11)
    kotlin("2.4.0")
    checkDependencies()
}

repositories {
    mavenCentral()
}

spotless {
    kotlinGradle {
        with(LicenseDescriptor) {
            licenseHeader(COMMENT, DELIMITER)
        }
        target("build.gradle.kts", "settings.gradle.kts")
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

dependencies {
    // Logging facade only: the concrete backend is the consumer's choice;
    // slf4j-simple is provided here just to keep library test runs quiet.
    implementation(libs.slf4j.api)
    testRuntimeOnly(libs.slf4j.simple)

    testImplementation(kotlin("test"))
}

tasks.named<Test>("test") {
    useJUnitPlatform()

    testLogging {
        events(
            TestLogEvent.FAILED,
            TestLogEvent.PASSED,
            TestLogEvent.SKIPPED,
            TestLogEvent.STANDARD_ERROR,
            TestLogEvent.STANDARD_OUT,
        )
    }
}

detekt {
    config.from(files("detekt-config.yml"))
    buildUponDefaultConfig = false
}

tasks.named("check") {
    dependsOn("detekt")
    dependsOn("koverVerify")
    dependsOn("dokkaGenerateHtml")
}

// Coverage gates: every check run verifies the library stays above these floors.
kover {
    reports {
        filters {
            excludes {
                // Logging facade: exercised transitively by every other test run.
                packages("br.usp.poli.lta.cereda.kdaptive.logging")
            }
        }
        verify {
            rule {
                // Line coverage floor (LINE is the default coverage unit).
                minBound(95)
            }
            rule {
                bound {
                    coverageUnits.set(CoverageUnit.BRANCH)
                    minValue = 78
                }
            }
        }
    }
}

dokka {
    moduleName.set(project.name)

    dokkaPublications.html {
        suppressInheritedMembers.set(true)
        suppressObviousFunctions.set(true)
        failOnWarning.set(true)
    }

    dokkaSourceSets.all {
        jdkVersion.set(11)
        moduleName.set(project.name)
        skipDeprecated.set(false)
        reportUndocumented.set(true)
        skipEmptyPackages.set(true)

        sourceLink {
            localDirectory.set(file("./"))
            remoteLineSuffix.set("#L")
            remoteUrl("https://github.com/cereda/kdaptive")
        }

        enableKotlinStdLibDocumentationLink.set(true)
        enableJdkDocumentationLink.set(true)
    }
}

private object LicenseDescriptor {
    const val COMMENT = "// SPDX-License-Identifier: GPL-3.0-or-later"
    const val DELIMITER = "(package|import|open|module|plugins|pluginManagement) "
}
