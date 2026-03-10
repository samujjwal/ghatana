plugins {
    id("java-library")
    id("java-test-fixtures")
    id("jacoco")
}

group = "com.ghatana.products.yappc"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Core API dependencies - exposed to consumers
    api(project(":platform:java:ai-integration"))
    api(project(":platform:java:observability"))
    api(project(":platform:java:agent-framework"))
    api(project(":products:yappc:core:agents"))
    // api(project(":libs:validation-api")) - path needs verification
    api(project(":products:data-cloud:platform"))
    // api(project(":libs:event-cloud")) - path needs verification
    // NOTE: contracts:yappc is temporarily disabled - see settings.gradle.kts
    
    // ActiveJ - core async framework (exposed through Promise-based API)
    api(libs.activej.promise)
    api(libs.activej.eventloop)
    api(libs.activej.inject)
    api(libs.activej.launcher)
    api(libs.activej.http)
    
    // Internal implementation dependencies - using platform paths
    // implementation(project(":libs:common-utils")) - path needs verification
    implementation(project(":platform:java:database"))
    implementation(project(":platform:java:security"))
    // implementation(project(":libs:audit")) - path needs verification
    implementation(project(":platform:java:governance"))
    // implementation(project(":libs:plugin-framework")) - path needs verification
    // implementation(project(":libs:operator")) - path needs verification
    
    // Protobuf
    api("com.google.protobuf:protobuf-java:3.25.1")
    
    // Validation
    implementation("jakarta.validation:jakarta.validation-api:3.0.2")
    
    // JSON Processing
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)
    implementation(libs.jackson.module.parameter.names)
    
    // Logging
    implementation(libs.slf4j.api)
    
    // Testing
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    
    testFixturesApi(libs.junit.jupiter)
    testFixturesApi(libs.assertj.core)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        html.required.set(true)
        xml.required.set(true)
        csv.required.set(false)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.55".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
