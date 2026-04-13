plugins {
    id("java-module")
}

description = "YAPPC Testing Specialists - Test generation, validation, and coverage agents"


dependencies {
    api(project(":products:yappc:core:agents:runtime"))
    api(project(":products:yappc:core:agents:common"))
    api(project(":products:yappc:core:agents:code-specialists"))
    api(project(":products:yappc:core:agents:architecture-specialists"))
    api(project(":products:yappc:core:ai"))
    api(project(":products:yappc:core:yappc-domain-impl"))
    api(project(":platform:java:agent-core"))

    implementation(libs.activej.promise)
    implementation(libs.activej.eventloop)
    implementation(libs.slf4j.api)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.assertj.core)
    testImplementation("com.tngtech.archunit:archunit-junit5:1.0.1")
    testImplementation(project(":platform:java:testing"))
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    // useJUnitPlatform() already applied by java-module
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
    finalizedBy(tasks.jacocoTestReport)
}

// jacoco and jacocoTestReport configured by java-module; keep coverage verification thresholds

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                counter = "BRANCH"
                value   = "COVEREDRATIO"
                minimum = "0.00".toBigDecimal()
            }
            limit {
                counter = "LINE"
                value   = "COVEREDRATIO"
                minimum = "0.00".toBigDecimal()
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

// java-module already sets UTF-8 encoding and -Xlint:unchecked/-Xlint:deprecation
