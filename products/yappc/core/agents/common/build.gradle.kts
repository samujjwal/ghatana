plugins {
    id("java-library")
    id("jacoco")
}

description = "YAPPC Agent Common - Shared Input/Output classes and interfaces for all agent modules"

dependencies {
    api(project(":products:yappc:core:yappc-domain-impl"))
    api(project(":platform:java:agent-core"))

    implementation(libs.activej.promise)
    implementation(libs.slf4j.api)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.lombok)
    testAnnotationProcessor(libs.lombok)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}
