plugins {
    id("java")
    id("application")
}

description = "Event tailing service for real-time feature ingestion"

dependencies {
    // Platform libraries (updated paths)
    implementation(project(":platform:java:ai-integration"))
    implementation(project(":products:data-cloud:spi"))
    implementation(project(":platform:java:observability"))
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:domain"))
    
    // ActiveJ runtime
    implementation(libs.activej.eventloop)
    implementation(libs.activej.promise)
    implementation(libs.activej.datastream)
    implementation(libs.activej.inject)
    implementation(libs.activej.http)
    
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
