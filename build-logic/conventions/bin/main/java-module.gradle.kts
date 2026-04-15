import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.jvm.toolchain.JavaLanguageVersion

/**
 * Unified Java Module Convention Plugin
 *
 * Combines all buildSrc convention capabilities:
 * - java-library + testing + quality + lombok conventions
 * - Java 21 toolchain with IDE compatibility
 * - Javadoc generation with property control
 * - Testcontainers compatibility
 * - Dependency guard against deprecated shared:* modules
 * - Proper compiler flags for ActiveJ/Jackson
 */

plugins {
    `java-library`
    `idea`
    jacoco
    checkstyle
    pmd
    id("com.diffplug.spotless")
}

// Property to control Javadoc generation (disabled by default for speed)
val enableJavadoc = project.findProperty("enableJavadoc")?.toString()?.toBoolean() ?: false

// Sources JAR generated only when publishing.  Skipped on every-day builds to
// save ~15 % per-module build time.  Enable with: ./gradlew build -PwithSourcesJar=true
val withSourcesJar = project.findProperty("withSourcesJar")?.toString()?.toBoolean() ?: false

// JaCoCo only fires during CI or when explicitly requested locally.
// Avoids doubling test time on every developer build.
val withCoverage: Boolean = System.getenv("CI") != null ||
    project.hasProperty("coverage")

val sharedConfigRoot = generateSequence(rootProject.rootDir) { it.parentFile }
    .firstOrNull { candidate ->
        File(candidate, "config/checkstyle/checkstyle.xml").exists() &&
            File(candidate, "config/pmd/minimal-ruleset.xml").exists()
    }
    ?: rootProject.rootDir

fun sharedConfigFile(path: String): File = File(sharedConfigRoot, path)

// Java 21 Toolchain with IDE compatibility
java {
    if (!toolchain.languageVersion.isPresent) {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    }
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    if (withSourcesJar) withSourcesJar()
    if (enableJavadoc)  withJavadocJar()
}

// Compiler Configuration - ActiveJ/Jackson compatible
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

// Javadoc Configuration - uses enableJavadoc property defined above
tasks.withType<Javadoc>().configureEach {
    options.encoding = "UTF-8"
    (options as? StandardJavadocDocletOptions)?.apply {
        addStringOption("Xdoclint:none", "-quiet")
        addBooleanOption("html5", true)
    }
    isEnabled = enableJavadoc
}

// Testing Configuration with Testcontainers compatibility
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showCauses = true
        showStackTraces = true
        showStandardStreams = false
    }
    
    // Testcontainers / Docker Desktop 29+ compatibility
    jvmArgs("-Dapi.version=1.44", "-XX:+UseZGC", "-XX:+ZGenerational")
    maxHeapSize = "1536m"

    // Parallel test execution
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2)
        .coerceAtLeast(1)
        .coerceAtMost(4)
    // NOTE: forkEvery intentionally omitted.  Setting forkEvery > 0 causes Gradle
    // to SIGTERM the test JVM at the class boundary, which races with the XML result
    // writer and produces "Could not write XML test results" errors and null-byte
    // corruption in the output files.  Java 21 + ZGC handles metaspace pressure
    // well without per-class JVM recycling.
}

// JaCoCo — wire finalizedBy only when coverage is requested.
// Without this guard, running ./gradlew compileJava triggers JaCoCo
// instrumentation even though no tests were executed.
if (withCoverage) {
    tasks.withType<Test>().configureEach {
        finalizedBy(tasks.named("jacocoTestReport"))
    }
}

// JaCoCo Configuration - use hardcoded version
configure<JacocoPluginExtension> {
    toolVersion = "0.8.14"
}

