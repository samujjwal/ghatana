plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

// Configuration cache compatibility for kotlin-dsl plugin
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    notCompatibleWithConfigurationCache("kotlin-dsl plugin uses task completion listeners not supported by configuration cache")
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

    testImplementation(kotlin("test"))
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    incremental = false
    outputs.cacheIf { false }
}

gradlePlugin {
    plugins {
        create("javaModule") {
            id = "java-module"
            implementationClass = "com.ghatana.buildlogic.JavaModuleConventionPlugin"
        }
        create("javaApplication") {
            id = "java-application"
            implementationClass = "com.ghatana.buildlogic.JavaApplicationConventionPlugin"
        }
        create("protobufModule") {
            id = "protobuf-module"
            implementationClass = "com.ghatana.buildlogic.ProtobufModuleConventionPlugin"
        }
        create("financeDomainModule") {
            id = "finance-domain-module"
            implementationClass = "com.ghatana.buildlogic.FinanceDomainModuleConventionPlugin"
        }
        create("integrationTestProfile") {
            id = "integration-test-profile"
            implementationClass = "com.ghatana.buildlogic.IntegrationTestProfileConventionPlugin"
        }
        create("productPackValidation") {
            id = "product-pack-validation"
            implementationClass = "com.ghatana.buildlogic.ProductPackValidationConventionPlugin"
        }
    }
}
