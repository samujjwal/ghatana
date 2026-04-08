import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport
import java.math.BigDecimal

/**
 * JaCoCo Convention Plugin
 *
 * @doc.type convention-plugin
 * @doc.purpose Applies JaCoCo coverage reports and enforces configurable branch/line
 *              coverage gates.  Versions are sourced from the version catalog.
 * @doc.layer build
 * @doc.pattern Convention
 *
 * NOTE: New modules should prefer com.ghatana.testing-conventions which includes
 * this functionality alongside integration-test profile management and structured
 * test logging.  This plugin is retained for modules that apply it explicitly
 * (e.g. products/yappc subprojects).
 *
 * Coverage thresholds can be overridden per-module via Gradle properties:
 *   ./gradlew test -PjacocoMinBranch=0.70 -PjacocoMinLine=0.75
 */

pluginManager.withPlugin("java") {
    project.pluginManager.apply("jacoco")

    val libs = project.extensions.findByType(VersionCatalogsExtension::class.java)?.named("libs")
    val jacocoVersion = libs?.findVersion("jacoco")?.orElse(null)?.requiredVersion
        ?: error("jacoco version not found in libs.versions.toml")

    project.extensions.configure(JacocoPluginExtension::class.java) {
        toolVersion = jacocoVersion
    }

    // ── JaCoCo Report ─────────────────────────────────────────────────────
    project.tasks.withType(JacocoReport::class.java).configureEach {
        reports.xml.required.set(true)
        reports.html.required.set(true)
        reports.csv.required.set(false)
    }

    project.tasks.withType(Test::class.java).matching { it.name == "test" }.configureEach {
        useJUnitPlatform()
        finalizedBy(project.tasks.named("jacocoTestReport"))
    }

    project.tasks.named("jacocoTestReport", JacocoReport::class.java).configure {
        dependsOn(project.tasks.named("test"))
        val excludes = listOf("**/generated/**", "**/test/**", "**/proto/**")
        runCatching {
            val classesDirs = project.extensions.getByType(JavaPluginExtension::class.java)
                .sourceSets.getByName("main").output.classesDirs.files
            classDirectories.setFrom(
                classesDirs.map { dir -> project.fileTree(dir).exclude(excludes) }
            )
        }.onFailure { e ->
            project.logger.debug("jacocoTestReport: could not resolve classesDirs: ${e.message}")
        }
    }

    // ── Coverage Verification ──────────────────────────────────────────────
    val minBranch: BigDecimal =
        project.findProperty("jacocoMinBranch")?.toString()?.toBigDecimalOrNull()
            ?: BigDecimal("0.50")
    val minLine: BigDecimal =
        project.findProperty("jacocoMinLine")?.toString()?.toBigDecimalOrNull()
            ?: BigDecimal("0.60")

    project.tasks.named(
        "jacocoTestCoverageVerification",
        JacocoCoverageVerification::class.java
    ).configure {
        dependsOn(project.tasks.named("jacocoTestReport"))
        violationRules {
            rule {
                limit {
                    counter = "BRANCH"
                    value = "COVEREDRATIO"
                    minimum = minBranch
                }
                limit {
                    counter = "LINE"
                    value = "COVEREDRATIO"
                    minimum = minLine
                }
            }
        }
        val excludes = listOf("**/generated/**", "**/test/**", "**/proto/**")
        runCatching {
            val classesDirs = project.extensions.getByType(JavaPluginExtension::class.java)
                .sourceSets.getByName("main").output.classesDirs.files
            classDirectories.setFrom(
                classesDirs.map { dir -> project.fileTree(dir).exclude(excludes) }
            )
        }.onFailure { e ->
            project.logger.debug(
                "jacocoTestCoverageVerification: could not resolve classesDirs: ${e.message}"
            )
        }
    }

    // Wire coverage verification into the standard `check` lifecycle
    project.tasks.named("check").configure {
        dependsOn(project.tasks.named("jacocoTestCoverageVerification"))
    }
}
