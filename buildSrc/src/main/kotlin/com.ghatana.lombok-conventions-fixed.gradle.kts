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
    // Use hardcoded version for buildSrc compatibility
    val lombokVersion = "1.18.36"
    val lombokCoordinate = "org.projectlombok:lombok:$lombokVersion"

    compileOnly(lombokCoordinate)
    annotationProcessor(lombokCoordinate)
    testCompileOnly(lombokCoordinate)
    testAnnotationProcessor(lombokCoordinate)
}
