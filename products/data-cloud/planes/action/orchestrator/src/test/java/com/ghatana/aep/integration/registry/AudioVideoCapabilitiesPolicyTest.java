/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.integration.registry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Policy contract tests for audio-video tool capability catalog.
 *
 * @doc.type class
 * @doc.purpose Enforce explicit role requirements for AV tool registrations
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@DisplayName("Audio-video capability policy contracts")
class AudioVideoCapabilitiesPolicyTest {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    private static final String CAPABILITIES_PATH =
            "products/data-cloud/planes/action/agent-catalog/capabilities/audio-video-capabilities.yaml";

    @Test
    @DisplayName("all AV tools declare explicit required roles")
    void allToolsDeclareRequiredRoles() throws Exception {
        Path file = resolveRepoRoot().resolve(CAPABILITIES_PATH);
        assertThat(file)
                .as("AV capabilities file must exist")
                .exists();

        JsonNode root = YAML.readTree(file.toFile());
        JsonNode tools = root.path("tools");

        assertThat(tools.isArray())
                .as("tools must be an array")
                .isTrue();
        assertThat(tools)
                .as("tools must not be empty")
                .isNotEmpty();

        for (JsonNode tool : tools) {
            String toolId = tool.path("toolId").asText("<unknown>");
            JsonNode requiredRoles = tool.path("accessPolicy").path("requiredRoles");

            assertThat(requiredRoles.isArray())
                    .as("requiredRoles must be an array for %s", toolId)
                    .isTrue();
            assertThat(requiredRoles)
                    .as("requiredRoles must be non-empty for %s", toolId)
                    .isNotEmpty();

            boolean highRisk = containsTag(tool.path("policyTags"), "pii-risk")
                    || containsTag(tool.path("policyTags"), "biometric-risk");
            if (highRisk) {
                assertThat(containsRole(requiredRoles, "OPERATOR") || containsRole(requiredRoles, "ADMIN"))
                        .as("high-risk tool %s must require at least OPERATOR or ADMIN", toolId)
                        .isTrue();
            }
        }
    }

            @Test
            @DisplayName("high-risk AV tools deny low-privilege roles")
            void highRiskToolsDenyLowPrivilegeRoles() throws Exception {
            Path file = resolveRepoRoot().resolve(CAPABILITIES_PATH);
            assertThat(file)
                .as("AV capabilities file must exist")
                .exists();

            JsonNode root = YAML.readTree(file.toFile());
            JsonNode tools = root.path("tools");

            assertThat(tools.isArray())
                .as("tools must be an array")
                .isTrue();

            for (JsonNode tool : tools) {
                String toolId = tool.path("toolId").asText("<unknown>");
                JsonNode requiredRoles = tool.path("accessPolicy").path("requiredRoles");
                boolean highRisk = containsTag(tool.path("policyTags"), "pii-risk")
                    || containsTag(tool.path("policyTags"), "biometric-risk");

                if (!highRisk) {
                continue;
                }

                assertThat(isRoleAllowed(requiredRoles, "VIEWER"))
                    .as("VIEWER must be denied for %s", toolId)
                    .isFalse();
                assertThat(isRoleAllowed(requiredRoles, "API_CLIENT"))
                    .as("API_CLIENT must be denied for %s", toolId)
                    .isFalse();
                assertThat(isRoleAllowed(requiredRoles, "OPERATOR") || isRoleAllowed(requiredRoles, "ADMIN"))
                    .as("high-risk tool %s must remain usable by governed operator/admin flows", toolId)
                    .isTrue();
            }
            }

    private static boolean containsTag(JsonNode tags, String expected) {
        if (!tags.isArray()) {
            return false;
        }
        for (JsonNode tag : tags) {
            if (expected.equals(tag.asText())) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsRole(JsonNode roles, String expectedRole) {
        for (JsonNode role : roles) {
            if (expectedRole.equals(role.asText())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRoleAllowed(JsonNode requiredRoles, String role) {
        return containsRole(requiredRoles, role);
    }

    private static Path resolveRepoRoot() {
        Path current = Paths.get("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("gradlew"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate repository root");
    }
}