plugins {
    id("java")
    id("application")
}

description = "HTTP REST API service for AI Model Registry"

dependencies {
    // Platform libraries (updated paths)
    implementation(project(":platform:java:ai-integration"))
    implementation(project(":platform:java:http"))
    implementation(project(":platform:java:observability"))
    implementation(project(":platform:java:core"))
    
    // ActiveJ HTTP (via core abstractions - never direct imports)
    implementation(libs.activej.http)
    implementation(libs.activej.promise)
    implementation(libs.activej.inject)
    
    // Jackson for JSON
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)
    
    // Logging (Log4j2 + SLF4J, never Logback)
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
    mainClass.set("com.ghatana.services.airegistry.AiRegistryServiceLauncher")
}

tasks.test {
    useJUnitPlatform()
}
