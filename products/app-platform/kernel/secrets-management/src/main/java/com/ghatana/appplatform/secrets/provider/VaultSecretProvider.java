package com.ghatana.appplatform.secrets.provider;

import com.ghatana.appplatform.secrets.domain.SecretMetadata;
import com.ghatana.appplatform.secrets.domain.SecretValue;
import com.ghatana.appplatform.secrets.port.SecretProvider;
import io.activej.promise.Promise;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * HashiCorp Vault KV v2 implementation of {@link SecretProvider}.
 *
 * <p>Communicates with Vault via the KV v2 API using the Java 11+ {@link HttpClient}.
 * All HTTP calls are wrapped in {@link Promise#ofBlocking} to avoid blocking the ActiveJ eventloop.
 *
 * <p>Authentication uses a Vault token provided at construction. For production,
 * use AppRole or Kubernetes auth and rotate the token via {@link SecretRotationScheduler}.
 *
 * <p>API paths:
 * <ul>
 *   <li>GET  {@code /v1/{mount}/data/{path}} — read latest secret version
 *   <li>POST {@code /v1/{mount}/data/{path}} — write/update secret
 *   <li>DELETE {@code /v1/{mount}/metadata/{path}} — delete all versions
 *   <li>LIST {@code /v1/{mount}/metadata/{prefix}} — list paths under prefix
 * </ul>
 *
 * @doc.type class
 * @doc.purpose HashiCorp Vault KV v2 secret provider adapter (STORY-K14)
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class VaultSecretProvider implements SecretProvider {

    private final String vaultAddr;   // e.g. "https://vault.internal:8200"
    private final String vaultToken;  // root or AppRole token
    private final String mount;       // KV v2 mount, e.g. "secret"
    private final HttpClient httpClient;
    private final Executor executor;

    public VaultSecretProvider(String vaultAddr, String vaultToken, String mount, Executor executor) {
        this.vaultAddr = vaultAddr.endsWith("/") ? vaultAddr.substring(0, vaultAddr.length() - 1) : vaultAddr;
        this.vaultToken = vaultToken;
        this.mount = mount;
        this.httpClient = HttpClient.newBuilder().build();
        this.executor = executor;
    }

    @Override
    public Promise<SecretValue> getSecret(String path) {
        return Promise.ofBlocking(executor, () -> {
            String url = vaultAddr + "/v1/" + mount + "/data" + normalizePath(path);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("X-Vault-Token", vaultToken)
                .GET()
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                throw new IllegalArgumentException("Secret not found at path: " + path);
            }
            if (response.statusCode() != 200) {
                throw new RuntimeException("Vault GET failed: " + response.statusCode() + " " + response.body());
            }
            return parseGetResponse(path, response.body());
        });
    }

    @Override
    public Promise<SecretValue> putSecret(String path, char[] value, SecretMetadata metadata) {
        return Promise.ofBlocking(executor, () -> {
            String url = vaultAddr + "/v1/" + mount + "/data" + normalizePath(path);
            String body = "{\"data\":{\"value\":\"" + escapeJson(new String(value)) + "\"}}";
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("X-Vault-Token", vaultToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 && response.statusCode() != 204) {
                throw new RuntimeException("Vault PUT failed: " + response.statusCode() + " " + response.body());
            }
            int version = parseVersion(response.body());
            return new SecretValue(path, version, value, Instant.now(),
                metadata.maxAge() != null ? Instant.now().plus(metadata.maxAge()) : null);
        });
    }

    @Override
    public Promise<Void> deleteSecret(String path) {
        return Promise.ofBlocking(executor, () -> {
            String url = vaultAddr + "/v1/" + mount + "/metadata" + normalizePath(path);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("X-Vault-Token", vaultToken)
                .DELETE()
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 && response.statusCode() != 204) {
                throw new RuntimeException("Vault DELETE failed: " + response.statusCode());
            }
            return null;
        });
    }

    @Override
    public Promise<List<String>> listSecrets(String prefix) {
        return Promise.ofBlocking(executor, () -> {
            String url = vaultAddr + "/v1/" + mount + "/metadata" + normalizePath(prefix)
                + "?list=true";
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("X-Vault-Token", vaultToken)
                .GET()
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) return List.of();
            if (response.statusCode() != 200) {
                throw new RuntimeException("Vault LIST failed: " + response.statusCode());
            }
            return parseListResponse(prefix, response.body());
        });
    }

    @Override
    public Promise<SecretValue> rotateSecret(String path) {
        // Read current, generate a new random value, and write it back
        return getSecret(path).then(current -> {
            char[] newValue = generateRandomSecret(32);
            return putSecret(path, newValue, SecretMetadata.defaults());
        });
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    private String normalizePath(String path) {
        return path.startsWith("/") ? path : "/" + path;
    }

    private SecretValue parseGetResponse(String path, String json) {
        // Minimal JSON parsing — extract "value" from data.data and version from data.metadata
        String valueStr = extractJsonField(json, "\"value\"");
        String versionStr = extractJsonField(json, "\"version\"");
        int version = versionStr != null ? Integer.parseInt(versionStr.trim()) : 1;
        char[] value = valueStr != null ? valueStr.toCharArray() : new char[0];
        return new SecretValue(path, version, value, Instant.now(), null);
    }

    private int parseVersion(String json) {
        String versionStr = extractJsonField(json, "\"version\"");
        return versionStr != null ? Integer.parseInt(versionStr.trim()) : 1;
    }

    private List<String> parseListResponse(String prefix, String json) {
        // Extract string array from "keys": ["key1","key2",...]
        int keysIdx = json.indexOf("\"keys\"");
        if (keysIdx == -1) return List.of();
        int arrStart = json.indexOf("[", keysIdx);
        int arrEnd   = json.indexOf("]", arrStart);
        if (arrStart == -1 || arrEnd == -1) return List.of();
        String arrContent = json.substring(arrStart + 1, arrEnd);
        List<String> keys = new ArrayList<>();
        for (String part : arrContent.split(",")) {
            String key = part.trim().replace("\"", "");
            if (!key.isEmpty()) keys.add(prefix + key);
        }
        return keys;
    }

    private String extractJsonField(String json, String fieldKey) {
        int idx = json.indexOf(fieldKey);
        if (idx == -1) return null;
        int colonIdx = json.indexOf(":", idx + fieldKey.length());
        if (colonIdx == -1) return null;
        int valStart = colonIdx + 1;
        while (valStart < json.length() && json.charAt(valStart) == ' ') valStart++;
        if (valStart >= json.length()) return null;
        if (json.charAt(valStart) == '"') {
            int valEnd = json.indexOf("\"", valStart + 1);
            return valEnd == -1 ? null : json.substring(valStart + 1, valEnd);
        } else {
            int valEnd = valStart;
            while (valEnd < json.length() && json.charAt(valEnd) != ',' && json.charAt(valEnd) != '}') valEnd++;
            return json.substring(valStart, valEnd).trim();
        }
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\n", "\\n").replace("\r", "\\r");
    }

    private char[] generateRandomSecret(int length) {
        java.security.SecureRandom random = new java.security.SecureRandom();
        char[] chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*".toCharArray();
        char[] result = new char[length];
        for (int i = 0; i < length; i++) {
            result[i] = chars[random.nextInt(chars.length)];
        }
        return result;
    }

    // ─── K14-013 version history ──────────────────────────────────────────────

    @Override
    public Promise<SecretValue> getSecretVersion(String path, int version) {
        return Promise.ofBlocking(executor, () -> {
            // Vault KV v2: GET /v1/{mount}/data/{path}?version={n}
            String url = vaultAddr + "/v1/" + mount + "/data" + normalizePath(path)
                       + "?version=" + version;
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("X-Vault-Token", vaultToken)
                .GET()
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                throw new SecretNotFoundException(path + "@v" + version);
            }
            if (response.statusCode() != 200) {
                throw new RuntimeException("Vault GET version failed: " + response.statusCode());
            }
            return parseGetResponse(path, response.body());
        });
    }
}

