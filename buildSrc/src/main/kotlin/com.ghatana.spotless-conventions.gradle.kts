import com.diffplug.gradle.spotless.SpotlessExtension

/**
 * Spotless Convention Plugin
 *
 * @doc.type convention-plugin
 * @doc.purpose Configures Spotless formatting for misc, XML, and Protobuf files.
 *              Activates only when the Spotless plugin is already on the project's
 *              plugin manager (opt-in, applied via com.ghatana.quality-conventions
 *              or explicitly declared).
 * @doc.layer build
 * @doc.pattern Convention
 *
 * NOTE: For full quality gates (Checkstyle + PMD + Spotless + JaCoCo) prefer
 * com.ghatana.quality-conventions.  This plugin is a narrower opt-in that only
 * handles Spotless formatting concerns.
 */

pluginManager.withPlugin("com.diffplug.spotless") {
    project.extensions.configure(SpotlessExtension::class.java) {
        format("misc") {
            target("*.gradle", ".gitignore")
            trimTrailingWhitespace()
            endWithNewline()
        }
        format("proto") {
            target("**/*.proto")
            trimTrailingWhitespace()
            endWithNewline()
            licenseHeaderFile(
                project.rootProject.file("config/spotless/license-header.proto"),
                "//"
            )
        }
        format("xml") {
            target("**/*.xml", "**/*.xsd")
            trimTrailingWhitespace()
            endWithNewline()
        }
    }
}
