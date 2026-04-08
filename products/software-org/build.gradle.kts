plugins {
    `java-library`
}

description = "Software-Org Product - Agentic software organization simulation"

subprojects {
    group = "com.ghatana.softwareorg"
    version = rootProject.version
}

// Root project aggregates all software-org modules
// When building software-org, all submodules are built
dependencies {
    // Framework and domain models - use platform domain instead of local
    api(project(":platform:java:domain"))
    // "api"(project(":products:software-org:libs:java:agents"))
    // "api"(project(":products:software-org:libs:java:workflows"))
    // "api"(project(":products:software-org:libs:java:testing"))

    // Consolidated departments module (all 10 departments in one module)
    "api"(project(":products:software-org:libs:java:departments"))

    // Virtual-Org framework — flows transitively via domain-model → departments.
    // software-org IS-A virtual-org extension (subclasses Department, AbstractOrganization).
    // See ADR: cross-product dependency is intentional; framework provides the organization
    // simulation primitives that software-org specializes.
    // Direct dep removed from aggregate build — use domain-model:api for compile scope.

    // Core HTTP server
    "implementation"(project(":platform:java:http"))

    // Make framework and department modules available on the aggregate product classpath
    // Framework used by many tests and by product code
    
    // Consolidated departments module (tests reference department classes directly)
    "testImplementation"(project(":products:software-org:libs:java:departments"))

    implementation(project(":platform:java:domain"))

    // Test libraries used by integration and performance tests
    "testImplementation"(project(":platform:java:testing"))
    "testImplementation"(libs.assertj.core)
    "testImplementation"(libs.mockito.core)
    "testImplementation"(libs.mockito.junit.jupiter)
    "testRuntimeOnly"(libs.junit.jupiter.engine)

    // JMH benchmark support for SoftwareOrgPerformanceBenchmark (compiled as part of tests)
    "testImplementation"(libs.jmh.core)
    "testAnnotationProcessor"(libs.jmh.generator.annprocess)
}

tasks.test {
    useJUnitPlatform()
}
