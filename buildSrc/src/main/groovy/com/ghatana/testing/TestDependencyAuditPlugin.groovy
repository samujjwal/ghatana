package com.ghatana.testing

import org.gradle.api.Project
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.artifacts.ProjectDependency

/**
 * Plugin that validates test dependency alignment with the standardized testing modules.
 */
class TestDependencyAuditPlugin implements Plugin<Project> {

    private static final String REQUIRED_TEST_MODULE = ':libs:test-utils'

    @Override
    void apply(Project project) {
        def auditTask = project.tasks.register('verifyTestDependencies') {
            group = 'verification'
            description = 'Ensures standardized testing modules are applied to this project.'

            doLast {
                if (!project.plugins.hasPlugin('java')) {
                    project.logger.debug('[test-audit] {} skipped (no java plugin)', project.path)
                    return
                }

                boolean hasTestSources = project.file('src/test/java').exists() || project.file('src/test/kotlin').exists()
                if (!hasTestSources) {
                    project.logger.debug('[test-audit] {} skipped (no test sources)', project.path)
                    return
                }

                // Do not audit the provider module itself (it is the source of the test-utils)
                if (project.path == REQUIRED_TEST_MODULE) {
                    project.logger.debug('[test-audit] {} skipped (provider module)', project.path)
                    project.logger.lifecycle('[test-audit] {} dependencies OK', project.path)
                    return
                }

                def testDependencies = project.configurations.findByName('testImplementation')?.allDependencies ?: []

                // Check if the project's testImplementation configuration includes the required test-utils
                boolean hasCoreTestingDependency = false

                // First check direct dependencies
                hasCoreTestingDependency = testDependencies.any { dep ->
                    if (dep instanceof ProjectDependency) {
                        // Prefer the dependency project path when available
                        try {
                            def depPath = dep.dependencyProject?.path
                            if (depPath) {
                                return depPath == REQUIRED_TEST_MODULE || depPath.endsWith(REQUIRED_TEST_MODULE)
                            }
                        } catch (Exception ignored) {
                            // fall back to name checks below
                        }
                        // Check by name as a fallback
                        return dep.name == 'test-utils' || dep.name.endsWith('test-utils')
                    } else if (dep instanceof ExternalDependency) {
                        // Check for external dependencies that might be the test-utils
                        def name = dep.name?.toLowerCase() ?: ''
                        return name.contains('test-utils')
                    }
                    return false
                }

                // If not found in direct dependencies, check the resolved classpath
                if (!hasCoreTestingDependency) {
                    try {
                        def testRuntimeConfig = project.configurations.findByName('testRuntimeClasspath')
                        if (testRuntimeConfig) {
                            hasCoreTestingDependency = testRuntimeConfig.resolvedConfiguration.resolvedArtifacts.any { artifact ->
                                def name = artifact.moduleVersion.id.name.toLowerCase()
                                name.contains('test-utils')
                            }
                        }
                    } catch (Exception e) {
                        project.logger.debug("[test-audit] Could not check runtime classpath: ${e.message}")
                    }
                }

                if (!hasCoreTestingDependency) {
                    throw new GradleException("${project.path} is missing required test dependency ${REQUIRED_TEST_MODULE}")
                }

                project.logger.lifecycle('[test-audit] {} dependencies OK', project.path)
            }
        }

        project.tasks.matching { it.name == 'check' }.configureEach {
            dependsOn(auditTask)
        }
    }
}
