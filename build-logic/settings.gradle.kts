/**
 * Build Logic - Included Build for Convention Plugins
 *
 * This included build provides convention plugins that can access
 * the main project's version catalog directly.
 */

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"

include("conventions")
