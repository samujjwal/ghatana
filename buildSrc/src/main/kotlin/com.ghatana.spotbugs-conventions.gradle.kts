import org.gradle.api.artifacts.VersionCatalogsExtension

/**
 * SpotBugs Convention Plugin
 *
 * @doc.type convention-plugin
 * @doc.purpose Adds the FindSecBugs security scanner to the SpotBugs plugin classpath
 *              when the SpotBugs Gradle plugin is present.  Version is sourced from
 *              the version catalog (gradle/libs.versions.toml).
 * @doc.layer build
 * @doc.pattern Convention
 *
 * This plugin intentionally avoids direct SpotBugs class imports to prevent
 * buildSrc classpath conflicts.  Full SpotBugs extension configuration (effort,
 * report level, exclude filters) is left to the consuming module's build file.
 *
 * Usage:
 *   plugins {
 *       alias(libs.plugins.spotbugs)
 *       id("com.ghatana.spotbugs-conventions")
 *   }
 *
 *   // Then configure in the module:
 *   spotbugs {
 *       ignoreFailures.set(false)
 *       effort.set(com.github.spotbugs.snom.Effort.MAX)
 *       excludeFilter.set(file("config/spotbugs/exclude.xml"))
 *   }
 */

pluginManager.withPlugin("com.github.spotbugs") {
    val libs = project.extensions.findByType(VersionCatalogsExtension::class.java)?.named("libs")

    val findsecbugsVersion = libs?.findVersion("findsecbugs-plugin")?.orElse(null)?.requiredVersion
        ?: error("findsecbugs-plugin version not found in libs.versions.toml")

    // Add the FindSecBugs security scanner to the SpotBugs plugin classpath
    project.dependencies {
        add("spotbugsPlugins", "com.h3xstream.findsecbugs:findsecbugs-plugin:$findsecbugsVersion")
    }

    logger.lifecycle(
        "[spotbugs-conventions] FindSecBugs $findsecbugsVersion added to ${project.path}"
    )
}
