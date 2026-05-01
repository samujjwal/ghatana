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
    void sharedServicesShouldUseJwtPortFactories() throws IOException { 
        Path repoRoot = findRepoRoot(); 
        Map<String, String> violations = new LinkedHashMap<>(); 

        for (String relativePath : new String[]{ 
                "shared-services/auth-gateway/src/main/java/com/ghatana/services/auth/AuthService.java",
                "shared-services/auth-gateway/src/main/java/com/ghatana/services/auth/AuthGatewayLauncher.java",
                "shared-services/user-profile-service/src/main/java/com/ghatana/services/userprofile/UserProfileService.java",
                "shared-services/ai-inference-service/src/main/java/com/ghatana/services/aiinference/AIInferenceServiceLauncher.java"
        }) {
            Path file = repoRoot.resolve(relativePath); 
            if (!Files.exists(file)) { 
                continue; // Skip files that don't exist yet
            }
            String source = Files.readString(file, StandardCharsets.UTF_8); 
            if (source.contains(FORBIDDEN_CONCRETE_REFERENCE)) { 
                violations.put(relativePath, FORBIDDEN_CONCRETE_REFERENCE); 
            }
        }

        assertThat(violations) 
                .as("shared-service source should not reference the concrete JWT provider class directly")
                .isEmpty(); 
    }

    @Test
    @DisplayName("non-local deployments should reject missing platform JWT secrets")
    void nonLocalDeploymentsShouldRejectMissingPlatformJwtSecret() { 
        assertThatThrownBy(() -> AuthGatewayLauncher.resolvePlatformJwtSecret("production", null)) 
                .isInstanceOf(IllegalStateException.class) 
                .hasMessageContaining("PLATFORM_JWT_SECRET");
    }

    @Test
    @DisplayName("non-local deployments should reject the development fallback platform JWT secret")
    void nonLocalDeploymentsShouldRejectDefaultPlatformJwtSecret() { 
        assertThatThrownBy(() -> AuthGatewayLauncher.resolvePlatformJwtSecret( 
                "staging",
                "dev-platform-jwt-secret-change-me-in-prod!"
        ))
                .isInstanceOf(IllegalStateException.class) 
                .hasMessageContaining("minimum 32 characters and not the development fallback");
    }

    @Test
    @DisplayName("local development may still use the fallback platform JWT secret")
    void localDevelopmentMayUseFallbackPlatformJwtSecret() { 
        assertThat(AuthGatewayLauncher.resolvePlatformJwtSecret("development", null)) 
                .isEqualTo("dev-platform-jwt-secret-change-me-in-prod!");
    }

    private static Path findRepoRoot() { 
        Path current = Path.of("").toAbsolutePath();
        while (current != null) { 
            if (Files.exists(current.resolve("settings.gradle.kts"))) {
                return current;
            }
            current = current.getParent(); 
        }
        throw new IllegalStateException("Could not locate repository root");
    }
}
