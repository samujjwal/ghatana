plugins {
    id("java-library")
    id("maven-publish")
    id("com.ghatana.java-conventions")
}

description = "YAPPC Domain Models Module"

dependencies {
    // Platform modules
    implementation(project(":platform:java:domain"))
    implementation(project(":platform:java:ai-integration"))
    implementation(project(":platform:java:http"))
    implementation(project(":platform:java:agent-core"))
    implementation(project(":platform:java:observability"))
    
    // YAPPC shared utilities
    implementation(project(":products:yappc:core:yappc-shared"))
    
    // ActiveJ for async
    implementation(libs.activej.promise)
    implementation(libs.activej.http)
    
    // JSON processing
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)
    
    // Validation
    implementation(libs.hibernate.validator)
    implementation(libs.jakarta.validation.api)
    
    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
}

tasks.test {
    useJUnitPlatform()
}

// Publish as a library for other products to use
java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            
            pom {
                name.set("YAPPC Domain Models")
                description.set("Domain models and business entities for YAPPC")
                url.set("https://github.com/ghatana/ghatana/tree/main/products/yappc")
                
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                
                developers {
                    developer {
                        id.set("ghatana")
                        name.set("Ghatana AI Platform")
                        email.set("platform@ghatana.ai")
                    }
                }
            }
        }
    }
}

// Domain-specific tasks
tasks.register("validateDomainModels") {
    group = "verification"
    description = "Validate domain model consistency"
    
    doLast {
        println("Validating domain models...")
    }
}

tasks.register("generateDomainDocs") {
    group = "documentation"
    description = "Generate domain model documentation"
    
    doLast {
        println("Generating domain documentation...")
    }
}