tasks.named<JacocoReport>("jacocoTestReport") {
    // Use mustRunAfter instead of dependsOn so the task graph stays lazy —
    // JaCoCo report won't force test execution when only compiling.
    mustRunAfter(tasks.withType<Test>())
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

// Checkstyle Configuration - use hardcoded version
configure<CheckstyleExtension> {
    toolVersion = "10.21.4"
    configFile = sharedConfigFile("config/checkstyle/checkstyle.xml")
    configProperties = mapOf(
        "suppressionFile" to sharedConfigFile("config/checkstyle/suppressions.xml").absolutePath
    )
    isIgnoreFailures = false
}

// PMD Configuration - use hardcoded version
configure<PmdExtension> {
    toolVersion = "7.11.0"
    ruleSetFiles = files(sharedConfigFile("config/pmd/minimal-ruleset.xml"))
    ruleSets = emptyList()
    isIgnoreFailures = false
    isConsoleOutput = true
}

tasks.withType<org.gradle.api.plugins.quality.Pmd>().configureEach {
    val rulesetFile = if (name.contains("Test", ignoreCase = true)) {
        sharedConfigFile("config/pmd/test-ruleset.xml")
    } else {
        sharedConfigFile("config/pmd/minimal-ruleset.xml")
    }
    val sourceDirectory = if (name.contains("Test", ignoreCase = true)) {
        "src/test/java"
    } else {
        "src/main/java"
    }
    ruleSetFiles = files(rulesetFile)
    ruleSets = emptyList()
    source = fileTree(sourceDirectory) {
        exclude("**/generated/**")
        exclude("**/build/generated/**")
        exclude("**/*Grpc.java")
        exclude("**/*Proto.java")
        exclude("**/*_Grpc*.java")
        exclude("**/grpc/**")
        exclude("**/proto/**")
    }
}

// Spotless Configuration
configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    java {
        target("src/**/*.java")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    format("misc") {
        target("*.gradle", "*.gradle.kts", ".gitignore")
        targetExclude("**/node_modules/**", "**/build/**", "**/.gradle/**")
        trimTrailingWhitespace()
        endWithNewline()
    }
    // Format checks are enforced on CI and when explicitly requested.
    // On local builds this is opt-in to avoid adding ~5-15 s per module.
    // Enable locally with: ./gradlew spotlessCheck -PenforceFormatting=true
    isEnforceCheck = System.getenv("CI") != null ||
        project.hasProperty("enforceFormatting")
}

// JAR Manifest with full metadata
tasks.withType<Jar>().configureEach {
    manifest {
        attributes(
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version,
            "Built-JDK" to JavaVersion.current(),
            "Created-By" to "Gradle ${project.gradle.gradleVersion}"
        )
    }
}

// Dependency Guard: Block deprecated shared:* modules
// configureEach is lazy and configuration-cache safe; configurations.all is eager
// and runs against Gradle-internal configurations (e.g. incrementalScalaAnalysis).
configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == project.rootProject.name
            && requested.name.startsWith("shared-")) {
            throw GradleException(
                "Dependency on deprecated module '${requested.name}' is forbidden. " +
                "Migrate: shared:metrics -> platform:java:observability, " +
                "shared:exception -> platform:java:core, " +
                "shared:test-utils -> platform:java:testing"
            )
        }
    }
}

// Standard Dependencies - build-logic cannot access version catalog
dependencies {
    // Lombok - version synced with gradle/libs.versions.toml
    val lombokVersion = "1.18.36"
    compileOnly("org.projectlombok:lombok:$lombokVersion")
    annotationProcessor("org.projectlombok:lombok:$lombokVersion")
    testCompileOnly("org.projectlombok:lombok:$lombokVersion")
    testAnnotationProcessor("org.projectlombok:lombok:$lombokVersion")

    // Testing dependencies
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.assertj:assertj-core:3.27.3")
    testImplementation("org.mockito:mockito-core:5.16.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")

    // Logging for tests
    testRuntimeOnly("org.apache.logging.log4j:log4j-slf4j-impl:2.24.3")
    testRuntimeOnly("org.apache.logging.log4j:log4j-core:2.24.3")
}
