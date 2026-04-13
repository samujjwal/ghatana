plugins {
    id("java-module")
    // alias(libs.plugins.lombok) // Disabled: Gradle 8.10 afterEvaluate conflict
}

group = "com.ghatana.products.software-org"
version = rootProject.version

dependencies {
    // Platform modules
    api(project(":platform:java:core"))
    api(project(":platform:java:domain"))
    api(project(":platform:java:agent-core"))

    // Contracts - software-org contracts
    api(project(":platform:contracts"))  // Common types
    api(project(":products:aep:aep-operator-contracts"))  // Agent contracts (for DevSecOps)

    // Virtual-Org Framework
    api(project(":products:virtual-org:modules:framework"))

    // Lombok
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // Annotations
    compileOnly(libs.jetbrains.annotations)

    // Jakarta validation

    // Jackson for serialization
    api(platform(libs.jackson.bom))
    api(libs.jackson.databind)
    api(libs.jackson.annotations)
    api(libs.jackson.datatype.jsr310)
    api(libs.jackson.datatype.jdk8)

    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(project(":platform:java:testing"))
}
