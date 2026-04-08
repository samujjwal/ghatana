package com.ghatana.conventions

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.IdeaPlugin
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.javadoc.Javadoc

/**
 * Convention plugin that replaces the subprojects block in the root build.gradle.kts.
 * This plugin applies common Java configuration to all subprojects that have Java source.
 */
class GhatanaJavaSubprojectConventionPlugin : Plugin<Project> {
    
    override fun apply(project: Project) {
        // Skip non-Java projects
        val hasJavaSource = project.file("${project.projectDir}/src/main/java").exists() ||
                          project.file("${project.projectDir}/src/main/kotlin").exists() ||
                          project.file("${project.projectDir}/src/test/java").exists()
        
        if (!hasJavaSource) {
            return
        }
        
        // Apply plugins
        project.plugins.apply(JavaLibraryPlugin::class.java)
        project.plugins.apply(IdeaPlugin::class.java)
        
        // Apply custom plugins if they exist
        try {
            project.plugins.apply("com.ghatana.test-failure-tolerance")
        } catch (e: Exception) {
            // Plugin not available, skip
        }
        
        try {
            project.plugins.apply("com.ghatana.integration-test-profile")
        } catch (e: Exception) {
            // Plugin not available, skip
        }
        
        // Set group and version
        project.group = "com.ghatana"
        project.version = project.rootProject.version
        
        // Configure Java extension
        project.extensions.configure(JavaPluginExtension::class.java) {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(21))
            }
            withJavadocJar()
            withSourcesJar()
        }
        
        // Configure Java compilation
        project.tasks.withType(JavaCompile::class.java).configureEach {
            options.encoding = "UTF-8"
            options.compilerArgs.addAll(listOf(
                "-parameters",
                "-Xlint:all",
                "-Xlint:-processing",
                "-Xlint:-serial"
            ))
        }
        
        // Configure test tasks
        project.tasks.withType(Test::class.java).configureEach {
            // JUnit Platform configuration is handled by IntegrationTestProfilePlugin
            // Do NOT call useJUnitPlatform() here
            
            testLogging {
                events(org.gradle.api.tasks.testing.TestLogEvent.PASSED,
                        org.gradle.api.tasks.testing.TestLogEvent.SKIPPED,
                        org.gradle.api.tasks.testing.TestLogEvent.FAILED)
                exceptionFormat = org.gradle.api.tasks.testing.TestExceptionFormat.FULL
                showStandardStreams = false
                showCauses = true
                showStackTraces = true
            }
            
            // Parallel test execution
            maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
            
            // Testcontainers / Docker Desktop 29+ compatibility
            jvmArgs("-Dapi.version=1.44")
        }
        
        // Apply platform boundary guardrails to platform modules
        if (project.path.startsWith(":platform:")) {
            project.apply(mapOf("from" to project.rootProject.file("gradle/platform-boundary-check.gradle")))
        }
        
        // Configure Javadoc
        project.tasks.withType(Javadoc::class.java).configureEach {
            options.encoding = "UTF-8"
            (options as org.gradle.external.documentation.dsl.JavadocOptions).apply {
                addStringOption("Xdoclint:none", "-quiet")
            }
        }
    }
}
