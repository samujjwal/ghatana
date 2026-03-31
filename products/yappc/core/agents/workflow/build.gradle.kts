plugins {
    id("java-library")
    id("jacoco")
}

group = "com.ghatana.products.yappc"
version = rootProject.version

description = "YAPPC Agent Workflow — SDLC phase step implementations (architecture, implementation, leads, requirements, enhancement, ops, testing phases)"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    // Runtime base (provides base agent types, step contracts, tools, dispatch)
    api(project(":products:yappc:core:agents:runtime"))

    // Platform deps used directly by workflow steps (api — types appear in public step signatures)
    api(project(":platform:java:database"))
    // TODO(ADAPTER-SEAM): data-cloud:spi leaks the DataCloud API into the workflow capability.
    //   Future: replace with DataStorePort, implemented by infrastructure:datacloud
    api(project(":products:data-cloud:spi"))
    api(project(":platform:java:workflow"))

    // ActiveJ for async operations
    implementation(libs.activej.promise)
    implementation(libs.activej.eventloop)

    // Jackson
    implementation(libs.jackson.databind)
    implementation(libs.jackson.dataformat.yaml)

    // Utilities
    implementation(libs.commons.lang3)
    implementation(libs.guava)

    // Annotations
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // Logging
    implementation(libs.slf4j.api)
    implementation(libs.logback.classic)

    // Testing
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
    finalizedBy(tasks.jacocoTestReport)
}

jacoco { toolVersion = "0.8.11" }

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

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:unchecked", "-Xlint:deprecation"))
}
