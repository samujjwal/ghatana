/**
 * JaCoCo code coverage threshold enforcement.
 *
 * Defines minimum coverage levels for platform-kernel and platform-plugins modules.
 * Enforces coverage during `./gradlew check` for target modules only.
 */

plugins {
    id("jacoco")
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

tasks.withPath(setOf(
        ":platform-kernel:kernel-core:test",
        ":platform-kernel:kernel-persistence:test",
        ":platform-kernel:kernel-plugin:test",
        ":platform-kernel:kernel-testing:test",
        ":platform-plugins:plugin-audit-trail:test",
        ":platform-plugins:plugin-billing-ledger:test",
        ":platform-plugins:plugin-compliance:test",
        ":platform-plugins:plugin-consent:test",
        ":platform-plugins:plugin-fraud-detection:test",
        ":platform-plugins:plugin-human-approval:test",
        ":platform-plugins:plugin-risk-management:test",
        ":products:phr:test",
        ":products:finance:integration-testing:test"
)) { testTask ->
    testTask.finalizedBy("jacoco${testTask.name.replaceFirstChar { it.uppercase() }}Report")
}

// Coverage thresholds per module (target root: platform-kernel + platform-plugins)
// These are enforced during the jacocoTestCoverageVerification task.

val coverageRules = mapOf(
        // ── Platform-kernel (core kernel infrastructure) ────────────────────
        ":platform-kernel:kernel-core" to CoverageThreshold(
                instruction = 0.75,  // 75% branch coverage minimum
                branch = 0.70,       // 70% branch coverage minimum
                line = 0.80,         // 80% line coverage minimum
                method = 0.75        // 75% method coverage minimum
        ),
        ":platform-kernel:kernel-persistence" to CoverageThreshold(
                instruction = 0.70,
                branch = 0.65,
                line = 0.75,
                method = 0.70
        ),
        ":platform-kernel:kernel-plugin" to CoverageThreshold(
                instruction = 0.75,
                branch = 0.70,
                line = 0.80,
                method = 0.75
        ),
        ":platform-kernel:kernel-testing" to CoverageThreshold(
                instruction = 0.70,
                branch = 0.65,
                line = 0.75,
                method = 0.70
        ),

        // ── Platform-plugins (conformance infrastructure) ───────────────────
        ":platform-plugins:plugin-audit-trail" to CoverageThreshold(
                instruction = 0.80,
                branch = 0.75,
                line = 0.85,
                method = 0.80
        ),
        ":platform-plugins:plugin-billing-ledger" to CoverageThreshold(
                instruction = 0.80,
                branch = 0.75,
                line = 0.85,
                method = 0.80
        ),
        ":platform-plugins:plugin-compliance" to CoverageThreshold(
                instruction = 0.75,
                branch = 0.70,
                line = 0.80,
                method = 0.75
        ),
        ":platform-plugins:plugin-consent" to CoverageThreshold(
                instruction = 0.80,
                branch = 0.75,
                line = 0.85,
                method = 0.80
        ),
        ":platform-plugins:plugin-fraud-detection" to CoverageThreshold(
                instruction = 0.75,
                branch = 0.70,
                line = 0.80,
                method = 0.75
        ),
        ":platform-plugins:plugin-human-approval" to CoverageThreshold(
                instruction = 0.80,
                branch = 0.75,
                line = 0.85,
                method = 0.80
        ),
        ":platform-plugins:plugin-risk-management" to CoverageThreshold(
                instruction = 0.75,
                branch = 0.70,
                line = 0.80,
                method = 0.75
        ),

        // ── Product modules (regulated PHR & Finance) ──────────────────────
        ":products:phr" to CoverageThreshold(
                instruction = 0.70,
                branch = 0.65,
                line = 0.75,
                method = 0.70
        ),
        ":products:finance:integration-testing" to CoverageThreshold(
                instruction = 0.70,
                branch = 0.65,
                line = 0.75,
                method = 0.70
        )
)

data class CoverageThreshold(
        val instruction: Double,
        val branch: Double,
        val line: Double,
        val method: Double
)

// Configure jacocoTestCoverageVerification to use thresholds
tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            element = "SOURCEFILE"
            includes = listOf("com.ghatana.*")

            // Instruction-level (bytecode) coverage
            limit {
                counter = "INSTRUCTION"
                value = "COVEREDRATIO"
                minimum = coverageRules[project.path]?.instruction?.toBigDecimal() ?: 0.60.toBigDecimal()
            }

            // Branch coverage
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = coverageRules[project.path]?.branch?.toBigDecimal() ?: 0.55.toBigDecimal()
            }

            // Line coverage
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = coverageRules[project.path]?.line?.toBigDecimal() ?: 0.70.toBigDecimal()
            }

            // Method coverage
            limit {
                counter = "METHOD"
                value = "COVEREDRATIO"
                minimum = coverageRules[project.path]?.method?.toBigDecimal() ?: 0.60.toBigDecimal()
            }
        }
    }
}

// Wire coverage verification into check task for target modules
if (project.path in coverageRules.keys) {
    tasks.check {
        dependsOn(tasks.jacocoTestCoverageVerification)
    }
}
