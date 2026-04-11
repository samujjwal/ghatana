plugins {
    id("java-library")
    id("com.ghatana.java-conventions")
}

description = "YAPPC Domain API - Domain models, ports, and events (Phase 2.1 consolidation)"

dependencies {
    // Platform contracts
    api(project(":platform:contracts"))
    api(project(":platform:java:core"))
    api(project(":platform:java:domain"))

    // Events (will be relocated to platform:event in Phase 2.3)
    api(project(":products:yappc:core:yappc-shared"))

    // Lombok
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(project(":platform:java:testing"))
}

tasks.test {
    useJUnitPlatform()
}
