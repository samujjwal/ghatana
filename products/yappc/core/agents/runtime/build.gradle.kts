plugins {
    id("java-library")
    id("jacoco")
}

group = "com.ghatana.products.yappc"
version = rootProject.version

description = "YAPPC Agent Runtime — base types, contracts, dispatch, coordination, tools and prompt templates"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    // Framework core (api — FeatureFlags/FeatureFlag used in YAPPCAgentBase public API)
    api(project(":products:yappc:core:yappc-infrastructure"))

    // Domain types and repository ports
    // backend:persistence removed (2026-03-23) — functionality consolidated into core modules

    // Agent framework from platform (api — types are part of YAPPCAgentBase public API)
    api(project(":platform:java:agent-core"))
    // TODO(ADAPTER-SEAM): aep-agent-runtime, data-cloud:spi, and aep-operator-contracts
    //   bypass the adapter boundary. Future: introduce AgentRuntimePort, DataStorePort,
    //   and OperatorCatalogPort in core; move AEP/DC impls to infrastructure:aep
    implementation(project(":products:aep:aep-agent-runtime"))  // Migrated from agent-dispatch + agent-memory + agent-learning
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:workflow"))
    implementation(project(":platform:java:database"))
    implementation(project(":platform:java:ai-integration"))
    // TODO(ADAPTER-SEAM): data-cloud:spi should be accessed via DataStorePort once in place
    implementation(project(":products:data-cloud:spi"))

    // AEP contracts (decoupled from concrete runtime implementation)
    // TODO(ADAPTER-SEAM): aep-operator-contracts should be hidden behind OperatorCatalogPort
    implementation(project(":products:aep:aep-operator-contracts"))

    // AEP central runtime — only for tests (YappcAepIntegrationTest used real service pre-migration)
    testImplementation(project(":products:aep:aep-central-runtime"))

    // AI module (api — LLMProvider/LLMRequest/LLMResponse in public agent API)
    api(project(":products:yappc:core:ai"))

    // ActiveJ for async operations
    implementation(libs.activej.promise)
    implementation(libs.activej.eventloop)
    implementation(libs.activej.http)

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
    testImplementation(libs.archunit.junit5)
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
