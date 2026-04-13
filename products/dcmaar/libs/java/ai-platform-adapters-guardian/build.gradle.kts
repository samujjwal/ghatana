plugins {
    id("java-module")
}

group = "com.ghatana.guardian"
version = rootProject.version

java {
    // Keep sources/javadoc jars for this publishable module
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    // Core agent and observability abstractions
    api(project(":platform:java:domain"))
    api(project(":platform:java:observability"))

    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}
