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
    // Spotless code-formatter plugin
    implementation("com.diffplug.spotless:spotless-plugin-gradle:8.4.0")
    
    // Protobuf plugin for convention plugins
    implementation("com.google.protobuf:protobuf-gradle-plugin:0.9.4")
    
    // Saxon-HE for build-time document transforms
    implementation("net.sf.saxon:Saxon-HE:12.4") {
        exclude(group = "org.apache.httpcomponents.client5", module = "httpclient5")
        exclude(group = "org.apache.httpcomponents.core5", module = "httpcore5")
        exclude(group = "org.apache.httpcomponents.core5", module = "httpcore5-h2")
    }
    implementation("org.apache.httpcomponents.client5:httpclient5:5.5.1")
    implementation("org.apache.httpcomponents.core5:httpcore5:5.3.6")
    implementation("org.apache.httpcomponents.core5:httpcore5-h2:5.3.6")
}
