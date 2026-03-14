/**
 * Ghatana Monorepo - Root Build Configuration
 * 
 * Design Principles:
 * - CLEAN: Minimal configuration, maximum clarity
 * - CONSISTENT: Same conventions across all modules
 * - EXTENSIBLE: Easy to add new modules and features
 */
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    id("java-platform")
    id("idea")
}

group = "com.ghatana"
version = "1.0.0-SNAPSHOT"

// =============================================================================
// Repository Configuration
// =============================================================================

allprojects {
    repositories {
        mavenCentral()
        mavenLocal()
    }
}

// =============================================================================
// Java Conventions - Applied to all Java subprojects
// =============================================================================

subprojects {
    // Skip non-Java projects
    if (!file("$projectDir/src/main/java").exists() && 
        !file("$projectDir/src/main/kotlin").exists() &&
        !file("$projectDir/src/test/java").exists()) {
        return@subprojects
    }
    
    apply(plugin = "java-library")
    apply(plugin = "idea")
    apply(plugin = "com.ghatana.test-failure-tolerance")
    apply(plugin = "com.ghatana.integration-test-profile")
    
    group = "com.ghatana"
    version = rootProject.version
    
    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
        withJavadocJar()
        withSourcesJar()
    }

    dependencies {
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }
    
    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf(
            "-parameters",           // Preserve parameter names
            "-Xlint:all",           // Enable all warnings
            "-Xlint:-processing",   // Disable annotation processing warnings
            "-Xlint:-serial"        // Disable serialization warnings
        ))
    }
    
    tasks.withType<Test> {
        // JUnit Platform configuration (tag-based filtering) is handled by
        // the IntegrationTestProfilePlugin. Do NOT call useJUnitPlatform()
        // here — the plugin applies it with the correct tag exclusions.
        
        testLogging {
            events(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
            exceptionFormat = TestExceptionFormat.FULL
            showStandardStreams = false
            showCauses = true
            showStackTraces = true
        }
        
        // Parallel test execution
        maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
    }
    
    // Apply platform boundary guardrails to platform modules
    if (project.path.startsWith(":platform:")) {
        apply(from = rootProject.file("gradle/platform-boundary-check.gradle"))
    }
    
    tasks.withType<Javadoc> {
        options.encoding = "UTF-8"
        (options as StandardJavadocDocletOptions).apply {
            addStringOption("Xdoclint:none", "-quiet")
        }
    }
}

// =============================================================================
// IDE Configuration
// =============================================================================

idea {
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}

// =============================================================================
// Architectural Guardrails
// =============================================================================

apply(from = file("gradle/product-isolation.gradle"))
apply(from = file("gradle/doc-tag-check.gradle"))

// =============================================================================
// Aggregate Tasks
// =============================================================================

tasks.register("buildPlatform") {
    group = "build"
    description = "Build all platform modules"
    dependsOn(
        ":platform:java:core:build",
        ":platform:java:database:build",
        ":platform:java:http:build",
        ":platform:java:security:build",
        ":platform:java:observability:build",
        ":platform:java:testing:build"
    )
}

tasks.register("testPlatform") {
    group = "verification"
    description = "Test all platform modules"
    dependsOn(
        ":platform:java:core:test",
        ":platform:java:database:test",
        ":platform:java:http:test",
        ":platform:java:security:test",
        ":platform:java:observability:test",
        ":platform:java:testing:test"
    )
}

tasks.register("buildProducts") {
    group = "build"
    description = "Build all product modules"
    dependsOn(
        ":products:aep:build",
        ":products:data-cloud:build",
        ":products:yappc:build",
        ":products:flashit:build",
        ":products:software-org:build",
        ":products:virtual-org:build",
        ":products:security-gateway:build"
    )
}

tasks.register("buildAll") {
    group = "build"
    description = "Build entire monorepo"
    dependsOn("buildPlatform", "buildProducts")
}
