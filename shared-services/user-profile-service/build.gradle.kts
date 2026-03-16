plugins {
    id("java-library")
}

description = "User Profile Service - Centralised user preferences and profile management"

dependencies {
    // Platform HTTP + config
    api(project(":platform:java:http"))
    api(project(":platform:java:config"))

    // Platform security (JWT validation for tenant/user extraction)
    implementation(project(":platform:java:security"))

    // ActiveJ
    implementation(libs.activej.http)
    implementation(libs.activej.promise)
    implementation(libs.activej.inject)
    implementation(libs.activej.launcher)

    // Database
    implementation(project(":platform:java:database"))
    implementation(libs.postgresql)
    implementation(libs.hikaricp)

    // JSON serialisation
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    // Observability
    implementation(project(":platform:java:observability"))

    // Lombok
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // Test
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.assertj.core)
    testImplementation(libs.junit.jupiter)
}
