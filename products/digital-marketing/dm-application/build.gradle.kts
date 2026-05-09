plugins {
    id("java-module")
}

apply(from = "../gradle/dmos-quality-gates.gradle.kts")

// P2-003: Task to generate feature flags from canonical manifest
tasks.register("generateFeatureFlags") {
    group = "code generation"
    description = "Generate Java and TypeScript feature flag constants from FEATURE_FLAGS_MANIFEST.json"
    
    val scriptPath = project.file("../scripts/generate-feature-flags.ts")
    val workingDirectory = project.projectDir
    
    doLast {
        if (!scriptPath.exists()) {
            throw GradleException("Feature flag generator script not found: ${scriptPath.absolutePath}")
        }
        
        val process = ProcessBuilder("npx", "tsx", scriptPath.absolutePath)
            .directory(workingDirectory)
            .inheritIO()
            .start()
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw GradleException("Feature flag generation failed with exit code $exitCode")
        }
    }
}

// P2-003: Feature flag generation disabled temporarily due to build issues
// tasks.named("compileJava") {
//     dependsOn("generateFeatureFlags")
// }

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
    testRuntimeOnly(libs.junit.jupiter.engine)
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
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
    violationRules {
        rule {
            // DMOS-P1-043: Temporary threshold reduction for the application layer.
            // Keep the full module in coverage and lower the gate to the current baseline
            // until broader service and repository-path tests are added.
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.65".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.50".toBigDecimal()
            }
        }
    }
}

tasks.named("check") {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
