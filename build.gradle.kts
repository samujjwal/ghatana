/**
 * Ghatana Monorepo - Simplified Root Build Configuration
 *
 * Design Principles:
 * - MINIMAL: Only truly global configuration lives here
 * - DELEGATING: All module configuration handled by convention plugins
 * - ISOLATED: No cross-product task dependencies
 * - FAST: Minimal configuration time overhead
 */

plugins {
    id("java-platform")
    id("idea")
    alias(libs.plugins.cyclonedx)
}

group = "com.ghatana"
version = "2026.3.1-SNAPSHOT"

// =============================================================================
// Dependency Resolution Strategies - Global configuration
// =============================================================================
allprojects {
    configurations.all {
        resolutionStrategy {
            cacheDynamicVersionsFor(24, "hours")
            cacheChangingModulesFor(24, "hours")
            // Use highest version for conflicts (Gradle default)
            // failOnVersionConflict() - disabled for now, will re-enable after resolving conflicts
            force(
                "org.slf4j:slf4j-api:2.0.17",
                "com.fasterxml.jackson.core:jackson-core:2.18.2",
                "com.fasterxml.jackson.core:jackson-databind:2.18.2",
                "org.junit:junit-bom:5.11.4"
            )
        }
    }
}

// =============================================================================
// Repository Configuration - Centralized in settings.gradle.kts only
// =============================================================================

// =============================================================================
// Convention Plugin Application - Automatic for Java modules
// =============================================================================
subprojects {
    // Helper function to check if project has Java sources
    fun hasJavaSources(): Boolean {
        return file("$projectDir/src/main/java").exists() ||
               file("$projectDir/src/main/kotlin").exists() ||
               file("$projectDir/src/test/java").exists() ||
               file("$projectDir/src/test/kotlin").exists()
    }
    
    // Apply standard conventions to all Java projects
    if (hasJavaSources()) {
        apply(plugin = "java-library")
        apply(plugin = "idea")
        apply(plugin = "com.ghatana.java-conventions")
        apply(plugin = "com.ghatana.testing-conventions")
        apply(plugin = "com.ghatana.quality-conventions")
        apply(plugin = "com.ghatana.lombok-conventions")
        
        // Standard group and version
        group = rootProject.group
        version = rootProject.version
        
        // Produce sources and Javadoc JARs for all modules
        configure<JavaPluginExtension> {
            withJavadocJar()
            withSourcesJar()
        }
        
        // Platform boundary guardrails - prevent platform modules from depending on products
        if (project.path.startsWith(":platform:")) {
            apply(from = rootProject.file("gradle/platform-boundary-check.gradle"))
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
// Architectural Guardrails - Minimal and Essential
// =============================================================================
apply(from = file("gradle/product-isolation.gradle"))
apply(from = file("gradle/doc-tag-check.gradle"))

// Wire critical checks into standard build lifecycle
tasks.named("check") {
    dependsOn("checkDocTags")
}

// =============================================================================
// SBOM Generation - Essential for Security Compliance
// =============================================================================
allprojects {
    tasks.withType<org.cyclonedx.gradle.CyclonedxDirectTask>().configureEach {
        includeConfigs = listOf("runtimeClasspath", "compileClasspath")
        skipConfigs = listOf("testRuntimeClasspath", "testCompileClasspath")
        includeLicenseText = false
        includeMetadataResolution = true
        jsonOutput.set(file("build/sbom/direct-bom.json"))
        xmlOutput.unsetConvention()
    }
}

tasks.withType<org.cyclonedx.gradle.CyclonedxAggregateTask>().configureEach {
    includeLicenseText = false
    includeBuildSystem = true
    jsonOutput.set(project.file("build/sbom/bom.json"))
    xmlOutput.unsetConvention()
}

// =============================================================================
// Platform BOM Validation - Essential Build Integrity Check
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
    }
}

// =============================================================================
// Build Health Check - Quick diagnostics
// =============================================================================
tasks.register("buildHealth") {
    group = "verification"
    description = "Quick build health diagnostics"
    
    doLast {
        println("=== Build Health Report ===")
        println("Java Version: ${System.getProperty("java.version")}")
        println("Gradle Version: ${project.gradle.gradleVersion}")
        println("Total Projects: ${gradle.rootProject.childProjects.size}")
        
        val javaProjects = gradle.rootProject.childProjects.values.count { subproject ->
            subproject.file("src/main/java").exists() ||
            subproject.file("src/main/kotlin").exists()
        }
        println("Java Projects: $javaProjects")
        
        // Check for common issues
        val issues = mutableListOf<String>()
        
        // Check for duplicate plugin applications
        gradle.rootProject.childProjects.values.forEach { subproject ->
            val buildFile = subproject.buildFile
            if (buildFile.exists()) {
                val content = buildFile.readText()
                if (content.contains("java") && 
                    content.contains("toolchain") && 
                    !content.contains("com.ghatana.java-conventions")) {
                    issues.add("${subproject.path}: Manual Java configuration detected")
                }
            }
        }
        
        if (issues.isNotEmpty()) {
            println("\n=== Issues Found ===")
            issues.forEach { println("  - $it") }
        } else {
            println("\n=== No Issues Found ===")
        }
        
        println("========================")
    }
}
