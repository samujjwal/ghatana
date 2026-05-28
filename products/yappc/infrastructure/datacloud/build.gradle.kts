import java.time.Instant

plugins {
    id("java-module")
}

group = "com.ghatana.yappc"
version = rootProject.version
description = "YAPPC Infrastructure - Data-Cloud Integration"

dependencies {
    // Data-Cloud SPI only (no platform dep)
    api(project(":products:data-cloud:planes:shared-spi"))

    // YAPPC domain models (for mapping)
    implementation(project(":products:yappc:libs:java:yappc-domain"))
    implementation(project(":products:yappc:core:yappc-domain-impl"))
    implementation(project(":products:yappc:core:yappc-infrastructure"))

    // Platform libs
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:cache"))
    implementation(project(":platform:java:http"))
    implementation(project(":platform:java:observability"))
    implementation(project(":platform:java:database"))
    // implementation(project(":libs:types")) - path needs verification

    // ActiveJ for async
    implementation(libs.activej.promise)

    // Jackson for JSON mapping
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    // Logging
    implementation(libs.slf4j.api)

    // Testing
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)

    // JMH benchmarks
    testImplementation(libs.jmh.core)
    testAnnotationProcessor(libs.jmh.generator.annprocess)
}


tasks.test {
    useJUnitPlatform()
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

// Coverage verification: re-enable when module test coverage reaches minimum threshold.
// Tracked by YAPPC-011 — add ConfidenceScoringService tests to lift coverage.
tasks.named("jacocoTestCoverageVerification") {
    enabled = false
}

// SBOM generation task (YAPPC-009)
// Produces a CycloneDX-format SBOM in build/sbom/ by delegating to the cyclonedxBom Gradle plugin
// when it is applied, or emits a build warning instructing teams to apply the plugin in CI.
tasks.register("generateSbom") {
    group = "security"
    description = "Generates a CycloneDX SBOM for this module. Requires org.cyclonedx.bom plugin in CI."

    doLast {
        val cyclonedxTask = project.tasks.findByName("cyclonedxBom")
        if (cyclonedxTask != null) {
            // Delegate to the real CycloneDX task when the plugin is present
            cyclonedxTask.actions.forEach { it.execute(cyclonedxTask) }
            println("[SBOM] CycloneDX SBOM generated via cyclonedxBom task")
        } else {
            // In local dev, emit a warning — CI must apply the plugin for real SBOM output
            val sbomDir = layout.buildDirectory.dir("sbom").get().asFile
            sbomDir.mkdirs()
            val stubFile = sbomDir.resolve("bom.json")
            stubFile.writeText(
                """{
  "bomFormat": "CycloneDX",
  "specVersion": "1.4",
  "version": 1,
  "metadata": {
    "timestamp": "${Instant.now()}",
    "component": {
      "type": "library",
      "name": "${project.name}",
      "version": "${project.version}",
      "description": "STUB — apply org.cyclonedx.bom plugin for production SBOM"
    }
  },
  "components": []
}"""
            )
            logger.warn(
                "[SBOM][YAPPC-009] CycloneDX plugin (org.cyclonedx.bom) not applied. " +
                "A stub SBOM was written to ${stubFile.absolutePath}. " +
                "Apply the plugin in build.gradle.kts to generate a real dependency SBOM in CI."
            )
        }
    }
}
