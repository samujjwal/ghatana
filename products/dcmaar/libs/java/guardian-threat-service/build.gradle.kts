plugins {
    id("java")
    id("application")
}

description = "Guardian Threat & Health Scoring Service"

dependencies {
    // Core HTTP server abstractions
    implementation(project(":platform:java:http"))
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:observability"))
    implementation(project(":platform:java:domain"))
    // Multi-tenancy
    implementation(project(":platform:java:governance"))

    // Guardian-specific adapter
    implementation(project(":products:dcmaar:libs:java:ai-platform-adapters-guardian"))

    // ActiveJ (via core abstractions)
    implementation(libs.activej.http)
    implementation(libs.activej.promise)
    implementation(libs.activej.inject)

    // Jackson for JSON
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    // Logging
    implementation(libs.log4j.core)
    implementation(libs.log4j.slf4j.impl)

    // Testing
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    mainClass = "com.ghatana.guardian.threat.GuardianThreatServiceLauncher"
}

tasks.test {
    useJUnitPlatform()
}
