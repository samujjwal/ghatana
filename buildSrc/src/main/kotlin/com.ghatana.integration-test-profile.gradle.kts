import org.gradle.api.tasks.testing.Test

/**
 * Manages integration test profiles.
 *
 * Default: excludes tests tagged "integration".
 * With -PrunIntegrationTests: includes all tests and forwards infrastructure system properties.
 *
 * Usage:
 *   ./gradlew build                       — regular build, excludes "integration"-tagged tests
 *   ./gradlew build -PrunIntegrationTests — full integration build, all tags included
 */

val integrationMode = project.hasProperty("runIntegrationTests")

val integrationProperties = mapOf(
    "testcontainers.enabled"  to "true",
    "test.typescript.enabled" to "true",
    "test.python.enabled"     to "true",
    "test.go.enabled"         to "true",
    "test.rust.enabled"       to "true",
    "test.native.enabled"     to "true",
    "test.ai.enabled"         to "true",
    "runBenchmarks"           to "true",
)

tasks.withType<Test>().configureEach {
    val taskPath = path
    if (integrationMode) {
        useJUnitPlatform()
        integrationProperties.forEach { (key, value) -> systemProperty(key, value) }
        logger.lifecycle("[$taskPath] Integration test profile ACTIVE — all tests included")
    } else {
        useJUnitPlatform {
            excludeTags("integration")
        }
    }
}

// Convenience task at root project level only
if (project == rootProject) {
    tasks.register("integrationTest") {
        group = "verification"
        description = "Run the full build including all integration tests.\n" +
            "Equivalent to: ./gradlew build -PrunIntegrationTests"
        doFirst {
            logger.lifecycle(
                "NOTE: This task is a shortcut. For a full integration build, use:\n" +
                    "  ./gradlew build -PrunIntegrationTests"
            )
        }
    }
}
