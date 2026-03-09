plugins {
    id("java-library")
}

// Determine project path based on whether we're in standalone yappc or root ghatana build
val yappcDomainPath = if (findProject(":products:yappc:libs:java:yappc-domain") != null) {
    ":products:yappc:libs:java:yappc-domain"
} else {
    ":libs:java:yappc-domain"
}

dependencies {
    // Re-export the shared domain API (if present) so consumers referencing
    // ':products:yappc:domain' still get those classes transitively.
    api(project(yappcDomainPath))

    // Core platform dependencies used by YAPPC domain components
    api(project(":platform:java:agent-framework"))
    api(project(":platform:java:observability"))
    api(project(":platform:java:ai-integration"))
    api(project(":platform:java:http"))

    // ActiveJ HTTP types used by the agent HTTP controller
    implementation(libs.activej.http)

    // ActiveJ Promise (from merged domain:task)
    implementation(libs.activej.promise)

    // JSON
    implementation(libs.jackson.databind)

    // YAML parsing (from merged domain:service — YamlTaskDefinitionProvider)
    implementation("org.yaml:snakeyaml:2.0")

    // SLF4J (from merged domain:service)
    implementation(libs.slf4j.api)

    // Lombok (YAPPC domain model uses Lombok annotations)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // JetBrains annotations (from merged domain:task)
    compileOnly("org.jetbrains:annotations:24.0.1")

    // Hypersistence / hibernate-types for JSON/array mapping (needed for domain model classes)
    implementation("com.vladmihalcea:hibernate-types-60:2.21.1")

    // Testing
    testImplementation(project(":platform:java:testing"))
    testImplementation(project(":platform:java:runtime"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

description = "YAPPC product domain (merged: domain + service + task)"
