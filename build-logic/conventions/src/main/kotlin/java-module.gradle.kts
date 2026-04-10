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

// Java 21 Toolchain with IDE compatibility
java {
    if (!toolchain.languageVersion.isPresent) {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    }
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    withSourcesJar()
    // Javadoc JAR only created if Javadoc is enabled
    if (enableJavadoc) {
        withJavadocJar()
    }
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
    jvmArgs("-Dapi.version=1.44")
    
    // Parallel test execution
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
}

// JaCoCo Configuration - use hardcoded version
configure<JacocoPluginExtension> {
    toolVersion = "0.8.14"
}

tasks.named<JacocoReport>("jacocoTestReport") {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

// Checkstyle Configuration - use hardcoded version
configure<CheckstyleExtension> {
    toolVersion = "10.21.4"
    configFile = rootProject.file("config/checkstyle/checkstyle.xml")
    configProperties = mapOf(
        "suppressionFile" to rootProject.file("config/checkstyle/suppressions.xml").absolutePath
    )
    isIgnoreFailures = false
}

// PMD Configuration - use hardcoded version
configure<PmdExtension> {
    toolVersion = "7.11.0"
    isIgnoreFailures = false
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
        target("**/*.gradle", "**/.gitignore")
        trimTrailingWhitespace()
        endWithNewline()
    }
    isEnforceCheck = true
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
configurations.all {
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
