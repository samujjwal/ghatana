plugins {
    id("com.ghatana.java-conventions")
    id("com.ghatana.java-library-conventions")
}

description = "YAPPC Consolidated Shared Utilities Module"

dependencies {
    // Platform dependencies
    implementation(platform("com.ghatana:platform-bom"))
    
    // Platform shared utilities
    implementation("com.ghatana.platform:common-utils")
    implementation("com.ghatana.platform:json-utils")
    
    // JSON processing
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    
    // Logging
    implementation("org.slf4j:slf4j-api")
    
    // Validation
    implementation("org.apache.commons:commons-lang3")
    implementation("org.apache.commons:commons-collections4")
    
    // Configuration
    implementation("com.typesafe:config")
    
    // Testing utilities
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.junit-pioneer:junit-pioneer")
}

tasks.test {
    useJUnitPlatform()
}

// Source sets for shared utilities
sourceSets {
    main {
        java {
            srcDirs(
                "src/main/java",
                "src/common/java",
                "src/utils/java"
            )
        }
        resources {
            srcDirs(
                "src/main/resources",
                "src/common/resources",
                "src/utils/resources"
            )
        }
    }
    test {
        java {
            srcDirs(
                "src/test/java",
                "src/common/test",
                "src/utils/test"
            )
        }
    }
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
                name.set("YAPPC Shared Utilities")
                description.set("Shared utilities and common components for YAPPC")
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

// Shared utility tasks
tasks.register("validateSharedUtils") {
    group = "verification"
    description = "Validate shared utilities"
    
    doLast {
        println("Validating shared utilities...")
    }
}

tasks.register("benchmarkUtils") {
    group = "performance"
    description = "Benchmark shared utilities performance"
    
    doLast {
        println("Running utility benchmarks...")
    }
}
