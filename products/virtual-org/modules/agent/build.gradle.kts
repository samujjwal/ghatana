plugins {
    id("java-library")
}

sourceSets {
    main {
        java {
            // Include pre-generated proto sources
            srcDir("src/generated/main/java")
        }
    }
}

dependencies {
    // Platform modules
    implementation(project(":platform:java:workflow"))
    implementation(project(":platform:java:agent-framework"))

    // Protobuf (for generated sources)
    implementation(libs.protobuf.java)

    // ActiveJ
    implementation(libs.activej.promise)

    // Logging
    implementation(libs.slf4j.api)

    // JetBrains annotations
    compileOnly(libs.jetbrains.annotations)
}
