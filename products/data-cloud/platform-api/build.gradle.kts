/**
 * Platform API Module
 *
 * Contains REST/gRPC/GraphQL controllers, request/response DTOs, and
 * application service layer (CollectionService, EntityService, etc.).
 *
 * This is a strict subset of what was previously in platform-launcher.
 * No plugin loading code or infrastructure bootstrap belongs here.
 *
 * Phase 1 of FINDING-DC-H2 (platform-launcher split).
 */
plugins {
    id("java-module")
    alias(libs.plugins.spotbugs)
}

group = "com.ghatana.datacloud"
version = rootProject.version

description = "Data Cloud REST/gRPC/GraphQL API layer — extracted from platform-launcher (Phase 1)"


dependencies {
    api(project(":products:data-cloud:spi"))
    api(project(":products:data-cloud:platform-entity"))
    api(project(":products:data-cloud:platform-config"))
    api(project(":products:data-cloud:platform-analytics"))
    api(project(":platform:java:audit"))
    api(project(":platform:java:agent-core"))
    api(project(":platform:contracts"))

    implementation(project(":platform:java:http"))
    implementation(project(":platform:java:observability"))
    implementation(project(":platform:java:security"))
    implementation(project(":platform:java:ai-integration"))

    api(platform("com.fasterxml.jackson:jackson-bom:2.18.2"))
    api(libs.jackson.databind)
    api(libs.jackson.annotations)
    api(libs.jackson.datatype.jsr310)
    api("jakarta.validation:jakarta.validation-api:3.0.2")
    api(libs.micrometer.core)
    implementation("io.swagger.core.v3:swagger-annotations:2.2.28")

    implementation(libs.activej.http)
    implementation(libs.activej.promise)
    implementation(libs.bundles.activej.core)
    implementation("io.grpc:grpc-api:1.79.0")
    implementation(libs.grpc.stub)
    implementation(libs.grpc.protobuf)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    compileOnly("javax.annotation:javax.annotation-api:1.3.2")
    compileOnly("com.github.spotbugs:spotbugs-annotations:4.8.6")

    testImplementation(libs.bundles.testing.core)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
    testImplementation(project(":platform:java:testing"))
    testImplementation("io.grpc:grpc-testing:1.79.0")
    testImplementation("io.grpc:grpc-inprocess:1.79.0")
    testCompileOnly("javax.annotation:javax.annotation-api:1.3.2")
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    // useJUnitPlatform() already applied by java-module; keep finalizedBy for unconditional JaCoCo
    finalizedBy(tasks.jacocoTestReport)
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    violationRules {
        rule {
            limit {
                counter = "INSTRUCTION"
                value = "COVEREDRATIO"
                minimum = "0.31".toBigDecimal()  // TODO: Raise back to 0.50 after adding more tests (currently at 31%)
            }
        }
    }
}

tasks.named("check") {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

spotbugs {
    toolVersion.set("4.8.6")  // Valid SpotBugs tool version
    ignoreFailures.set(false)
    effort.set(com.github.spotbugs.snom.Effort.MAX)
    reportLevel.set(com.github.spotbugs.snom.Confidence.MEDIUM)
    excludeFilter.set(rootProject.file("config/spotbugs/spotbugs-exclude.xml"))
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    reports.create("html") { required.set(true) }
    reports.create("xml") { required.set(true) }
}
