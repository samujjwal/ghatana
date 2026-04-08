plugins {
    id("java-library")
    id("jacoco")
}

description = "YAPPC Scaffold Engine - Core scaffolding orchestration and AI-assisted pipeline logic"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

dependencies {
    api(project(":products:yappc:core:scaffold:templates"))
    api(project(":products:yappc:core:ai"))
    api(project(":products:yappc:core:yappc-domain-impl"))
    api(project(":platform:java:domain"))
    api(project(":platform:java:core"))
    implementation(project(":platform:java:http"))
    implementation(project(":platform:java:observability"))
    api(project(":products:yappc:core:yappc-infrastructure"))

    api(libs.jackson.databind)
    api(libs.jackson.annotations)
    api(libs.jackson.datatype.jsr310)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.slf4j.api)

    // OpenTelemetry for unified telemetry
    api(libs.opentelemetry.api)
    implementation(libs.opentelemetry.sdk)
    implementation(libs.opentelemetry.exporter.otlp)
    implementation(libs.opentelemetry.sdk.testing)

    // ActiveJ
    api(libs.activej.inject)
    implementation(libs.activej.common)
    implementation(libs.activej.boot)
    implementation(libs.activej.promise)

    // Validation
    api(libs.jakarta.validation.api)
    implementation(libs.hibernate.validator)

    implementation(libs.joda.time)
    implementation(libs.commons.text)

    implementation(project(":platform:java:runtime"))
    // platform:java:testing provides TestStatus and other domain types used by
    // the scaffold engine's failure-injection testing framework
    implementation(project(":platform:java:testing"))

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockito.core)
    testImplementation(libs.assertj.core)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
    finalizedBy(tasks.jacocoTestReport)
}

// JaCoCo version managed by convention plugin

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                counter = "BRANCH"
                value   = "COVEREDRATIO"
                minimum = "0.15".toBigDecimal()
            }
            limit {
                counter = "LINE"
                value   = "COVEREDRATIO"
                minimum = "0.15".toBigDecimal()
            }
        }
    }
    classDirectories.setFrom(
        fileTree(layout.buildDirectory.dir("classes/java/main")) {
            exclude(
                "**/package-info.class",
                "**/*Config.class",
                "**/*Module.class",
                "**/*Launcher.class",
                "**/*Bootstrapper.class",
                "**/generated/**"
            )
        }
    )
}

