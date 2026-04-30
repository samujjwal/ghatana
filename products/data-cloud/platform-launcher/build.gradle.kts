plugins {
    id("java-module")
    id("java-test-fixtures")
    alias(libs.plugins.jmh)
    alias(libs.plugins.spotbugs)
    alias(libs.plugins.owasp)
}

group = "com.ghatana.datacloud"
version = rootProject.version

description = "Data Cloud Platform Launcher Module"


dependencies {
    implementation(project(":products:data-cloud:platform-api"))
    api(project(":products:data-cloud:platform-entity"))
    api(project(":products:data-cloud:platform-event"))
    api(project(":products:data-cloud:platform-config"))
    api(project(":products:data-cloud:platform-analytics"))
    api(project(":products:data-cloud:spi"))
    // WarmTierEventLogStore extracted to platform-event-store (DC-A10)
    api(project(":products:data-cloud:platform-event-store"))
    implementation(project(":products:data-cloud:platform-plugins"))
    api(project(":platform:java:audit"))

    implementation(project(":platform:contracts"))
    implementation(project(":platform:java:ai-integration"))

    implementation(project(":platform:java:http"))
    implementation(project(":platform:java:observability"))
    implementation(project(":platform:java:security"))
    implementation(project(":platform:java:config"))
    implementation(project(":platform-kernel:kernel-plugin"))
    implementation(platform("com.fasterxml.jackson:jackson-bom:2.17.2"))

    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation(libs.jedis)
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
    runtimeOnly("io.grpc:grpc-netty-shaded")
    implementation("io.grpc:grpc-protobuf")
    implementation("io.grpc:grpc-stub")
    implementation(libs.hikaricp)
    implementation("org.rocksdb:rocksdbjni:8.0.0")
    runtimeOnly("org.postgresql:postgresql")
    implementation(libs.h2)

    // AWS SDK for S3 storage connectors
    implementation(platform("software.amazon.awssdk:bom:2.29.46"))
    implementation("software.amazon.awssdk:s3")

    // ClickHouse client
    implementation("com.clickhouse:clickhouse-client:0.5.0")

    // OpenSearch client
    implementation("org.opensearch.client:opensearch-rest-client:2.18.0")
    implementation("org.opensearch.client:opensearch-java:2.18.0")

    // Apache HTTP client for OpenSearch
    implementation("org.apache.httpcomponents:httpclient:4.5.14")

    compileOnly("jakarta.annotation:jakarta.annotation-api:3.0.0")
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testImplementation(libs.junit.jupiter)
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
    testImplementation("org.testcontainers:clickhouse:1.21.4")
    testImplementation(libs.testcontainers.postgresql)
    testImplementation("org.testcontainers:kafka:1.21.4")
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.hikaricp)
    testImplementation("io.grpc:grpc-testing:1.79.0")
    testImplementation("io.grpc:grpc-inprocess:1.79.0")
    testImplementation("org.xerial:sqlite-jdbc:3.46.0.0")
    testCompileOnly("jakarta.annotation:jakarta.annotation-api:3.0.0")
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)

    testFixturesImplementation(libs.bundles.testing.core)
    testFixturesImplementation("org.assertj:assertj-core:3.26.3")
    testFixturesImplementation(project(":platform:java:testing"))

    compileOnly("com.github.spotbugs:spotbugs-annotations:4.8.6")
}

tasks.test {
    // useJUnitPlatform() already applied by java-module; preserve custom config
    useJUnitPlatform {
        excludeTags("performance")
    }
    finalizedBy(tasks.jacocoTestReport)

    environment("DOCKER_HOST", System.getenv("DOCKER_HOST") ?: "unix:///var/run/docker.sock")
    environment(
        "TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE",
        System.getenv("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE") ?: "/var/run/docker.sock"
    )
    environment(
        "TESTCONTAINERS_HOST_OVERRIDE",
        System.getenv("TESTCONTAINERS_HOST_OVERRIDE") ?: "host.docker.internal"
    )
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}
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
                minimum = "0.26".toBigDecimal()
            }
        }
        rule {
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.20".toBigDecimal()
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
    toolVersion.set("4.8.6")  // Valid SpotBugs tool version
    ignoreFailures.set(false)
    effort.set(com.github.spotbugs.snom.Effort.MAX)
    reportLevel.set(com.github.spotbugs.snom.Confidence.MEDIUM)
    excludeFilter.set(rootProject.file("config/spotbugs/spotbugs-exclude.xml"))
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    reports.create("html") { required.set(true) }
    reports.create("xml") { required.set(true) }
}

//dependencyCheck {
//    failBuildOnCVSS = 7.0f
//    suppressionFile = rootProject.file("config/owasp-suppressions.xml").path
//    formats = listOf("HTML", "SARIF", "JSON")
//    outputDirectory = layout.buildDirectory.dir("reports/owasp").get().asFile.absolutePath
//    nvd {
//        apiKey = System.getenv("NVD_API_KEY") ?: ""
//        delay = 4000
//    }
//    scanConfigurations = listOf("runtimeClasspath", "compileClasspath")
//}
