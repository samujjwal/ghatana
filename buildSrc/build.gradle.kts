plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

dependencies {
    // Spotless plugin on classpath so SpotlessConventionsPlugin can configure it type-safely
    // NOTE: buildSrc has isolated classloader and cannot access libs.versions.toml
    // Version must be manually synchronized with gradle/libs.versions.toml spotless = "8.4.0"
    implementation("com.diffplug.spotless:spotless-plugin-gradle:8.4.0")
    // Saxon-HE for build-time XSLT tasks
    implementation("net.sf.saxon:Saxon-HE:12.4") {
        exclude(group = "org.apache.httpcomponents.client5", module = "httpclient5")
        exclude(group = "org.apache.httpcomponents.core5", module = "httpcore5")
        exclude(group = "org.apache.httpcomponents.core5", module = "httpcore5-h2")
    }
    implementation("org.apache.httpcomponents.client5:httpclient5:5.5.1")
    implementation("org.apache.httpcomponents.core5:httpcore5:5.3.6")
    implementation("org.apache.httpcomponents.core5:httpcore5-h2:5.3.6")
}

// Precompiled script plugins in src/main/kotlin/*.gradle.kts are auto-discovered.
// No gradlePlugin { } block is needed.
