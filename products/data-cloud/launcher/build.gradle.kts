plugins {
    id("java-module")
    `maven-publish`
    alias(libs.plugins.spotbugs)
}

val runLauncher by tasks.registering(JavaExec::class) {
    group = "application"
    description = "Runs the Data Cloud standalone launcher"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.ghatana.datacloud.launcher.DataCloudLauncher")
    standardInput = System.`in`
}

dependencies {
    // Platform modules
    implementation(project(":products:data-cloud:platform-launcher"))
    implementation(project(":products:data-cloud:platform-api"))
    implementation(project(":products:data-cloud:platform-plugins"))

    // Core platform dependencies
    implementation(project(":platform:java:observability"))
    implementation(project(":platform:java:config"))
    implementation(project(":platform:java:http"))
    implementation(project(":platform:java:governance"))
    implementation(project(":platform:java:audit"))
    implementation(project(":platform:java:security"))

    // AI platform integration — model registry, feature store, observability (all merged into ai-integration)
    implementation(project(":platform:java:ai-integration"))

    // HikariCP for AI service DataSource creation in standalone launcher
    implementation(libs.hikaricp)

    // gRPC transport (runtime) — needed to start the gRPC server
    implementation(libs.grpc.netty.shaded)

    // ActiveJ framework
    implementation(libs.activej.launcher)
    implementation(libs.activej.http)
    implementation(libs.activej.inject)
    implementation(libs.activej.config)
    implementation(libs.bundles.activej.core)
    implementation(libs.activej.promise)
    implementation(libs.activej.csp)
    implementation(libs.activej.bytebuf)

    // Jackson for JSON
    implementation(libs.jackson.core)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    // Logging
    implementation(libs.slf4j.api)
    implementation(libs.log4j.slf4j.impl)
    implementation(libs.log4j.core)

    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.assertj.core)
    testImplementation(project(":platform:java:testing"))
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")

    // Testcontainers for integration testing
    testImplementation("org.testcontainers:testcontainers:1.19.7")
    testImplementation("org.testcontainers:postgresql:1.19.7")
    testImplementation("org.testcontainers:kafka:1.19.7")
    testImplementation("org.testcontainers:localstack:1.19.7")
    testImplementation("org.testcontainers:junit-jupiter:1.19.7")
}

tasks.test {
    // useJUnitPlatform() already applied by java-module; increase parallelism for faster test execution
    maxParallelForks = 4
    finalizedBy(tasks.jacocoTestReport)
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

spotbugs {
    toolVersion = "4.8.6"
    ignoreFailures = false
    effort = com.github.spotbugs.snom.Effort.MAX
    reportLevel = com.github.spotbugs.snom.Confidence.MEDIUM
    excludeFilter = file("config/spotbugs-exclude.xml")
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    reports.create("html") { required = true }
    reports.create("xml") { required = true }
}

