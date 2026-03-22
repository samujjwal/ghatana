plugins {
    id("java-library")
}

group = "com.ghatana.products.yappc.services"
version = rootProject.version.toString()
base.archivesName.set("yappc-services-platform")

description = "YAPPC Services: Platform — Combined domain and infrastructure services (merges services:domain + services:infrastructure)"

dependencies {
    // YAPPC domain library
    implementation(project(":products:yappc:libs:java:yappc-domain"))

    // Core lifecycle and infrastructure
    implementation(project(":products:yappc:core:lifecycle"))
    implementation(project(":products:yappc:infrastructure:datacloud"))
    implementation(project(":products:yappc:backend:auth"))  // security scanning classes moved here from infrastructure:security

    // Core Platform Libraries
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:domain"))
    implementation(project(":platform:java:database"))
    implementation(project(":platform:java:observability"))
    implementation(project(":platform:java:event-cloud"))

    // Data Cloud
    implementation(project(":products:data-cloud:platform"))

    // ActiveJ for async
    implementation(libs.activej.promise)
    implementation(libs.activej.inject)

    // Database
    implementation("com.zaxxer:HikariCP:5.1.0")
    runtimeOnly("org.postgresql:postgresql:42.7.1")

    // JSON Processing
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    // Validation
    implementation("jakarta.validation:jakarta.validation-api:3.0.2")
    implementation("org.hibernate.validator:hibernate-validator:8.0.1.Final")

    // Logging
    implementation(libs.slf4j.api)

    // Testing
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.testcontainers:junit-jupiter:1.19.3")
    testImplementation("org.testcontainers:postgresql:1.19.3")
}
