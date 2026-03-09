package com.ghatana.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test

/**
 * Gradle plugin that manages integration test profiles.
 *
 * <p>By default, the regular build excludes tests that require external
 * infrastructure (Docker, databases, AI services, native libraries, external
 * toolchains). When the {@code runIntegrationTests} property is set, ALL
 * tests are included and the necessary system properties are forwarded.
 *
 * <h3>Profiles</h3>
 * <table>
 *   <tr><th>Profile</th><th>Command</th><th>Behaviour</th></tr>
 *   <tr>
 *     <td>Regular (default)</td>
 *     <td>{@code ./gradlew build}</td>
 *     <td>Excludes integration-tagged tests; all infra system-properties OFF</td>
 *   </tr>
 *   <tr>
 *     <td>Full Integration</td>
 *     <td>{@code ./gradlew build -PrunIntegrationTests}</td>
 *     <td>Includes ALL tests; all infra system-properties ON</td>
 *   </tr>
 * </table>
 *
 * <h3>Test Tagging Convention</h3>
 * Tests that require external infrastructure MUST be annotated with
 * {@code @Tag("integration")}. The regular build profile excludes this tag.
 * The full integration profile includes it.
 *
 * <h3>System Properties Forwarded in Integration Profile</h3>
 * <ul>
 *   <li>{@code testcontainers.enabled=true} — Docker/TestContainers based tests</li>
 *   <li>{@code test.typescript.enabled=true} — TypeScript toolchain tests</li>
 *   <li>{@code test.python.enabled=true} — Python toolchain tests</li>
 *   <li>{@code test.go.enabled=true} — Go toolchain tests</li>
 *   <li>{@code test.rust.enabled=true} — Rust toolchain tests</li>
 *   <li>{@code test.native.enabled=true} — Native library tests (Whisper, Coqui)</li>
 *   <li>{@code test.ai.enabled=true} — AI service tests (Ollama etc.)</li>
 *   <li>{@code runBenchmarks=true} — JMH performance benchmarks</li>
 * </ul>
 *
 * @doc.type plugin
 * @doc.purpose Integration test profile management
 * @doc.layer build-infrastructure
 * @doc.pattern Convention Plugin
 */
class IntegrationTestProfilePlugin implements Plugin<Project> {

    /** All system properties forwarded when integration profile is active. */
    static final Map<String, String> INTEGRATION_PROPERTIES = [
        'testcontainers.enabled'  : 'true',
        'test.typescript.enabled'  : 'true',
        'test.python.enabled'      : 'true',
        'test.go.enabled'          : 'true',
        'test.rust.enabled'        : 'true',
        'test.native.enabled'      : 'true',
        'test.ai.enabled'          : 'true',
        'runBenchmarks'            : 'true',
    ]

    @Override
    void apply(Project project) {
        boolean integrationMode = project.hasProperty('runIntegrationTests')

        project.tasks.withType(Test).configureEach { Test testTask ->
            if (integrationMode) {
                // Full integration mode — include everything, forward infra properties
                testTask.useJUnitPlatform {
                    // Include ALL tags (no exclusions)
                }
                INTEGRATION_PROPERTIES.each { key, value ->
                    testTask.systemProperty(key, value)
                }
                project.logger.lifecycle(
                    "[${testTask.path}] Integration test profile ACTIVE — all tests included"
                )
            } else {
                // Regular mode — exclude integration-tagged tests
                testTask.useJUnitPlatform {
                    excludeTags 'integration'
                }
            }
        }

        // Register a convenience task to run full integration tests
        if (project == project.rootProject) {
            project.tasks.register('integrationTest') {
                group = 'verification'
                description = 'Run the full build including all integration tests requiring external infrastructure.\n' +
                    'Equivalent to: ./gradlew build -PrunIntegrationTests'
                doFirst {
                    project.logger.lifecycle(
                        "NOTE: This task is a shortcut. For a full integration build, use:\n" +
                        "  ./gradlew build -PrunIntegrationTests"
                    )
                }
            }
        }
    }
}
