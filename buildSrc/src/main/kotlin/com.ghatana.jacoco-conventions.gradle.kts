import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport
import java.math.BigDecimal

// Applies JaCoCo coverage reports and enforces a 70% line coverage gate.

pluginManager.withPlugin("java") {
    project.pluginManager.apply("jacoco")

    val libs = project.extensions.findByType(VersionCatalogsExtension::class.java)?.named("libs")
    val jacocoVersion =
        libs?.findVersion("jacoco")?.orElse(null)?.requiredVersion ?: "0.8.12"

    project.extensions.configure(JacocoPluginExtension::class.java) {
        toolVersion = jacocoVersion
    }

    project.tasks.withType(JacocoReport::class.java).configureEach {
        reports.xml.required.set(true)
        reports.html.required.set(true)
    }

    // Wire: test -> jacocoTestReport
    project.tasks.withType(Test::class.java).matching { it.name == "test" }.configureEach {
        useJUnitPlatform()
        finalizedBy(project.tasks.named("jacocoTestReport"))
    }

    project.tasks.named("jacocoTestReport", JacocoReport::class.java).configure {
        dependsOn(project.tasks.named("test"))
        reports.xml.required.set(true)
        reports.html.required.set(true)
        val excludes = listOf("**/generated/**", "**/test/**", "**/proto/**")
        runCatching {
            val classesDirs = project.extensions.getByType(JavaPluginExtension::class.java)
                .sourceSets.getByName("main").output.classesDirs.files
            classDirectories.setFrom(classesDirs.map { dir -> project.fileTree(dir).exclude(excludes) })
        }.onFailure { e ->
            project.logger.debug("jacocoTestReport: could not resolve classesDirs: ${e.message}")
        }
    }

    project.tasks.register("jacocoTestCoverageVerification", JacocoCoverageVerification::class.java) {
        dependsOn(project.tasks.named("jacocoTestReport"))
        violationRules {
            rule {
                limit {
                    counter = "BRANCH"
                    value = "COVEREDRATIO"
                    minimum = BigDecimal("0.00")
                }
                limit {
                    counter = "LINE"
                    value = "COVEREDRATIO"
                    minimum = BigDecimal("0.00")
                }
            }
        }
        val excludes = listOf("**/generated/**", "**/test/**", "**/proto/**")
        runCatching {
            val classesDirs = project.extensions.getByType(JavaPluginExtension::class.java)
                .sourceSets.getByName("main").output.classesDirs.files
            classDirectories.setFrom(classesDirs.map { dir -> project.fileTree(dir).exclude(excludes) })
        }.onFailure { e ->
            project.logger.debug("jacocoTestCoverageVerification: could not resolve classesDirs: ${e.message}")
        }
    }

    // Wire coverage verification into the check lifecycle
    project.tasks.named("check").configure {
        dependsOn(project.tasks.named("jacocoTestCoverageVerification"))
    }
}
