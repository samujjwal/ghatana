plugins {
    id("java-library")
}

group = "com.ghatana.products.yappc.services"
version = rootProject.version.toString()
base.archivesName.set("yappc-services-infrastructure")

description = "YAPPC Services: Infrastructure — Data integration and persistence"

dependencies {
    // Internal domain (full monorepo paths)
    implementation(project(":products:yappc:services:domain"))
    implementation(project(":products:yappc:libs:java:yappc-domain"))
    implementation(project(":products:yappc:infrastructure:datacloud"))

    // Core Platform Libraries
    implementation(project(":platform:java:core"))
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
