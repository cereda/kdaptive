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
    alias(libs.plugins.spotless.changelog)
    alias(libs.plugins.tapmoc)
    alias(libs.plugins.binary.compatibility.validator)
}

// The changelog is the single source of truth of the version: the plugin
// reads it and derives the version of the release it describes. A release
// is declared by promoting the `[Unreleased]` section to a version heading;
// the build reads the heading, and the tag names it.
//
// The configuration of the extension has to come before its first use — the
// plugin computes the version lazily and refuses to be reconfigured
// afterwards — so the block sits here, right after the plugins.
spotlessChangelog {
    changelogFile("CHANGELOG.md")
    // Every build but the release one carries the `-SNAPSHOT` suffix: the
    // release build says so with `-Prelease=true`.
    setAppendDashSnapshotUnless_dashPrelease(true)
    // The major bump recognizes the documented phrase and the bold marker
    // of the current changelog convention; the match is a plain, case
    // sensitive contains, so the spellings cover the phrase in prose, at
    // sentence start, and the marker with or without its colon — the
    // marker alone would miss `**BREAKING:**`, which is the spelling the
    // changelog itself uses.
    ifFoundBumpBreaking(
        "breaking change",
        "Breaking change",
        "**Breaking",
        "**BREAKING",
    )
    tagPrefix("v")
    commitMessage("Release v{{version}}")
    remote("origin")
    branch("main")
}

group = "br.usp.poli.lta.cereda"
version = spotlessChangelog.versionNext

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
    // The language, the API and the Java target are *not* set here: tapmoc
    // owns them, and a build that states them twice states them twice.
    explicitApi()

    compilerOptions {
        // The default methods of an interface live in the interface itself,
        // and the synthetic `DefaultImpls` class that held them is gone. The
        // class was never callable: nothing could reach it but the compiler.
        //
        // `no-compatibility` is the mode that drops the class; `enable` would
        // keep it for the sake of the consumers compiled against the older
        // layout, and this is a breaking release that says so.
        freeCompilerArgs.add("-jvm-default=no-compatibility")
    }
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
