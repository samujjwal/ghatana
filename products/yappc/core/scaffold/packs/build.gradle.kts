plugins {
    id("java-library")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

description = "YAPPC Scaffold Packs - Extension packs & adapters (merged: packs + adapters)"

dependencies {
    implementation(project(":products:yappc:core:scaffold:core"))
    // Platform deps (from adapters)
    implementation(project(":platform:java:http"))
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:observability"))

    implementation(libs.picocli)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.diffutils)
    implementation(libs.slf4j.api)
    implementation(libs.jgit)
    implementation(libs.joda.time)
    implementation(libs.commons.text)

    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

