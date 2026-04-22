plugins {
    id("java-module")
}

val integrationTest by sourceSets.creating {
    java.srcDir("src/integrationTest/java")
    resources.srcDir("src/integrationTest/resources")
    compileClasspath += sourceSets["main"].output + sourceSets["test"].output + configurations.testRuntimeClasspath.get()
    runtimeClasspath += output + compileClasspath
}

val kgScaleValidationRuntime by configurations.creating

configurations[integrationTest.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[integrationTest.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())
dependencies {
    kgScaleValidationRuntime("org.junit.platform:junit-platform-console-standalone:1.10.2")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // Platform plugin dependency
    implementation(project(":platform-kernel:kernel-plugin"))
    // TODO(ADAPTER-SEAM): knowledge-graph access should be hidden behind a DataStorePort.
    //   Move the actual DataCloud dependency to infrastructure:datacloud; inject via interface.
    implementation(project(":products:data-cloud:platform-launcher"))
    implementation(project(":products:data-cloud:platform-plugins"))
    implementation(project(":products:yappc:core:yappc-services"))
    implementation(project(":products:yappc:libs:java:yappc-domain"))
    implementation(project(":products:yappc:core:ai"))
    implementation(project(":platform:java:http"))
    implementation(project(":platform:java:observability"))
    // implementation(project(":libs:common-utils")) - path needs verification
    // implementation(project(":libs:validation")) - path needs verification
    implementation(libs.activej.promise)
    implementation(libs.activej.eventloop)
    implementation(libs.activej.inject)
    implementation(libs.activej.http)
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)
    implementation("org.commonmark:commonmark:0.21.0")
    implementation("org.apache.pdfbox:pdfbox:3.0.1")
    implementation(libs.slf4j.api)
    implementation(libs.jgrapht.core)
    implementation(libs.caffeine)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.postgresql)
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
}

description = "YAPPC Knowledge Graph - Consolidated integration module"

// Explicit task dependency to fix Gradle implicit dependency issue
tasks.compileJava {
    dependsOn(":products:yappc:core:ai:compileJava")
}

tasks.register<Test>("integrationTest") {
    description = "Runs knowledge-graph integration tests."
    group = "verification"
    testClassesDirs = integrationTest.output.classesDirs
    classpath = integrationTest.runtimeClasspath
    shouldRunAfter(tasks.test)
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

tasks.register<JavaExec>("kgScaleValidation") {
    description = "Runs the knowledge-graph production-scale validation suite via JUnit ConsoleLauncher."
    group = "verification"
    dependsOn("integrationTestClasses")
    classpath = integrationTest.runtimeClasspath + kgScaleValidationRuntime
    mainClass.set("org.junit.platform.console.ConsoleLauncher")
    args(
        "--select-class",
        "com.ghatana.yappc.knowledge.persistence.KGScaleValidationTest",
        "--details",
        "tree"
    )
}
