import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.artifacts.ProjectDependency

/**
 * Validates that every Java project with test sources declares the standard test-utils module.
 *
 * The audit runs at task-execution time so it does not affect configuration performance.
 * The "verifyTestDependencies" task is added to every project and wired into the "check" lifecycle.
 */

val requiredTestModule = ":libs:test-utils"

val auditTask = tasks.register("verifyTestDependencies") {
    group = "verification"
    description = "Ensures standardized testing modules (test-utils) are applied to this project."

    doLast {
        runAudit(project, requiredTestModule)
    }
}

tasks.matching { it.name == "check" }.configureEach {
    dependsOn(auditTask)
}

fun runAudit(project: Project, requiredModule: String) {
    if (!project.plugins.hasPlugin("java")) {
        project.logger.debug("[test-audit] {} skipped (no java plugin)", project.path)
        return
    }

    val hasTestSources = project.file("src/test/java").exists() ||
        project.file("src/test/kotlin").exists()
    if (!hasTestSources) {
        project.logger.debug("[test-audit] {} skipped (no test sources)", project.path)
        return
    }

    if (project.path == requiredModule) {
        project.logger.debug("[test-audit] {} skipped (provider module)", project.path)
        project.logger.lifecycle("[test-audit] {} dependencies OK", project.path)
        return
    }

    val testDependencies = project.configurations
        .findByName("testImplementation")?.allDependencies ?: emptySet()

    var hasDep = testDependencies.any { dep ->
        when (dep) {
            is ProjectDependency -> runCatching {
                dep.path == requiredModule || dep.path.endsWith(requiredModule)
            }.getOrElse {
                dep.name == "test-utils" || dep.name.endsWith("test-utils")
            }
            is ExternalDependency -> dep.name.lowercase().contains("test-utils")
            else -> false
        }
    }

    if (!hasDep) {
        runCatching {
            val testRuntimeConfig = project.configurations.findByName("testRuntimeClasspath")
            if (testRuntimeConfig != null) {
                hasDep = testRuntimeConfig.resolvedConfiguration.resolvedArtifacts.any { artifact ->
                    artifact.moduleVersion.id.name.lowercase().contains("test-utils")
                }
            }
        }.onFailure { e ->
            project.logger.debug("[test-audit] Could not check runtime classpath: ${e.message}")
        }
    }

    if (!hasDep) {
        throw GradleException("${project.path} is missing required test dependency $requiredModule")
    }

    project.logger.lifecycle("[test-audit] {} dependencies OK", project.path)
}
