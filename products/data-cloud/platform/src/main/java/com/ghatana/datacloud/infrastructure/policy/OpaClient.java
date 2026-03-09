package com.ghatana.datacloud.infrastructure.policy;

import io.activej.promise.Promise;

import java.util.Map;

/**
 * HTTP client for Open Policy Agent (OPA) REST API.
 *
 * <p><b>Purpose</b><br>
 * Provides low-level HTTP communication with OPA server.
 * Abstracts HTTP transport details from policy engine logic.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * OpaClient client = new ActiveJOpaClient(httpClient, objectMapper);
 *
 * Map<String, Object> request = Map.of("input", policyInput);
 * Map<String, Object> response = runPromise(() ->
 *     client.evaluate("http://opa:8181/v1/data", "schema_change", request)
 * );
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * - Infrastructure abstraction for OPA HTTP API
 * - Implemented by ActiveJOpaClient (using core/http-server)
 * - Used by OpaPolicyEngine
 *
 * <p><b>Thread Safety</b><br>
 * Implementations must be thread-safe.
 *
 * @doc.type interface
 * @doc.purpose HTTP client for OPA REST API
 * @doc.layer product
 * @doc.pattern Adapter
 */
public interface OpaClient {

    /**
     * Evaluates a policy via OPA API.
     *
     * <p><b>HTTP Request</b><br>
     * POST {endpoint}/{policyName}/evaluate
     * Body: JSON with "input" field
     *
     * <p><b>HTTP Response</b><br>
     * JSON with "allow", "reason", "metadata" fields
     *
     * @param endpoint the OPA API endpoint (required)
     * @param policyName the policy to evaluate (required)
     * @param request the request body (required, contains "input")
     * @return Promise of response map (with "allow", "reason", "metadata")
     * @throws IllegalArgumentException if parameters invalid
     */
    Promise<Map<String, Object>> evaluate(String endpoint, String policyName, Map<String, Object> request);

    /**
     * Reloads policies from OPA server.
     *
     * <p><b>HTTP Request</b><br>
     * POST {endpoint}/reload
     *
     * @param endpoint the OPA API endpoint (required)
     * @return Promise of void when reload complete
     */
    Promise<Void> reloadPolicies(String endpoint);

    /**
     * Validates a policy definition.
     *
     * <p><b>HTTP Request</b><br>
     * POST {endpoint}/validate
     * Body: {"policy": policyName, "definition": regoCode}
     *
     * <p><b>HTTP Response</b><br>
     * JSON with "valid" boolean and "errors" array
     *
     * @param endpoint the OPA API endpoint (required)
     * @param policyName the policy name (required)
     * @param policyDefinition the Rego policy code (required)
     * @return Promise of validation response map
     */
    Promise<Map<String, Object>> validatePolicy(String endpoint, String policyName, String policyDefinition);
}
