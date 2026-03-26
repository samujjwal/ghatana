plugins {
    id("java")
    id("application")
}

description = "EventCloud tailing service for real-time feature ingestion (migrated from shared-services per ADR-013)"

dependencies {
    // Platform libraries (updated paths)
    implementation(project(":platform:java:ai-integration"))
    implementation(project(":products:data-cloud:spi"))
    // WarmTierEventLogStore is used for the postgres ingest mode (postgres backend)
    implementation(project(":products:data-cloud:platform-launcher"))
    implementation(project(":platform:java:observability"))
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:domain"))

    // Connection pool for PostgreSQL (production FeatureStoreService)
    implementation(libs.hikaricp)
    
    // ActiveJ runtime
    implementation(libs.activej.eventloop)
    implementation(libs.activej.promise)
    implementation(libs.activej.datastream)
    implementation(libs.activej.inject)
    
    // Jackson for JSON
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)
    
    // Logging (Log4j2 + SLF4J)
    implementation(libs.log4j.core)
    implementation(libs.log4j.slf4j.impl)
    
    // Testing
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
}

application {
    mainClass.set("com.ghatana.services.featurestore.FeatureStoreIngestLauncher")
}

tasks.test {
    useJUnitPlatform()
}
