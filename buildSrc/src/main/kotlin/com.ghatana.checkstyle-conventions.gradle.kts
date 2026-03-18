import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.CheckstyleExtension

// Applies Checkstyle static analysis when the java plugin is present.

pluginManager.withPlugin("java") {
    project.pluginManager.apply("checkstyle")

    val libs = project.extensions.findByType(VersionCatalogsExtension::class.java)?.named("libs")
    val checkstyleVersion =
        libs?.findVersion("checkstyle")?.orElse(null)?.requiredVersion ?: "10.12.7"

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
        reports.xml.required.set(true)
        reports.html.required.set(true)
    }
}
