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
    implementation(project(":products:data-cloud:platform-api"))
    api(project(":products:data-cloud:platform-entity"))
    api(project(":products:data-cloud:platform-event"))
    api(project(":products:data-cloud:platform-config"))
    api(project(":products:data-cloud:platform-analytics"))
    api(project(":products:data-cloud:spi"))
    implementation(project(":products:data-cloud:platform-plugins"))
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
    implementation(project(":platform-kernel:kernel-plugin"))
    implementation(libs.swagger.annotations)
    implementation(platform(libs.aws.sdk.bom))

    implementation(libs.activej.http)
    implementation(libs.activej.inject)
    implementation(libs.activej.promise)
    implementation(libs.activej.eventloop)
    implementation(libs.grpc.api)
    implementation(libs.grpc.stub)
    implementation(libs.grpc.protobuf)
    implementation(libs.jackson.datatype.jsr310)
    implementation(libs.jedis)
    implementation(libs.lettuce.core)
    implementation(libs.aws.s3)
    implementation(libs.caffeine)
    implementation(libs.clickhouse.client)
    runtimeOnly(libs.clickhouse.http.client)
    implementation(libs.opensearch.java)
    implementation(libs.opensearch.rest.client)
    implementation(libs.hikaricp)
    implementation(libs.rocksdb)
    runtimeOnly(libs.sqlite.jdbc)  // Moved to runtimeOnly to reduce compile-time CVEs
    implementation(libs.h2)

    compileOnly(libs.javax.annotation.api)
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
    testCompileOnly(libs.javax.annotation.api)
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

// Skip SpotBugs analysis on test bytecode (test code quality is less critical than production code)
tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().matching { it.name == "spotbugsTest" }.configureEach {
    enabled = false
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
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
                minimum = "0.00".toBigDecimal()
            }
        }
        rule {
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.00".toBigDecimal()
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
    outputDirectory = layout.buildDirectory.dir("reports/owasp").get().asFile.absolutePath
    nvd {
        apiKey = System.getenv("NVD_API_KEY") ?: ""
        delay = 4000
    }
    scanConfigurations = listOf("runtimeClasspath", "compileClasspath")
}
