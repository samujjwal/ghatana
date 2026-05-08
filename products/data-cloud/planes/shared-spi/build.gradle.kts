plugins {
    id("java-module")
    id("java-test-fixtures")
}

group = "com.ghatana.datacloud"
version = rootProject.version

description = "Data-Cloud SPI - Shared interfaces and types for cross-product integration"


dependencies {
    // SPI now owns the base record contracts and concrete base classes (DataRecord, Collection,
    // RecordQuery, RecordType, DataRecordInterface, EntityInterface, FilterCriteria, SortSpec,
    // QuerySpecInterface, DataCloudColumnNames).
    // platform-entity depends on spi for these types and extends/implements them.
    api(project(":platform-kernel:kernel-plugin"))
    api(project(":platform:java:core"))        // Offset type
    api(project(":platform:java:domain"))      // Platform event-store contracts for migration bridge
    api(libs.activej.promise)                  // Promise<T> in EventLogStore

    // JPA + Hibernate: needed for @MappedSuperclass DataRecord and @Entity Collection
    api(libs.jakarta.persistence.api)
    api("jakarta.validation:jakarta.validation-api:3.0.2")
    implementation(libs.hibernate.core)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    compileOnly(libs.spotbugs.annotations)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.assertj.core)
    testImplementation(project(":platform:java:testing"))
    testRuntimeOnly(libs.junit.jupiter.engine)

    testFixturesApi(libs.junit.jupiter)
    testFixturesApi(libs.assertj.core)
    testFixturesApi(project(":platform:java:testing"))
    testFixturesRuntimeOnly(libs.junit.jupiter.engine)
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
    dependsOn(tasks.jacocoTestReport)
    violationRules {
            rule {
                limit {
                    counter = "INSTRUCTION"
                    value = "COVEREDRATIO"
                    minimum = "0.50".toBigDecimal()
            }
        }
    }
}

tasks.named("check") {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
