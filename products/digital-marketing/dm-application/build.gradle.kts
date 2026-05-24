plugins {
    id("java-module")
}

apply(from = "../gradle/dmos-quality-gates.gradle.kts")

// P2-003: Task to generate feature flags from canonical manifest
tasks.register("generateFeatureFlags") {
    group = "code generation"
    description = "Generate Java and TypeScript feature flag constants from FEATURE_FLAGS_MANIFEST.json"
    
    val scriptPath = project.file("../scripts/generate-feature-flags.mjs")
    val workingDirectory = project.projectDir
    
    doLast {
        if (!scriptPath.exists()) {
            throw GradleException("Feature flag generator script not found: ${scriptPath.absolutePath}")
        }

        val command = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            listOf("cmd", "/d", "/c", "node", scriptPath.absolutePath)
        } else {
            listOf("node", scriptPath.absolutePath)
        }

        val process = ProcessBuilder(command)
            .directory(workingDirectory)
            .inheritIO()
            .start()
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw GradleException("Feature flag generation failed with exit code $exitCode")
        }
    }
}

tasks.named("compileJava") {
    dependsOn("generateFeatureFlags")
}

group = "com.ghatana.digitalmarketing"
description = "DMOS Application — application service layer implementing campaign, workspace, and audience use cases"

dependencies {
    api(project(":products:digital-marketing:dm-core-contracts"))
    api(project(":products:digital-marketing:dm-domain"))
    api(project(":products:digital-marketing:dm-domain-packs"))
    api(project(":products:digital-marketing:dm-kernel-bridge"))
    api(project(":platform-kernel:kernel-core"))
    api(project(":platform:java:core"))
    api(project(":platform:java:observability"))
    api(project(":platform:java:security"))
    api(project(":platform-plugins:plugin-compliance"))
    api(project(":platform-plugins:plugin-consent"))

    compileOnly(libs.spotbugs.annotations)
    implementation(libs.activej.promise)

    testImplementation(libs.bundles.testing.core)
    testImplementation(project(":platform-kernel:kernel-testing"))
    testImplementation(project(":platform:java:testing"))
    testImplementation(project(":products:digital-marketing:dm-infra"))
    testImplementation("org.testcontainers:testcontainers:1.19.0")
    testImplementation("org.testcontainers:postgresql:1.19.0")
    testRuntimeOnly(libs.junit.jupiter.engine)
}

tasks.test {
    useJUnitPlatform {
        excludeTags("integration")
    }
    finalizedBy(tasks.jacocoTestReport)
}

// Separate task for integration tests
tasks.register("testIntegration") {
    group = "verification"
    description = "Run integration tests only"
    dependsOn(tasks.test)
    doFirst {
        tasks.test {
            useJUnitPlatform {
                includeTags("integration")
            }
        }
    }
}

// Task to run all tests (unit + integration)
tasks.register("testAll") {
    group = "verification"
    description = "Run all tests (unit + integration)"
    dependsOn(tasks.test)
    doFirst {
        tasks.test {
            useJUnitPlatform {
                // No tag filters - run all tests
            }
        }
    }
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

// Exclude integration tests from coverage verification
tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.60".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.45".toBigDecimal()
            }
        }
    }
}

tasks.named("check") {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
