plugins {
    java
    application
}

import org.gradle.jvm.toolchain.JavaLanguageVersion

description = "Software-Org Launcher - Main application entry point"

// Explicitly set Java toolchain to 21 for both IntelliJ and command line
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

application {
    mainClass.set("com.ghatana.softwareorg.launcher.SoftwareOrgLauncher")
}

// Set working directory to repo root for run task
tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}

dependencies {
    // Virtual-org framework — boot needs direct dep for VirtualAppBootstrap,
    // ConfigurableOrganization, and OrganizationConfigLoader wiring.
    // software-org IS-A virtual-org extension (structural coupling is intentional).
    implementation(project(":products:virtual-org:modules:framework"))
    
    // Domain models and agents
    implementation(project(":products:software-org:engine:modules:domain-model"))
    
    // HTTP server for REST API
    implementation(project(":platform:java:http"))
    
    // Observability and metrics
    implementation(project(":platform:java:observability"))
    
    // Configuration and validation (includes YAML support via Jackson)
    implementation(project(":platform:java:config"))
    
    // Common utilities (includes JsonUtils with Jackson)
    implementation(project(":platform:java:core"))
    
    // ActiveJ runtime (includes DI framework)
    implementation(project(":platform:java:runtime"))

    // Department implementations
    runtimeOnly(project(":products:software-org:libs:java:departments:engineering"))
    runtimeOnly(project(":products:software-org:libs:java:departments:qa"))
    runtimeOnly(project(":products:software-org:libs:java:departments:devops"))
    runtimeOnly(project(":products:software-org:libs:java:departments:support"))
    runtimeOnly(project(":products:software-org:libs:java:departments:sales"))
    runtimeOnly(project(":products:software-org:libs:java:departments:marketing"))
    runtimeOnly(project(":products:software-org:libs:java:departments:product"))
    runtimeOnly(project(":products:software-org:libs:java:departments:finance"))
    runtimeOnly(project(":products:software-org:libs:java:departments:hr"))
    runtimeOnly(project(":products:software-org:libs:java:departments:compliance"))

    // Integration plugins
    runtimeOnly(project(":products:software-org:engine:modules:integration"))
    
    // Testing
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
}

tasks.test {
    useJUnitPlatform()
}

// Handle duplicate files in distribution
tasks.withType<Tar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.withType<Zip> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// Create fat JAR for easy deployment
tasks.register<Jar>("fatJar") {
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    
    manifest {
        attributes["Main-Class"] = "com.ghatana.softwareorg.launcher.SoftwareOrgLauncher"
    }
    
    // Resolve runtime classpath lazily to avoid configuration-time dependency resolution.
    from({
        configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }
    })
    with(tasks.jar.get())
}
