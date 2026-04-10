import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.jvm.toolchain.JavaLanguageVersion

/**
 * Canonical Java Convention Plugin
 *
 * @doc.type convention-plugin
 * @doc.purpose Applies Java 21 toolchain, UTF-8 encoding, compiler safety flags,
 *              JUnit Platform test execution, structured test logging, JAR manifest
 *              metadata, and a guard against deprecated shared:* module references.
 * @doc.layer build
 * @doc.pattern Convention
 *
 * Apply to any Java module that needs standard Java 21 compilation + test setup:
 *
 *   plugins {
 *       id("java-library")
 *       id("com.ghatana.java-conventions")
 *   }
 *
 * Options:
 *   -PenableJavadoc=true    Re-enables Javadoc generation (disabled by default for speed).
 *
 * Supersedes:
 *   com.ghatana.unified-java-conventions  (merged 2026-04-08)
 */

plugins {
    java
}

// ── Java 21 Toolchain ─────────────────────────────────────────────────────────
java {
    if (!toolchain.languageVersion.isPresent) {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    }
    // Explicit sourceCompatibility / targetCompatibility for IDE compatibility
    // (JetBrains IDEs respect these even when toolchain is set)
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

// ── Compiler Configuration ────────────────────────────────────────────────────
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.isIncremental = true
    options.compilerArgs.addAll(
        listOf(
            "-parameters",          // Preserve parameter names (required by ActiveJ, Jackson)
            "-Xlint:unchecked",     // Warn on unchecked casts
            "-Xlint:deprecation",   // Warn on deprecated API usage
            "-Xlint:-processing",   // Silence annotation-processor noise
            "-Xlint:-serial"        // Silence missing serialVersionUID warnings
        )
    )
}

// ── Javadoc ───────────────────────────────────────────────────────────────────
tasks.withType<Javadoc>().configureEach {
    val enableJavadoc = project.findProperty("enableJavadoc")?.toString()?.toBoolean() ?: false
    options.encoding = "UTF-8"
    (options as? StandardJavadocDocletOptions)?.apply {
        addStringOption("Xdoclint:none", "-quiet")
        addBooleanOption("html5", true)
    }

    runCatching {
        setSource(
            project.extensions.getByType(JavaPluginExtension::class.java)
                .sourceSets.getByName("main").allJava
        )
    }

    if (enableJavadoc) {
        logger.lifecycle("Javadoc enabled for project $path (enableJavadoc=true). Generated sources excluded.")
    } else {
        isEnabled = false
    }
}

// ── Test Platform ─────────────────────────────────────────────────────────────
// JUnit Platform is the canonical test runtime.  Tag-based integration-test
// filtering is handled separately by com.ghatana.integration-test-profile.
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showCauses = true
        showStackTraces = true
        showStandardStreams = false   // Keep console output clean; set to true per-module if needed
    }
    // Testcontainers / Docker Desktop 29+ compatibility
    // Docker Desktop 29 rejects docker-java's default API version (1.24).
    // Setting api.version=1.44 works cross-platform (Linux Docker accepts any version).
    jvmArgs("-Dapi.version=1.44")
    // Parallel test execution — scales with available CPU cores
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
}

// ── JAR Manifest ─────────────────────────────────────────────────────────────
tasks.withType<Jar>().configureEach {
    manifest {
        attributes(
            "Implementation-Title"   to project.name,
            "Implementation-Version" to project.version,
            "Built-JDK"              to JavaVersion.current(),
            "Created-By"             to "Gradle ${project.gradle.gradleVersion}"
        )
    }
}

// ── Dependency Guard: Block deprecated shared:* modules ──────────────────────
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == project.rootProject.name
            && requested.name.startsWith("shared-")
        ) {
            throw GradleException(
                "Dependency on deprecated module '${requested.name}' is forbidden. " +
                "Migrate: shared:metrics → platform:java:observability, " +
                "shared:exception → platform:java:core, " +
                "shared:test-utils → platform:java:testing"
            )
        }
    }
}
