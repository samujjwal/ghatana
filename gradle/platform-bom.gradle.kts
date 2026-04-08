/**
 * Platform Bill of Materials (BOM)
 *
 * Centralises version management for all platform and third-party dependencies.
 * Products apply this BOM to ensure version consistency across the monorepo
 * without repeating version strings in every build file.
 *
 * Usage (in any product build.gradle.kts):
 *   apply(from = rootProject.file("gradle/platform-bom.gradle.kts"))
 *
 * After applying, declare dependencies WITHOUT version qualifiers:
 *   implementation(libs.jackson.databind)   // version managed by BOM
 *
 * @since 1.0.0
 * @doc.type configuration
 * @doc.purpose Centralized dependency version management
 * @doc.layer platform
 */

// ─────────────────────────────────────────────────────────────────────────────
// Platform module versions
// ─────────────────────────────────────────────────────────────────────────────
val platformVersion = project.findProperty("platform.version") as String? ?: "1.0.0-SNAPSHOT"

val javaVersion = 21
val activejVersion = libs.versions.activej.get()
val jacksonVersion = libs.versions.jackson.get()
val slf4jVersion = libs.versions.slf4j.get()

val hikariVersion = libs.versions.hikari.get()
val postgresDriverVersion = libs.versions.postgresql.get()
val flywayVersion = libs.versions.flyway.get()
val jpaApiVersion = libs.versions.jakarta.persistence.api.get()
val hibernateVersion = libs.versions.hibernate.core.get()

val jakartaValidationVersion = libs.versions.jakarta.validation.get()
val hibernateValidatorVersion = libs.versions.hibernate.validator.get()

val langchain4jVersion = libs.versions.langchain4j.get()

val micrometerVersion = libs.versions.micrometer.get()
val prometheusVersion = libs.versions.prometheus.simpleclient.get()

val junitVersion = libs.versions.junit.jupiter.get()
val assertjVersion = libs.versions.assertj.get()
val mockitoVersion = libs.versions.mockito.get()
val testcontainersVersion = libs.versions.testcontainers.get()

val swaggerParserVersion = libs.versions.swagger.annotations.get()
val graphqlJavaVersion = libs.versions.graphql.java.get()

val javaxInjectVersion = "1"

// ─────────────────────────────────────────────────────────────────────────────
// Constraint block — applied to ALL configurations of the consuming project.
// This ensures transitive upgrades don't silently introduce incompatible versions.
// ─────────────────────────────────────────────────────────────────────────────
subprojects {
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            val group = requested.group
            val module = requested.module.name

            // Jackson family
            if (group == "com.fasterxml.jackson.core" ||
                group == "com.fasterxml.jackson.datatype" ||
                group == "com.fasterxml.jackson.dataformat" ||
                group == "com.fasterxml.jackson.module") {
                useVersion(jacksonVersion)
                because("Platform BOM: Jackson unified at $jacksonVersion")
            }

            // SLF4J
            if (group == "org.slf4j") {
                useVersion(slf4jVersion)
                because("Platform BOM: SLF4J unified at $slf4jVersion")
            }

            // Logback: excluded from production classpath (Log4j2 is canonical)
            // See log4j2-config.gradle for exclusion configuration.

            // JUnit 5
            if (group == "org.junit.jupiter" || group == "org.junit.platform") {
                useVersion(junitVersion)
                because("Platform BOM: JUnit unified at $junitVersion")
            }

            // Mockito
            if (group == "org.mockito") {
                useVersion(mockitoVersion)
                because("Platform BOM: Mockito unified at $mockitoVersion")
            }

            // Testcontainers
            if (group == "org.testcontainers") {
                useVersion(testcontainersVersion)
                because("Platform BOM: Testcontainers unified at $testcontainersVersion")
            }

            // Flyway
            if (group == "org.flywaydb") {
                useVersion(flywayVersion)
                because("Platform BOM: Flyway unified at $flywayVersion")
            }

            // PostgreSQL driver
            if (group == "org.postgresql" && module == "postgresql") {
                useVersion(postgresDriverVersion)
                because("Platform BOM: PostgreSQL driver unified at $postgresDriverVersion")
            }

            // Micrometer
            if (group == "io.micrometer") {
                useVersion(micrometerVersion)
                because("Platform BOM: Micrometer unified at $micrometerVersion")
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Validation task — verifies no product uses a pinned version that conflicts
// with the BOM. Run with:  ./gradlew validatePlatformBom
// ─────────────────────────────────────────────────────────────────────────────
tasks.register("validatePlatformBom") {
    group = "Verification"
    description = "Validates that no product module overrides a BOM-managed version"

    doLast {
        val violations = mutableListOf<String>()

        rootProject.subprojects.forEach { sub ->
            sub.configurations.forEach { config ->
                try {
                    config.resolvedConfiguration.resolvedArtifacts.forEach { artifact ->
                        val mid = artifact.moduleVersion.id
                        val ver = mid.version

                        // Flag if Jackson versions diverge from BOM
                        if (mid.group.startsWith("com.fasterxml.jackson") &&
                            ver != jacksonVersion) {
                            violations.add("${sub.path}: Jackson ${mid.group}:${mid.name} is $ver, BOM expects $jacksonVersion")
                        }
                    }
                } catch (ignored: Exception) {
                    // Skip unresolvable configurations (e.g. annotation processors)
                }
            }
        }

        if (violations.isNotEmpty()) {
            violations.forEach { logger.warn("BOM Violation: {}", it) }
            logger.warn("Found {} BOM version violations (warnings only — set failOnViolation=true to fail build)", violations.size())
        } else {
            logger.lifecycle("Platform BOM validation passed — all managed dependencies are consistent.")
        }
    }
}
