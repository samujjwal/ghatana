package com.ghatana.stt.core.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * RBAC policy implementation for STT service authorization.
 *
 * <p>Supports role-based access control with method-level permissions.
 * Policies can be loaded from YAML configuration files.
 *
 * @doc.type class
 * @doc.purpose RBAC policy enforcement
 * @doc.layer security
 * @doc.pattern Strategy
 */
public final class RbacPolicyImpl implements RbacPolicy {

    private static final Logger LOG = LoggerFactory.getLogger(RbacPolicyImpl.class);

    private final Map<String, Set<String>> methodRoles;
    private final Map<Pattern, Set<String>> patternRoles;
    private final Set<String> adminRoles;
    private final boolean defaultAllow;

    private RbacPolicyImpl(Builder builder) {
        this.methodRoles = Map.copyOf(builder.methodRoles);
        this.patternRoles = Map.copyOf(builder.patternRoles);
        this.adminRoles = Set.copyOf(builder.adminRoles);
        this.defaultAllow = builder.defaultAllow;
        LOG.info("RBAC policy initialized: {} method rules, {} pattern rules, defaultAllow={}",
            methodRoles.size(), patternRoles.size(), defaultAllow);
    }

    @Override
    public boolean isAllowed(Set<String> userRoles, String method) {
        if (userRoles == null || userRoles.isEmpty()) {
            LOG.debug("No roles provided for method: {}", method);
            return defaultAllow;
        }

        // Admin roles have full access
        for (String role : userRoles) {
            if (adminRoles.contains(role)) {
                return true;
            }
        }

        // Check exact method match
        Set<String> requiredRoles = methodRoles.get(method);
        if (requiredRoles != null) {
            for (String role : userRoles) {
                if (requiredRoles.contains(role)) {
                    return true;
                }
            }
            return false;
        }

        // Check pattern matches
        for (Map.Entry<Pattern, Set<String>> entry : patternRoles.entrySet()) {
            if (entry.getKey().matcher(method).matches()) {
                for (String role : userRoles) {
                    if (entry.getValue().contains(role)) {
                        return true;
                    }
                }
                return false;
            }
        }

        return defaultAllow;
    }

    @Override
    public boolean hasPermission(String role, String permission) {
        // Check admin roles
        if (adminRoles.contains(role)) {
            return true;
        }

        // Check exact permission
        Set<String> requiredRoles = methodRoles.get(permission);
        if (requiredRoles != null) {
            return requiredRoles.contains(role);
        }

        // Check pattern matches
        for (Map.Entry<Pattern, Set<String>> entry : patternRoles.entrySet()) {
            if (entry.getKey().matcher(permission).matches()) {
                return entry.getValue().contains(role);
            }
        }

        return defaultAllow;
    }

    @Override
    public Set<String> getPermissions(String role) {
        Set<String> permissions = new HashSet<>();
        for (Map.Entry<String, Set<String>> entry : methodRoles.entrySet()) {
            if (entry.getValue().contains(role)) {
                permissions.add(entry.getKey());
            }
        }
        return permissions;
    }

    /**
     * Creates a default RBAC policy for STT service.
     */
    public static RbacPolicyImpl createDefault() {
        return builder()
            .adminRole("admin")
            .adminRole("stt-admin")
            // Transcription endpoints
            .allow("com.ghatana.stt.grpc.STTService/Transcribe", "user", "transcriber")
            .allow("com.ghatana.stt.grpc.STTService/StreamTranscribe", "user", "transcriber")
            // Model management (admin only by default)
            .allow("com.ghatana.stt.grpc.STTService/LoadModel", "model-manager")
            .allow("com.ghatana.stt.grpc.STTService/UnloadModel", "model-manager")
            .allow("com.ghatana.stt.grpc.STTService/ListModels", "user", "model-manager")
            // Adaptation
            .allow("com.ghatana.stt.grpc.STTService/AdaptModel", "user", "transcriber")
            .allow("com.ghatana.stt.grpc.STTService/SubmitCorrection", "user", "transcriber")
            // Profile management
            .allowPattern("com.ghatana.stt.grpc.STTService/.*Profile.*", "user", "profile-manager")
            .defaultAllow(false)
            .build();
    }

    /**
     * Creates an allow-all policy (for development/testing).
     */
    public static RbacPolicyImpl createAllowAll() {
        return builder().defaultAllow(true).build();
    }

    /**
     * Loads RBAC policy from a YAML file.
     *
     * @param path path to YAML policy file
     * @return loaded policy
     * @throws IOException if file cannot be read
     */
    public static RbacPolicyImpl loadFromYaml(Path path) throws IOException {
        Builder builder = builder();

        try (InputStream is = Files.newInputStream(path)) {
            String content = new String(is.readAllBytes());
            parseYamlPolicy(content, builder);
        }

        return builder.build();
    }

    private static void parseYamlPolicy(String yaml, Builder builder) {
        // Simple YAML parsing for policy structure:
        // admin_roles:
        //   - admin
        // default_allow: false
        // rules:
        //   - method: "Service/Method"
        //     roles: [role1, role2]

        String[] lines = yaml.split("\n");
        String currentSection = null;
        String currentMethod = null;

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            if (line.equals("admin_roles:")) {
                currentSection = "admin_roles";
            } else if (line.equals("rules:")) {
                currentSection = "rules";
            } else if (line.startsWith("default_allow:")) {
                String value = line.substring("default_allow:".length()).trim();
                builder.defaultAllow(Boolean.parseBoolean(value));
            } else if (currentSection != null) {
                if (currentSection.equals("admin_roles") && line.startsWith("- ")) {
                    builder.adminRole(line.substring(2).trim());
                } else if (currentSection.equals("rules")) {
                    if (line.startsWith("- method:")) {
                        currentMethod = line.substring("- method:".length()).trim().replace("\"", "");
                    } else if (line.startsWith("roles:") && currentMethod != null) {
                        String rolesStr = line.substring("roles:".length()).trim();
                        rolesStr = rolesStr.replace("[", "").replace("]", "");
                        String[] roles = rolesStr.split(",");
                        for (String role : roles) {
                            builder.allow(currentMethod, role.trim().replace("\"", ""));
                        }
                        currentMethod = null;
                    }
                }
            }
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final Map<String, Set<String>> methodRoles = new HashMap<>();
        private final Map<Pattern, Set<String>> patternRoles = new HashMap<>();
        private final Set<String> adminRoles = new HashSet<>();
        private boolean defaultAllow = false;

        public Builder allow(String method, String... roles) {
            methodRoles.computeIfAbsent(method, k -> new HashSet<>())
                .addAll(Set.of(roles));
            return this;
        }

        public Builder allowPattern(String pattern, String... roles) {
            patternRoles.computeIfAbsent(Pattern.compile(pattern), k -> new HashSet<>())
                .addAll(Set.of(roles));
            return this;
        }

        public Builder adminRole(String role) {
            adminRoles.add(role);
            return this;
        }

        public Builder defaultAllow(boolean allow) {
            this.defaultAllow = allow;
            return this;
        }

        public RbacPolicyImpl build() {
            return new RbacPolicyImpl(this);
        }
    }
}
