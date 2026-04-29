plugins {
    id("java-module")
}

group = "com.ghatana.datacloud"
version = rootProject.version

description = "Data-Cloud agent catalog — validates agent definition YAML files against schema and business rules"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    // Jackson for YAML parsing
    implementation(libs.jackson.databind)
    implementation(libs.jackson.dataformat.yaml)
}
