plugins {
    `java-library`
}

description = "Software-Org Product - Agentic software organization simulation"

subprojects {
    group = "com.ghatana.softwareorg"
    version = "1.0.0-SNAPSHOT"
}

// Root project aggregates all software-org modules
// When building software-org, all submodules are built
dependencies {
    // Framework and domain models - use platform domain instead of local
    api(project(":platform:java:domain"))
    // "api"(project(":products:software-org:libs:java:agents"))
    // "api"(project(":products:software-org:libs:java:workflows"))
    // "api"(project(":products:software-org:libs:java:testing"))

    // All department modules
    "api"(project(":products:software-org:libs:java:departments:engineering"))
    "api"(project(":products:software-org:libs:java:departments:qa"))
    "api"(project(":products:software-org:libs:java:departments:devops"))
    "api"(project(":products:software-org:libs:java:departments:support"))
    "api"(project(":products:software-org:libs:java:departments:product"))
    "api"(project(":products:software-org:libs:java:departments:sales"))
    "api"(project(":products:software-org:libs:java:departments:finance"))
    "api"(project(":products:software-org:libs:java:departments:hr"))
    "api"(project(":products:software-org:libs:java:departments:compliance"))
    "api"(project(":products:software-org:libs:java:departments:marketing"))

    // Virtual-Org framework — flows transitively via domain-model → departments.
    // software-org IS-A virtual-org extension (subclasses Department, AbstractOrganization).
    // See ADR: cross-product dependency is intentional; framework provides the organization
    // simulation primitives that software-org specializes.
    // Direct dep removed from aggregate build — use domain-model:api for compile scope.

    // Core HTTP server
    "implementation"(project(":platform:java:http"))

    // Make framework and department modules available on the aggregate product classpath
    // Framework used by many tests and by product code
    
    // Department modules (tests reference department classes directly)
    "testImplementation"(project(":products:software-org:libs:java:departments:engineering"))
    "testImplementation"(project(":products:software-org:libs:java:departments:qa"))
    "testImplementation"(project(":products:software-org:libs:java:departments:devops"))
    "testImplementation"(project(":products:software-org:libs:java:departments:support"))
    "testImplementation"(project(":products:software-org:libs:java:departments:product"))
    "testImplementation"(project(":products:software-org:libs:java:departments:sales"))
    "testImplementation"(project(":products:software-org:libs:java:departments:finance"))
    "testImplementation"(project(":products:software-org:libs:java:departments:hr"))
    "testImplementation"(project(":products:software-org:libs:java:departments:compliance"))
    "testImplementation"(project(":products:software-org:libs:java:departments:marketing"))

    implementation(project(":platform:java:domain"))

    // Test libraries used by integration and performance tests
    "testImplementation"(project(":platform:java:testing"))
    "testImplementation"(libs.assertj.core)
    "testImplementation"(libs.mockito.core)
    "testImplementation"(libs.mockito.junit.jupiter)
    "testRuntimeOnly"("org.junit.jupiter:junit-jupiter-engine")

    // JMH benchmark support for SoftwareOrgPerformanceBenchmark (compiled as part of tests)
    "testImplementation"("org.openjdk.jmh:jmh-core:1.37")
    "testAnnotationProcessor"("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}

tasks.test {
    useJUnitPlatform()
}
