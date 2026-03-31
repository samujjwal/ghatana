import org.gradle.api.GradleException

/**
 * Verifies that Java test classes in ActiveJ-dependent modules extend EventloopTestBase.
 *
 * Only enforced when:
 * - The project has the 'java' plugin applied
 * - The project depends on an ActiveJ eventloop or platform testing module
 * - Test sources exist under src/test/java
 *
 * Exempt patterns:
 * - Classes ending in "IT" (integration tests may use different harnesses)
 * - Classes annotated with @EventloopExempt (opt-out marker)
 * - Test files that do not contain any @Test annotations (helper classes)
 * - Files in packages containing "data", "model", "dto" (pure data tests)
 */

val checkTask = tasks.register("verifyEventloopTestBase") {
    group = "verification"
    description = "Ensures ActiveJ test classes extend EventloopTestBase."

    doLast {
        if (!project.plugins.hasPlugin("java")) {
            logger.debug("[eventloop-check] {} skipped (no java plugin)", project.path)
            return@doLast
        }

        val testDir = project.file("src/test/java")
        if (!testDir.exists()) {
            logger.debug("[eventloop-check] {} skipped (no test sources)", project.path)
            return@doLast
        }

        // Only enforce on modules that use ActiveJ (check for activej or platform testing dep)
        val allDeps = project.configurations
            .filter { it.isCanBeResolved }
            .flatMap { conf ->
                runCatching { conf.resolvedConfiguration.resolvedArtifacts }.getOrElse { emptySet() }
            }
            .map { it.moduleVersion.id.toString() }

        val usesActivej = allDeps.any { it.contains("activej") } ||
            project.configurations.findByName("testImplementation")?.allDependencies?.any {
                it.name.contains("activej") || it.name == "testing" || it.name == "activej-test-utils"
            } == true

        if (!usesActivej) {
            logger.debug("[eventloop-check] {} skipped (no ActiveJ dependency)", project.path)
            return@doLast
        }

        val violations = mutableListOf<String>()
        val testFiles = testDir.walk()
            .filter { it.isFile && it.name.endsWith("Test.java") }
            .toList()

        for (file in testFiles) {
            val content = file.readText()

            // Skip files without @Test annotations (helpers, not test classes)
            if (!content.contains("@Test")) continue

            // Skip integration tests (*IT.java is already excluded by name pattern *Test.java)
            // Skip exempt classes
            if (content.contains("@EventloopExempt")) continue

            // Skip pure data/model tests (package-level exemption)
            val relPath = file.relativeTo(testDir).path
            if (relPath.contains("/data/") || relPath.contains("/model/") || relPath.contains("/dto/")) continue

            // Check if extends EventloopTestBase
            if (!content.contains("extends EventloopTestBase")) {
                violations.add(relPath)
            }
        }

        if (violations.isNotEmpty()) {
            val msg = buildString {
                appendLine("[eventloop-check] ${project.path}: ${violations.size} test(s) do not extend EventloopTestBase:")
                violations.forEach { appendLine("  - $it") }
                appendLine("Add 'extends EventloopTestBase' or annotate with @EventloopExempt to opt out.")
            }
            logger.warn(msg)
            // Warn-only for now; change to throw GradleException(msg) to enforce as a hard gate
        }

        if (violations.isEmpty()) {
            logger.lifecycle("[eventloop-check] {} OK ({} test files checked)", project.path, testFiles.size)
        }
    }
}

tasks.matching { it.name == "check" }.configureEach {
    dependsOn(checkTask)
}
