package com.ghatana.services.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @doc.type class
 * @doc.purpose Prevent shared services from directly constructing platform JWT implementations
 * @doc.layer product
 * @doc.pattern ValidationTest
 */
@DisplayName("Shared Services JWT Boundary Tests")
class SharedServicesJwtBoundaryTest {

    private static final String FORBIDDEN_CONCRETE_REFERENCE = "com.ghatana.platform.security.jwt.JwtTokenProvider";

    @Test
    @DisplayName("shared services should rely on JWT port factories instead of concrete provider construction")
    void sharedServicesShouldUseJwtPortFactories() throws IOException { // GH-90000
        Path repoRoot = findRepoRoot(); // GH-90000
        Map<String, String> violations = new LinkedHashMap<>(); // GH-90000

        for (String relativePath : new String[]{ // GH-90000
                "shared-services/auth-gateway/src/main/java/com/ghatana/services/auth/AuthService.java",
                "shared-services/auth-gateway/src/main/java/com/ghatana/services/auth/AuthGatewayLauncher.java",
                "shared-services/user-profile-service/src/main/java/com/ghatana/services/userprofile/UserProfileService.java",
                "shared-services/ai-inference-service/src/main/java/com/ghatana/services/aiinference/AIInferenceServiceLauncher.java"
        }) {
            Path file = repoRoot.resolve(relativePath); // GH-90000
            if (!Files.exists(file)) { // GH-90000
                continue; // Skip files that don't exist yet
            }
            String source = Files.readString(file, StandardCharsets.UTF_8); // GH-90000
            if (source.contains(FORBIDDEN_CONCRETE_REFERENCE)) { // GH-90000
                violations.put(relativePath, FORBIDDEN_CONCRETE_REFERENCE); // GH-90000
            }
        }

        assertThat(violations) // GH-90000
                .as("shared-service source should not reference the concrete JWT provider class directly")
                .isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("non-local deployments should reject missing platform JWT secrets")
    void nonLocalDeploymentsShouldRejectMissingPlatformJwtSecret() { // GH-90000
        assertThatThrownBy(() -> AuthGatewayLauncher.resolvePlatformJwtSecret("production", null)) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("PLATFORM_JWT_SECRET");
    }

    @Test
    @DisplayName("non-local deployments should reject the development fallback platform JWT secret")
    void nonLocalDeploymentsShouldRejectDefaultPlatformJwtSecret() { // GH-90000
        assertThatThrownBy(() -> AuthGatewayLauncher.resolvePlatformJwtSecret( // GH-90000
                "staging",
                "dev-platform-jwt-secret-change-me-in-prod!"
        ))
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("minimum 32 characters and not the development fallback");
    }

    @Test
    @DisplayName("local development may still use the fallback platform JWT secret")
    void localDevelopmentMayUseFallbackPlatformJwtSecret() { // GH-90000
        assertThat(AuthGatewayLauncher.resolvePlatformJwtSecret("development", null)) // GH-90000
                .isEqualTo("dev-platform-jwt-secret-change-me-in-prod!");
    }

    private static Path findRepoRoot() { // GH-90000
        Path current = Path.of("").toAbsolutePath();
        while (current != null) { // GH-90000
            if (Files.exists(current.resolve("settings.gradle.kts"))) {
                return current;
            }
            current = current.getParent(); // GH-90000
        }
        throw new IllegalStateException("Could not locate repository root");
    }
}
