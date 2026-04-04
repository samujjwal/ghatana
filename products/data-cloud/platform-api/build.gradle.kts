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
    id("java-library")
    id("jacoco")
    alias(libs.plugins.spotbugs)
}

group = "com.ghatana.datacloud"
version = rootProject.version

description = "Data Cloud REST/gRPC/GraphQL API layer — extracted from platform-launcher (Phase 1)"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    api(project(":products:data-cloud:spi"))
    api(project(":products:data-cloud:platform-entity"))
    api(project(":products:data-cloud:platform-config"))
    api(project(":products:data-cloud:platform-analytics"))
    api(project(":platform:java:audit"))
    api(project(":platform:contracts"))

    implementation(project(":platform:java:http"))
    implementation(project(":platform:java:observability"))
    implementation(project(":platform:java:security"))
    implementation(project(":platform:java:ai-integration"))

    api(platform(libs.jackson.bom))
    api(libs.jackson.databind)
    api(libs.jackson.annotations)
    api(libs.jakarta.validation.api)
    api(libs.micrometer.core)
    implementation(libs.swagger.annotations)

    implementation(libs.activej.http)
    implementation(libs.activej.promise)
    implementation(libs.activej.eventloop)
    implementation(libs.grpc.api)
    implementation(libs.grpc.stub)
    implementation(libs.grpc.protobuf)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    compileOnly(libs.javax.annotation.api)
    compileOnly(libs.spotbugs.annotations)

    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.archunit.junit5)
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.grpc.testing)
    testImplementation(libs.grpc.inprocess)
    testCompileOnly(libs.javax.annotation.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

jacoco {
    toolVersion = "0.8.11"
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
                minimum = "0.070".toBigDecimal()
            }
        }
    }
}

tasks.named("check") {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

spotbugs {
    toolVersion.set("4.8.6")
    ignoreFailures.set(false)
    effort.set(com.github.spotbugs.snom.Effort.MAX)
    reportLevel.set(com.github.spotbugs.snom.Confidence.MEDIUM)
    excludeFilter.set(rootProject.file("config/spotbugs/spotbugs-exclude.xml"))
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    reports.create("html") { required.set(true) }
    reports.create("xml") { required.set(true) }
}
