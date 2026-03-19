plugins {
    id("application")
    id("java")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

dependencies {
    // adapters and schemas are now bundled in scaffold:core
    implementation(project(":products:yappc:core:scaffold:core"))
    implementation(project(":products:yappc:core:scaffold:packs"))

    implementation(libs.picocli)
    implementation(libs.jackson.databind)
    implementation(libs.slf4j.api)
    implementation(libs.diffutils)

    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    // mainClass was declared in the parent build but prefer explicit here
    mainClass.set("com.ghatana.yappc.cli.YappcEntryPoint")
}

// Prevent duplicate jar entries in distributions
tasks.withType<Tar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
tasks.withType<Zip> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
