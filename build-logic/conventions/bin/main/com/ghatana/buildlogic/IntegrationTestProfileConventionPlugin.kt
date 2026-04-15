package com.ghatana.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test

class IntegrationTestProfileConventionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val integrationMode = project.hasProperty("runIntegrationTests")
        val integrationProperties = mapOf(
            "testcontainers.enabled" to "true",
            "test.typescript.enabled" to "true",
            "test.python.enabled" to "true",
            "test.go.enabled" to "true",
            "test.rust.enabled" to "true",
            "test.native.enabled" to "true",
            "test.ai.enabled" to "true",
            "runBenchmarks" to "true"
        )

        project.tasks.withType(Test::class.java).configureEach {
            if (integrationMode) {
                integrationProperties.forEach { (key, value) -> systemProperty(key, value) }
                logger.lifecycle("[$path] Integration test profile ACTIVE - all tests included")
            } else {
                systemProperty("junit.jupiter.tags.exclude", "integration")
            }
        }

        if (project == project.rootProject) {
            project.tasks.register("integrationTest") {
                group = "verification"
                description = "Run full build including integration tests (equivalent to ./gradlew build -PrunIntegrationTests)"
                doFirst {
                    logger.lifecycle("Run: ./gradlew build -PrunIntegrationTests")
                }
            }
        }
    }
}