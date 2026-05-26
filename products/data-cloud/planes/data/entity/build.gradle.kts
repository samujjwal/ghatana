plugins {
    id("java-module")
}

group = "com.ghatana.datacloud"
version = rootProject.version

description = "Data Cloud Platform Entity Module"


dependencies {
    // spi defines the contract interfaces (EntityInterface, DataRecordInterface, etc.)
    // that platform-entity's concrete classes implement
    api(project(":products:data-cloud:planes:shared-spi"))
    api(project(":platform:java:core"))
    api(project(":platform:java:domain"))
    api(project(":platform:java:database"))
    api(project(":platform:java:observability"))

    api(libs.activej.promise)
    api(libs.bundles.activej.core)
    api(platform("com.fasterxml.jackson:jackson-bom:2.18.2"))
    api(libs.jackson.annotations)
    api(libs.jackson.databind)
    api(libs.jackson.datatype.jsr310)
    api(libs.jakarta.persistence.api)
    api("jakarta.validation:jakarta.validation-api:3.0.2")

    implementation(libs.hibernate.core)
    implementation("org.hibernate.validator:hibernate-validator:8.0.1.Final")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")

    testImplementation(libs.bundles.testing.core)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(project(":platform:java:testing"))
    testRuntimeOnly(libs.junit.jupiter.engine)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)
    compileOnly("com.github.spotbugs:spotbugs-annotations:4.8.6")
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
    classDirectories.setFrom(
        sourceSets.main.get().output.asFileTree.matching {
            exclude("**/generated/**", "**/*\$Builder.class")
        }
    )
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    violationRules {
        rule {
            limit {
                counter = "INSTRUCTION"
                value = "COVEREDRATIO"
                // Tier 0 critical: tenant isolation, CRUD, schema validation, query filtering,
                // audit hooks, and retention hooks must move toward the 0.70+ production bar.
                minimum = "0.40".toBigDecimal()
            }
        }
    }
    classDirectories.setFrom(
        sourceSets.main.get().output.asFileTree.matching {
            exclude("**/generated/**", "**/*\$Builder.class")
        }
    )
}

tasks.named("check") {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
