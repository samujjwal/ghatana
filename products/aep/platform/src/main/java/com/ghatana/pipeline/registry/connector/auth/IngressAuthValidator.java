package com.ghatana.pipeline.registry.connector.auth;

import com.ghatana.platform.domain.domain.pipeline.ConnectorSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

/**
 * Validates inbound Authorization tokens for HTTP ingress connectors.
 *
 * <p>Reads auth configuration from {@link ConnectorSpec#getProperties()}:
 * <ul>
 *   <li>{@code auth.type} — NONE | BEARER | API_KEY | BASIC (default: NONE)</li>
 *   <li>{@code auth.token} — expected Bearer token or API key value</li>
 *   <li>{@code auth.header} — header name for API_KEY mode (default: X-Api-Key)</li>
 *   <li>{@code auth.username} / {@code auth.password} — for BASIC mode</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Ingress HTTP authentication validator
 * @doc.layer product
 * @doc.pattern Strategy
 */
public final class IngressAuthValidator {

    private static final Logger LOG = LoggerFactory.getLogger(IngressAuthValidator.class);

    /**
     * Result of an auth validation check.
     *
     * @param isAuthorized whether the request is authorized
     * @param reason       human-readable reason if rejected (empty string when authorized)
     */
    public record AuthResult(boolean isAuthorized, String reason) {
        public static AuthResult allow() {
            return new AuthResult(true, "");
        }

        public static AuthResult deny(String reason) {
            return new AuthResult(false, reason);
        }
    }

    private enum AuthType {
        NONE, BEARER, API_KEY, BASIC
    }

    private final AuthType authType;
    private final String expectedToken;
    private final String apiKeyHeader;
    private final String expectedBasic;

    private IngressAuthValidator(AuthType authType, String expectedToken,
                                  String apiKeyHeader, String expectedBasic) {
        this.authType = authType;
        this.expectedToken = expectedToken;
        this.apiKeyHeader = apiKeyHeader;
        this.expectedBasic = expectedBasic;
    }

    /**
     * Build a validator from a {@link ConnectorSpec}'s properties map.
     * Falls back to NONE (allow-all) if no auth properties are configured.
     *
     * <p><b>Secret indirection</b>: property values prefixed with {@code env:} are
     * resolved from the environment at construction time so that plaintext secrets
     * are never stored inside a {@link ConnectorSpec}. For example:
     * <pre>
     *   auth.type  = BEARER
     *   auth.token = env:AEP_CONNECTOR_TOKEN
     * </pre>
     * If the referenced variable is absent or blank the validator falls back to NONE
     * and logs a warning — same behaviour as a missing literal value.
     */
    public static IngressAuthValidator fromSpec(ConnectorSpec spec) {
        Map<String, String> props = spec != null ? spec.getProperties() : null;
        if (props == null || props.isEmpty()) {
            return noAuth();
        }

        String typeStr = props.getOrDefault("auth.type", "NONE").toUpperCase();
        AuthType type;
        try {
            type = AuthType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            LOG.warn("Unknown auth.type '{}' in connector spec — defaulting to NONE", typeStr);
            type = AuthType.NONE;
        }

        return switch (type) {
            case BEARER -> {
                String token = resolve(props.get("auth.token"));
                if (token == null || token.isBlank()) {
                    LOG.warn("BEARER auth configured but auth.token is missing — defaulting to NONE");
                    yield noAuth();
                }
                yield new IngressAuthValidator(AuthType.BEARER, token, null, null);
            }
            case API_KEY -> {
                String token = resolve(props.get("auth.token"));
                String header = props.getOrDefault("auth.header", "X-Api-Key");
                if (token == null || token.isBlank()) {
                    LOG.warn("API_KEY auth configured but auth.token is missing — defaulting to NONE");
                    yield noAuth();
                }
                yield new IngressAuthValidator(AuthType.API_KEY, token, header, null);
            }
            case BASIC -> {
                String user = resolve(props.get("auth.username"));
                String pass = resolve(props.getOrDefault("auth.password", ""));
                if (user == null || user.isBlank()) {
                    LOG.warn("BASIC auth configured but auth.username is missing — defaulting to NONE");
                    yield noAuth();
                }
                String encoded = Base64.getEncoder()
                        .encodeToString((user + ":" + pass).getBytes(StandardCharsets.UTF_8));
                yield new IngressAuthValidator(AuthType.BASIC, null, null, encoded);
            }
            default -> noAuth();
        };
    }

    /**
     * Resolves a property value, expanding {@code env:VAR_NAME} references to the
     * actual environment variable value. Returns the original value unchanged if it
     * does not start with {@code env:}.
     *
     * @param value raw property value (may be null)
     * @return resolved value, or null if the env var is absent/blank
     */
    static String resolve(String value) {
        if (value == null) {
            return null;
        }
        if (value.startsWith("env:")) {
            String varName = value.substring(4).strip();
            String envVal = System.getenv(varName);
            if (envVal == null || envVal.isBlank()) {
                LOG.warn("Auth property references env var '{}' which is not set", varName);
                return null;
            }
            return envVal;
        }
        return value;
    }

    /**
     * Creates an allow-all validator (no authentication required).
     */
    public static IngressAuthValidator noAuth() {
        return new IngressAuthValidator(AuthType.NONE, null, null, null);
    }

    /**
     * Validate the raw Authorization header value (or null if the header was absent).
     *
     * <p>For API_KEY mode the caller must pass the value of the configured API-key header,
     * not the Authorization header.
     *
     * @param authorizationHeader raw header value
     * @return {@link AuthResult}
     */
    public AuthResult validate(String authorizationHeader) {
        return switch (authType) {
            case NONE -> AuthResult.allow();

            case BEARER -> {
                if (authorizationHeader == null || authorizationHeader.isBlank()) {
                    yield AuthResult.deny("missing Authorization header");
                }
                String prefix = "Bearer ";
                if (!authorizationHeader.startsWith(prefix)) {
                    yield AuthResult.deny("Authorization header must start with 'Bearer '");
                }
                String token = authorizationHeader.substring(prefix.length()).strip();
                yield Objects.equals(token, expectedToken)
                        ? AuthResult.allow()
                        : AuthResult.deny("invalid bearer token");
            }

            case API_KEY -> {
                if (authorizationHeader == null || authorizationHeader.isBlank()) {
                    yield AuthResult.deny("missing " + apiKeyHeader + " header");
                }
                yield Objects.equals(authorizationHeader.strip(), expectedToken)
                        ? AuthResult.allow()
                        : AuthResult.deny("invalid API key");
            }

            case BASIC -> {
                if (authorizationHeader == null || authorizationHeader.isBlank()) {
                    yield AuthResult.deny("missing Authorization header");
                }
                String prefix = "Basic ";
                if (!authorizationHeader.startsWith(prefix)) {
                    yield AuthResult.deny("Authorization header must start with 'Basic '");
                }
                String encoded = authorizationHeader.substring(prefix.length()).strip();
                yield Objects.equals(encoded, expectedBasic)
                        ? AuthResult.allow()
                        : AuthResult.deny("invalid Basic credentials");
            }
        };
    }

    public AuthType getAuthType() {
        return authType;
    }
}
