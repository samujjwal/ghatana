plugins {
    id("java-library")
    id("jacoco")
}

description = "YAPPC Scaffold Templates - Template loading, parsing, and rendering (foundational layer)"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

dependencies {
    api(project(":platform:java:domain"))
    api(project(":platform:java:core"))

    api(libs.jackson.databind)
    api(libs.jackson.annotations)
    api(libs.jackson.datatype.jsr310)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.slf4j.api)
    implementation(libs.networknt.validator)
    implementation("org.yaml:snakeyaml")

    implementation(libs.activej.common)

    // Handlebars template engine
    implementation("com.github.jknack:handlebars:4.3.1")

    // Diff library for text comparison
    implementation("com.googlecode.java-diff-utils:diffutils:1.3.0")

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockito.core)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
    finalizedBy(tasks.jacocoTestReport)
}

// JaCoCo configuration managed by convention plugin

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
