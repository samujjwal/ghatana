/**
 * Platform Kernel Persistence Module
 *
 * @doc.type build-script
 * @doc.purpose Durable persistence adapters for kernel
 * @doc.layer platform
 */
plugins {
    `java-library`
}

group = "com.ghatana.kernel"
version = "1.0.0"
description = "Platform Kernel Persistence - durable storage adapters"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    // Kernel Core
    api(project(":kernel-core"))

    // PostgreSQL
    implementation("org.postgresql:postgresql:42.7.3")

    // Redis
    implementation("redis.clients:jedis:5.1.2")

    // ActiveJ
    api("io.activej:activej-promise:6.0-rc2")

    // Logging
    api("org.slf4j:slf4j-api:2.0.12")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.assertj:assertj-core:3.25.3")
}

tasks.test {
    useJUnitPlatform()
}
