import org.gradle.api.artifacts.VersionCatalogsExtension

/**
 * Lombok Convention Plugin
 *
 * @doc.type convention-plugin
 * @doc.purpose Configures Lombok annotation processing consistently for main and
 *              test source sets.  Version is sourced from the version catalog;
 *              no fallback to a hardcoded string is permitted.
 * @doc.layer build
 * @doc.pattern Convention
 *
 * Apply to any module that uses Lombok annotations (@Data, @Builder, @Slf4j, etc.):
 *
 *   plugins {
 *       id("java-library")
 *       id("com.ghatana.java-conventions")
 *       id("com.ghatana.lombok-conventions")
 *   }
 *
 * Configures:
 *   - compileOnly / annotationProcessor for main sources
 *   - testCompileOnly / testAnnotationProcessor for test sources
 */

plugins {
    java
}

dependencies {
    val libs = project.extensions.findByType(VersionCatalogsExtension::class.java)?.named("libs")
    val lombokCoordinate = libs?.findLibrary("lombok")
        ?.orElse(null)
        ?.get()
        ?: error(
            "lombok library not found in libs.versions.toml — " +
                "add 'lombok = { module = \"org.projectlombok:lombok\", version.ref = \"lombok\" }' " +
                "to gradle/libs.versions.toml"
        )

    "compileOnly"(lombokCoordinate)
    "annotationProcessor"(lombokCoordinate)
    "testCompileOnly"(lombokCoordinate)
    "testAnnotationProcessor"(lombokCoordinate)
}
