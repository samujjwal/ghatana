plugins {
    id("java-library")
}

group = "com.ghatana.products.yappc.services"
version = rootProject.version.toString()
base.archivesName.set("yappc-services-domain")

description = "YAPPC Services: Domain — Business logic and domain models"

dependencies {
    // YAPPC domain library (full monorepo paths)
    implementation(project(":products:yappc:libs:java:yappc-domain"))

    // Core lifecycle services (for DomainServiceFacade)
    implementation(project(":products:yappc:core:lifecycle"))

    // Core Platform Libraries
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:domain"))
    implementation(project(":platform:java:observability"))

    // ActiveJ for async
    implementation(libs.activej.promise)
    implementation(libs.activej.inject)

    // JSON Processing
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
}
