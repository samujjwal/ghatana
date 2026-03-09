plugins {
    id("java-library")
}

group = "com.ghatana.platform"
version = "1.0.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

// Include sub-module source directories that have compilable sources
sourceSets {
    main {
        java {
            srcDirs(
                "src/main/java",
                "batch/src/main/java",
                "evaluation/src/main/java",
                "feature-store/src/main/java",
                "gateway/src/main/java",
                "observability/src/main/java",
                "promotion/src/main/java",
                "registry/src/main/java",
                "training/src/main/java"
            )
        }
    }
}

// Avoid duplicate entries in sourcesJar from overlapping srcDirs
tasks.withType<Jar>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

dependencies {
    api(project(":platform:java:core"))
    api(project(":platform:java:observability"))
    api(libs.activej.promise)
    api(libs.activej.http)
    api(libs.activej.bytebuf)
    implementation(libs.openai.client)
    implementation(libs.jackson.databind)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // Test
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}
