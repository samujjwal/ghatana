import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    "api"(project(":products:finance:platform-sdk"))
}
