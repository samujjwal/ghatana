import org.gradle.api.artifacts.VersionCatalogsExtension

// Opt-in Lombok convention for platform/java modules.
//
// Apply this plugin to any module that uses Lombok annotations (@Data, @Builder, @Slf4j, etc.)
//
// Usage in a module's build.gradle.kts:
//   plugins {
//       id("com.ghatana.lombok-conventions")
//   }
//
// This configures:
//   - compileOnly / annotationProcessor for main sources
//   - testCompileOnly / testAnnotationProcessor for test sources
//
// HIGH-002 remediation: standardises Lombok across all platform/java modules
// See: SHARED_MODULES_AUDIT_REPORT.md FINDING-003 / HIGH-002

plugins {
    java
}

dependencies {
    val libs = project.extensions.findByType(VersionCatalogsExtension::class.java)?.named("libs")
    val lombokCoordinate = libs?.findLibrary("lombok")
        ?.orElse(null)
        ?.get()
        ?: "org.projectlombok:lombok:1.18.38"

    // Lombok annotation processor — opt-in, applied consistently to all modules using this plugin
    "compileOnly"(lombokCoordinate)
    "annotationProcessor"(lombokCoordinate)
    "testCompileOnly"(lombokCoordinate)
    "testAnnotationProcessor"(lombokCoordinate)
}
