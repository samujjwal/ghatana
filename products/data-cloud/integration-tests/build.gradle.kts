plugins {
    id("java-module")
}

group = "com.ghatana.datacloud"
version = rootProject.version

description = "Data Cloud cross-module integration test suite"

dependencies {
    testImplementation(project(":products:data-cloud:launcher"))
    testImplementation(project(":products:data-cloud:platform-launcher"))
    testImplementation(project(":products:data-cloud:platform-entity"))
    testImplementation(project(":products:data-cloud:spi"))
    testImplementation(project(":platform:java:domain"))
    testImplementation(project(":platform:java:testing"))
    testImplementation(project(":platform:java:security"))

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)

    // Testcontainers for real provider integration tests
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.kafka)
    testImplementation(libs.testcontainers.redis)
    testImplementation(libs.testcontainers.clickhouse)

    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform {
        excludeTags("performance", "integration")
    }
}

/**
 * DC-A12: Smoke-validate critical product scripts (syntax + executable + warn-only runs).
 * Run with: ./gradlew :products:data-cloud:integration-tests:smokeValidateScripts
 */
val smokeValidateScripts by tasks.registering(Exec::class) {
    group = "verification"
    description = "Smoke-validates critical Data-Cloud shell scripts (DC-A12)"

    val scriptFile = project.file("../scripts/smoke-validate-scripts.sh")
    inputs.file(scriptFile)
    inputs.dir(project.file("../scripts"))

    // On Windows CI (where bash is available via Git), use: bash <script>
    // On Linux/macOS CI the script is directly executable.
    if (System.getProperty("os.name").lowercase().contains("windows")) {
        commandLine("bash", scriptFile.absolutePath)
    } else {
        commandLine(scriptFile.absolutePath)
    }

    isIgnoreExitValue = false
}

/**
 * DC-A13: Lint the Data Cloud Helm chart.
 * Run with: ./gradlew :products:data-cloud:integration-tests:helmLint
 */
val helmLint by tasks.registering(Exec::class) {
    group = "verification"
    description = "Lints the Data Cloud Helm chart with default values (DC-A13)"

    val chartDir = project.file("../helm/data-cloud")
    val valuesFile = project.file("../helm/data-cloud/values.yaml")
    inputs.dir(chartDir)

    commandLine(
        "helm", "lint", chartDir.absolutePath,
        "--values", valuesFile.absolutePath,
        "--strict"
    )
    isIgnoreExitValue = false
}

/**
 * DC-A14: Validate the Data Cloud Terraform configuration (syntax + provider schemas).
 * Runs `terraform init -backend=false` then `terraform validate`.
 * Run with: ./gradlew :products:data-cloud:integration-tests:terraformValidate
 */
val terraformValidate by tasks.registering(Exec::class) {
    group = "verification"
    description = "Validates the Data Cloud Terraform configuration (DC-A14)"

    val tfDir = project.file("../terraform")
    inputs.dir(tfDir)

    // terraform validate requires an initialized workspace; init with -backend=false
    // skips remote state configuration so it can run in CI without credentials.
    commandLine(
        if (System.getProperty("os.name").lowercase().contains("windows")) "cmd" else "sh",
        if (System.getProperty("os.name").lowercase().contains("windows")) "/c" else "-c",
        "cd \"${tfDir.absolutePath}\" && terraform init -backend=false -input=false && terraform validate"
    )
    isIgnoreExitValue = false
}

tasks.named("jacocoTestReport") {
    enabled = false
}

tasks.named("jacocoTestCoverageVerification") {
    enabled = false
}