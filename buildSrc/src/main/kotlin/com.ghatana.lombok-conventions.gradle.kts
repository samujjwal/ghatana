/**
 * Lombok Convention Plugin - Fixed
 *
 * @doc.type convention-plugin
 * @doc.purpose Configures Lombok annotation processing consistently
 * @doc.layer build
 * @doc.pattern Convention
 */

plugins {
    java
}

dependencies {
    // Hardcoded version required due to buildSrc isolation
    // This version must be kept in sync with gradle/libs.versions.toml
    // See buildSrc/VERSION_SYNC.md for details
    val lombokVersion = "1.18.36"
    val lombokCoordinate = "org.projectlombok:lombok:$lombokVersion"

    compileOnly(lombokCoordinate)
    annotationProcessor(lombokCoordinate)
    testCompileOnly(lombokCoordinate)
    testAnnotationProcessor(lombokCoordinate)
}
