plugins {
    id("java-library")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

dependencies {
    implementation project(':yappc-scaffold:core')

    implementation libs.picocli
    implementation libs.jackson.databind

    testImplementation libs.junit.jupiter.api
    testRuntimeOnly libs.junit.jupiter.engine
}
