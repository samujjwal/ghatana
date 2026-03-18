plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

dependencies {
    // Spotless plugin on classpath so SpotlessConventionsPlugin can configure it type-safely
    implementation("com.diffplug.spotless:spotless-plugin-gradle:8.0.0")
    // Saxon-HE for build-time XSLT tasks
    implementation("net.sf.saxon:Saxon-HE:12.4")
}

// Precompiled script plugins in src/main/kotlin/*.gradle.kts are auto-discovered.
// No gradlePlugin { } block is needed.
