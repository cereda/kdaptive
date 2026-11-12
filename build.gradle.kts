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
    alias(libs.plugins.binaryCompatibilityValidator)
}

group = "br.usp.poli.lta.cereda"
version = "5.1.0"

java {
    // The library ships its sources next to the jar: the jar is the
    // artefact, the sources are what make it readable.
    withSourcesJar()
}

tapmoc {
    java(11)
    kotlin("2.4.0")
    checkDependencies()
}

kotlin {
    // The library follows the conventions the Kotlin documentation gives to
    // the authors of a library: every public member names its visibility and
    // its return type, and every public member carries its documentation.
    //
    // The mode is a warning for now, and a rule from 6.0.0 onwards: the
    // warning measures the work that remains, and the rule keeps it done.
    //
    // The language, the API and the Java target are *not* set here: tapmoc
    // owns them, and a build that states them twice states them twice.
    explicitApiWarning()
}

// The binary-compatibility baseline guards the *library*: it is the artefact
// that ships, and its consumers are the ones a break would hurt. The examples
// and the benchmarks are executables with no consumers, so a baseline there
// would guard nothing and would only add a maintenance step.
apiValidation {
    ignoredProjects.add("examples")
    ignoredProjects.add("benchmarks")
}

repositories {
    mavenCentral()
}

spotless {
    // The steps run in the order they are declared, and the order matters.
    //
    // The trailing newlines are normalised *before* ktlint runs: a file that
    // ends with an extra blank line makes a ktlint rule fail with an
    // exception rather than with a message, and the step that fixes the file
    // is the one that has to run first.
    kotlinGradle {
        with(LicenseDescriptor) {
            licenseHeader(COMMENT, DELIMITER)
        }
        target("build.gradle.kts", "settings.gradle.kts")
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

dependencies {
    // Logging facade only: the concrete backend is the consumer's choice;
    // slf4j-simple is provided here just to keep library test runs quiet.
    implementation(libs.slf4j.api)
    testRuntimeOnly(libs.slf4j.simple)

    testImplementation(kotlin("test"))
    // The null-free sweep reads the nullability of the public API, which
    // only the Kotlin reflection knows about.
    testImplementation(kotlin("reflect"))
}

tasks.named<Test>("test") {
    useJUnitPlatform()

    // The null-free sweep reads the declarations that carry a reified type
    // parameter from the sources: Kotlin's reflection does not report them,
    // and they are the only declarations that could hide a nullable type
    // from the reflective pass.
    systemProperty(
        "kdaptive.sourceRoot",
        layout.projectDirectory
            .dir("src/main/kotlin")
            .asFile.absolutePath,
    )

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
                // Kotlin's `jvm-default=enable` mode — the default since Kotlin
                // 2.2 — emits a synthetic `X$DefaultImpls` class holding the
                // binary-compatibility bridges of the interfaces. Nothing can
                // call it, so it is unreachable by construction; the real
                // default bodies live in the interface itself, which stays in
                // the report. The `jvm-default=all` mode of 6.0.0 removes the
                // class, and the exclusion with it.
                classes("*\$DefaultImpls")
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

/** The text the trailing blank lines of a file are normalised to. */
private object TrailingNewlines {
    /** The name the step reports itself under. */
    const val STEP = "trailing-blank-lines"

    /** The blank lines at the end of a file, which ktlint cannot digest. */
    const val BLANK_LINES = """\n+\z"""

    /** The single newline every file ends with. */
    const val SINGLE = "\n"
}
