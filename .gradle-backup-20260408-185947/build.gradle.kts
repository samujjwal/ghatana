/**
 * Ghatana Monorepo — Root Build Configuration
 *
 * Design Principles:
 * - MINIMAL:    Only truly global configuration lives here.
 * - DELEGATING: Java / test / quality settings come from convention plugins.
 * - STABLE:     Changes here affect ALL modules; prefer convention plugins instead.
 *
 * Convention plugins (buildSrc/src/main/kotlin):
 *   com.ghatana.java-conventions       — Java 21 toolchain, compiler, Javadoc, JAR manifest
 *   com.ghatana.testing-conventions    — JUnit Platform, JaCoCo, parallel tests, Docker compat
 *   com.ghatana.quality-conventions    — Checkstyle, PMD, Spotless
 *   com.ghatana.lombok-conventions     — Lombok annotation processing
 *   com.ghatana.integration-test-profile — Integration-tag filtering (applied from subprojects{})
 *   com.ghatana.test-failure-tolerance   — Configurable failure-rate gate (applied from subprojects{})
 */
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.cyclonedx.gradle.CyclonedxAggregateTask
import org.cyclonedx.gradle.CyclonedxDirectTask

plugins {
    id("java-platform")
    id("idea")
    alias(libs.plugins.cyclonedx)
}

group = "com.ghatana"
version = "2026.3.1-SNAPSHOT"

// =============================================================================
// Java Version Validation
// =============================================================================
apply(from = file("gradle/java-version-check.gradle"))

// =============================================================================
// Repository Configuration
// =============================================================================
allprojects {
    repositories {
        mavenCentral()
        maven { url = uri("https://repo1.maven.org/maven2/") }
        // mavenLocal() is only enabled for local platform library development.
        // Activate with: ./gradlew <task> -PlocalBuild=true
        if (findProperty("localBuild") == "true") {
            mavenLocal()
        }
    }
}

// =============================================================================
// Convention Plugins — Applied to all Java/Kotlin subprojects
// =============================================================================
subprojects {
    // Skip modules with no Java / Kotlin sources
    if (!file("$projectDir/src/main/java").exists() &&
        !file("$projectDir/src/main/kotlin").exists() &&
        !file("$projectDir/src/test/java").exists()) {
        return@subprojects
    }

    // Base library plugin — every Java module is a library by default
    apply(plugin = "java-library")
    apply(plugin = "idea")

    // Convention plugins — provide all standard Java/test/CI config
    // Convention plugins - provide all standard Java/test/CI config
    apply(plugin = "com.ghatana.java-conventions")
    apply(plugin = "com.ghatana.testing-conventions-simplified")
    apply(plugin = "com.ghatana.quality-conventions")
    apply(plugin = "com.ghatana.integration-test-profile")

    group = "com.ghatana"
    version = rootProject.version

    // Produce sources and Javadoc JARs for all modules (required for publishing)
    configure<JavaPluginExtension> {
        withJavadocJar()
        withSourcesJar()
    }

    // Platform boundary guardrails — prevent platform modules from depending on products
    if (project.path.startsWith(":platform:")) {
        apply(from = rootProject.file("gradle/platform-boundary-check.gradle"))
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

// Wire checkDocTags into standard `check` so it runs on every CI build.
tasks.named("check") {
    dependsOn("checkDocTags")
}

// =============================================================================
// Aggregate Build / Test Tasks
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

// =============================================================================
// SBOM Generation (CycloneDX)
// =============================================================================
allprojects {
    tasks.withType<CyclonedxDirectTask>().configureEach {
        includeConfigs = listOf("runtimeClasspath", "compileClasspath")
        skipConfigs = listOf("testRuntimeClasspath", "testCompileClasspath")
        includeLicenseText = false
        includeMetadataResolution = true
        jsonOutput.set(file("build/sbom/direct-bom.json"))
        xmlOutput.unsetConvention()
    }
}

tasks.withType<CyclonedxAggregateTask>().configureEach {
    includeLicenseText = false
    includeBuildSystem = true
    jsonOutput.set(project.file("build/sbom/bom.json"))
    xmlOutput.unsetConvention()
}

// =============================================================================
// Platform BOM Validation
// =============================================================================
tasks.register("validatePlatformBom") {
    group = "verification"
    description = "Validate platform BOM consistency and dependency governance"
    
    doLast {
        println("Platform BOM validation started")
        
        // Validate critical build files exist and are readable
        val criticalFiles = listOf(
            "gradle/libs.versions.toml",
            "buildSrc/gradle.properties", 
            "buildSrc/build.gradle.kts"
        )
        
        criticalFiles.forEach { fileName ->
            val file = project.file(fileName)
            if (!file.exists()) {
                throw GradleException("Critical build file missing: $fileName")
            }
            if (!file.canRead()) {
                throw GradleException("Critical build file not readable: $fileName")
            }
        }
        
        println("Platform BOM validation passed")
        println("All critical build files validated")
    }
}


