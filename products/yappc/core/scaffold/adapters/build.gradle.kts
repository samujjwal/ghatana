plugins {
    id("java-library")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

dependencies {
    implementation(project(":products:yappc:core:scaffold:core"))
    implementation(project(":platform:java:http"))
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:observability"))

    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

