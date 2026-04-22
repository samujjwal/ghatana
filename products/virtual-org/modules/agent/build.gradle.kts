plugins {
    id("java-module")
}

sourceSets {
    main {
        java {
            // Include pre-generated proto sources
            srcDir("src/generated/main/java")
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    // Generated protobuf code uses deprecated GeneratedMessageV3 APIs; suppress for generated sources
    options.compilerArgs.addAll(listOf("-Xlint:-deprecation", "-Xlint:-removal"))
}

dependencies {
    // Platform modules
    implementation(project(":platform:java:workflow"))
    implementation(project(":platform:java:agent-core"))

    // Protobuf (for generated sources)
    implementation(libs.protobuf.java)

    // ActiveJ
    implementation(libs.activej.promise)

    // Logging
    implementation(libs.slf4j.api)

    // JetBrains annotations
    compileOnly(libs.jetbrains.annotations)
}
