plugins {
    `kotlin-dsl`
}

group = "com.ghatana.build"
version = "1.0.0"

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("com.diffplug.spotless:spotless-plugin-gradle:8.4.0")
    implementation("com.google.protobuf:protobuf-gradle-plugin:0.9.4")
}
