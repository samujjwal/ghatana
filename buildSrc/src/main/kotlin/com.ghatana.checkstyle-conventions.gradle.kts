import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.CheckstyleExtension

/**
 * Checkstyle Convention Plugin
 *
 * @doc.type convention-plugin
 * @doc.purpose Applies Checkstyle static-analysis using the version and rule files
 *              defined in the project's shared configuration.  Version is sourced
 *              exclusively from the version catalog.
 * @doc.layer build
 * @doc.pattern Convention
 *
 * NOTE: This plugin is a focused checkstyle-only option.  New modules that need
 * the full quality suite (Checkstyle + PMD + JaCoCo + Spotless) should prefer
 * com.ghatana.quality-conventions instead.
 *
 * This plugin is applied automatically when the 'java' plugin is present.
 */

pluginManager.withPlugin("java") {
    project.pluginManager.apply("checkstyle")

    val libs = project.extensions.findByType(VersionCatalogsExtension::class.java)?.named("libs")
    val checkstyleVersion = libs?.findVersion("checkstyle")?.orElse(null)?.requiredVersion
        ?: error("checkstyle version not found in libs.versions.toml")

    project.extensions.configure(CheckstyleExtension::class.java) {
        toolVersion = checkstyleVersion
        configFile = project.rootProject.file("config/checkstyle/checkstyle.xml")
        configProperties = mapOf(
            "checkstyle.cache.file" to
                "${project.layout.buildDirectory.get()}/checkstyle.cache",
            "checkstyle.suppressions.file" to
                project.rootProject.file("config/checkstyle/suppressions.xml").absolutePath
        )
        isIgnoreFailures = false
        isShowViolations = true
    }

    project.tasks.withType(Checkstyle::class.java).configureEach {
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }
}
