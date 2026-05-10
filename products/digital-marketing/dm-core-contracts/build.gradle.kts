plugins {
    id("java-module")
}

apply(from = "../gradle/dmos-quality-gates.gradle.kts")

group = "com.ghatana.digitalmarketing"
description = "DMOS Core Contracts — canonical IDs, context value objects, event schema version, and correlation standards"

dependencies {
    api(project(":platform-kernel:kernel-core"))
    api(project(":platform-kernel:kernel-plugin"))

    compileOnly(libs.spotbugs.annotations)

    // YAML parsing for route manifest generator
    implementation(libs.jackson.databind)
    implementation(libs.jackson.dataformat.yaml)

    testImplementation(libs.bundles.testing.core)
    testImplementation(project(":platform-kernel:kernel-testing"))
    testRuntimeOnly(libs.junit.jupiter.engine)
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude("**/DmosRouteManifestGenerator*.class")
            }
        })
    )
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

tasks.jacocoTestCoverageVerification {
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude("**/DmosRouteManifestGenerator*.class")
            }
        })
    )
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.95".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.75".toBigDecimal()
            }
        }
    }
}

tasks.named("check") {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

// Task to generate route/action/capability artifacts from canonical YAML manifest
// Note: This task should be run manually after changes to dmos-route-manifest.yaml
// It does not automatically depend on compileJava to avoid circular dependency
tasks.register<JavaExec>("generateRouteManifest") {
    group = "generation"
    description = "Generate route/action/capability artifacts from canonical YAML manifest"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.ghatana.digitalmarketing.contracts.generated.DmosRouteManifestGenerator")
    workingDir = projectDir
}
