plugins {
    id("java-library")
    id("java-test-fixtures")
    id("jacoco")
    alias(libs.plugins.jmh)
    alias(libs.plugins.spotbugs)
    alias(libs.plugins.owasp)
}

group = "com.ghatana.datacloud"
version = rootProject.version

description = "Data Cloud Platform Launcher Module"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    api(project(":products:data-cloud:platform-entity"))
    api(project(":products:data-cloud:platform-event"))
    api(project(":products:data-cloud:platform-config"))
    api(project(":products:data-cloud:platform-analytics"))
    api(project(":products:data-cloud:spi"))
    api(project(":platform:java:audit"))

    implementation(project(":platform:contracts"))
    implementation(project(":platform:java:ai-integration"))

    api(platform(libs.jackson.bom))
    api(libs.jackson.annotations)
    api(libs.jackson.databind)
    api(libs.jakarta.validation.api)
    api(libs.micrometer.core)

    implementation(project(":platform:java:http"))
    implementation(project(":platform:java:observability"))
    implementation(project(":platform:java:security"))
    implementation(project(":platform:java:config"))
    implementation(project(":platform:java:plugin"))
    implementation("io.swagger.core.v3:swagger-annotations:2.2.19")
    implementation(platform(libs.aws.sdk.bom))
    implementation(libs.aws.glacier)

    implementation(libs.activej.http)
    implementation(libs.activej.inject)
    implementation(libs.activej.promise)
    implementation(libs.activej.eventloop)
    implementation(libs.grpc.api)
    implementation(libs.grpc.stub)
    implementation(libs.grpc.protobuf)
    implementation(libs.jackson.datatype.jsr310)
    implementation(libs.kafka.clients)
    implementation(libs.jedis)
    implementation(libs.lettuce.core)
    implementation(libs.aws.s3)
    implementation(libs.caffeine)
    implementation(libs.clickhouse.client)
    implementation(libs.disruptor)
    implementation(libs.hadoop.common)
    implementation(libs.iceberg.core)
    implementation(libs.iceberg.data)
    implementation(libs.iceberg.parquet)
    implementation(libs.jgrapht.core)
    runtimeOnly(libs.clickhouse.http.client)
    implementation(libs.opensearch.java)
    implementation(libs.opensearch.rest.client)
    implementation(libs.parquet.avro)
    implementation(libs.hikaricp)
    implementation(libs.rocksdb)
    runtimeOnly(libs.sqlite.jdbc)  // Moved to runtimeOnly to reduce compile-time CVEs
    implementation(libs.h2)

    compileOnly("javax.annotation:javax.annotation-api:1.3.2")
    compileOnly(libs.trino.plugin.toolkit)
    compileOnly(libs.trino.spi)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(project(":platform:java:testing"))
    testImplementation(project(":platform:contracts"))
    testImplementation(project(":platform:java:ai-integration"))
    testImplementation(libs.grpc.stub)
    testImplementation(libs.grpc.protobuf)
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.clickhouse)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.kafka)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.hikaricp)
    testImplementation(libs.grpc.testing)
    testImplementation(libs.grpc.inprocess)
    testCompileOnly("javax.annotation:javax.annotation-api:1.3.2")
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)

    testFixturesImplementation(libs.junit.jupiter.api)
    testFixturesImplementation(libs.assertj.core)
    testFixturesImplementation(project(":platform:java:testing"))

    compileOnly(libs.spotbugs.annotations)
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)

    environment("DOCKER_HOST", "unix:///var/run/docker.sock")
    environment("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE", "/var/run/docker.sock")
    environment("TESTCONTAINERS_HOST_OVERRIDE", "host.docker.internal")
}

jacoco {
    toolVersion = "0.8.11"
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
            exclude(
                "**/proto/**",
                "**/*Proto.class",
                "**/*\$Builder.class",
                "**/generated/**"
            )
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
                // Roadmap: 0.11 (restored) → 0.20 (Q2) → 0.40 (Q3) → 0.60 (Q4)
                // Restored to 0.11 from regressed 0.10 — FINDING-DC-H1 remediation.
                minimum = "0.110".toBigDecimal()
            }
        }
        rule {
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                // Roadmap: 0.08 (restored) → 0.15 (Q2) → 0.30 (Q3) → 0.50 (Q4)
                // Restored to 0.08 from regressed 0.05 — FINDING-DC-H1 remediation.
                minimum = "0.080".toBigDecimal()
            }
        }
    }
    classDirectories.setFrom(
        sourceSets.main.get().output.asFileTree.matching {
            exclude(
                "**/proto/**",
                "**/*Proto.class",
                "**/generated/**",
                "**/spi/provider/**",
                // Exclude launcher bootstrapping (DI glue code, not testable in unit tests)
                "**/di/**",
                "**/launcher/**"
            )
        }
    )
}

tasks.named("check") {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

tasks.javadoc {
    dependsOn(tasks.compileJava)
    classpath += sourceSets.main.get().compileClasspath
    (options as StandardJavadocDocletOptions).apply {
        links("https://docs.oracle.com/en/java/javase/21/docs/api/")
        isFailOnError = false  // Continue on javadoc errors (Lombok symbol issues)
    }
}

spotbugs {
    toolVersion.set("4.8.6")
    ignoreFailures.set(false)
    effort.set(com.github.spotbugs.snom.Effort.MAX)
    reportLevel.set(com.github.spotbugs.snom.Confidence.MEDIUM)
    excludeFilter.set(rootProject.file("config/spotbugs/spotbugs-exclude.xml"))
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    reports.create("html") { required.set(true) }
    reports.create("xml") { required.set(true) }
}

dependencyCheck {
    failBuildOnCVSS = 7.0f
    suppressionFile = rootProject.file("config/owasp-suppressions.xml").path
    formats = listOf("HTML", "SARIF", "JSON")
    outputDirectory = layout.buildDirectory.dir("reports/owasp").get().asFile
    nvd {
        apiKey = System.getenv("NVD_API_KEY") ?: ""
        delay = 4000
    }
    scanConfigurations = listOf("runtimeClasspath", "compileClasspath")
}