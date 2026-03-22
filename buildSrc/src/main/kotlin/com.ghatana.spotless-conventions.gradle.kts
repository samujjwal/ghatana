import com.diffplug.gradle.spotless.SpotlessExtension

// Configures Spotless formatting for misc and proto files when the spotless plugin is present.

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
    }
}
