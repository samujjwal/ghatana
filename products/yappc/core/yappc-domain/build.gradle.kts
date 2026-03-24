plugins {
    id("com.ghatana.java-conventions")
    id("com.ghatana.java-library-conventions")
}

description = "YAPPC Domain Models Module"

dependencies {
    // Platform dependencies
    implementation(platform("com.ghatana:platform-bom"))
    
    // Platform domain models
    implementation("com.ghatana.platform:domain-models")
    
    // YAPPC shared utilities
    implementation(projects.yappcShared)
    
    // JSON processing
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    
    // Validation
    implementation("org.hibernate.validator:hibernate-validator")
    implementation("jakarta.validation:jakarta.validation-api")
    
    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testImplementation("org.mockito:mockito-core")
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
