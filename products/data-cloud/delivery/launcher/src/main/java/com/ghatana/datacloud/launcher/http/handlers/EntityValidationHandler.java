package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.validation.EntitySchemaValidator;
import com.ghatana.datacloud.entity.validation.ValidationResult;
import com.ghatana.datacloud.launcher.http.ApiInputValidator;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Handles entity schema-validation HTTP endpoints.
 *
 * <p>Extracted from {@code EntityCrudHandler} to respect the single-responsibility
 * principle (DC-004). Exposes a dedicated validate endpoint so clients can check
 * conformance to a collection schema before committing a save. Registered via:
 * <pre>{@code
 * .with(HttpMethod.POST, "/api/v1/entities/:collection/validate", validationHandler::handleValidateEntity)
 * .with(HttpMethod.POST, "/api/v1/entities/:collection/validate/batch", validationHandler::handleBatchValidateEntities)
 * }</pre>
 *
 * <p>Returns {@code 501 Unavailable} when no {@link EntitySchemaValidator} is
 * configured.
 *
 * <p>WS13: Validation is connected to data quality scoring and governance policy.
 * When QualityScoringService is available, validation responses include quality
 * scores and policy compliance information.
 *
 * @doc.type    class
 * @doc.purpose HTTP handler for validating entity data against collection schemas
 * @doc.layer   product
 * @doc.pattern Handler
 */
public class EntityValidationHandler {

    private static final Logger log = LoggerFactory.getLogger(EntityValidationHandler.class);

    private final EntitySchemaValidator schemaValidator;
    private final HttpHandlerSupport http;
    private final com.ghatana.datacloud.application.service.QualityScoringService qualityScoringService;

    /**
     * Creates a validation handler.
     *
     * @param schemaValidator the schema validator; may be {@code null} — handler returns 501 in that case
     * @param http            shared HTTP helpers
     * @param qualityScoringService optional quality scoring service for WS13 data quality integration
     */
    public EntityValidationHandler(EntitySchemaValidator schemaValidator, HttpHandlerSupport http,
                                   com.ghatana.datacloud.application.service.QualityScoringService qualityScoringService) {
        this.schemaValidator = schemaValidator;
        this.http = http;
        this.qualityScoringService = qualityScoringService;
    }

    /**
     * Validates a single entity payload against the collection's schema without saving.
     *
     * <p>Request body: the entity payload as a JSON object.
     * Response: {@code {"valid": true}} or {@code {"valid": false, "violations": ["..."]}}
     *
     * <p>WS13: When QualityScoringService is available, validation response includes
     * quality score and policy compliance information.
     *
     * @param request the incoming HTTP request
     * @return a Promise resolving to the HTTP response
     */
    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleValidateEntity(HttpRequest request) {
        if (schemaValidator == null) {
            return Promise.of(http.serviceUnavailableResponse(
                "Schema validation capability is not configured on this server",
                60));
        }

        String collection = request.getPathParameter("collection");
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();

        Optional<String> tenantErr = ApiInputValidator.validateTenantId(tenantId);
        if (tenantErr.isPresent()) return Promise.of(http.errorResponse(400, tenantErr.get()));
        Optional<String> collErr = ApiInputValidator.validateCollection(collection);
        if (collErr.isPresent()) return Promise.of(http.errorResponse(400, collErr.get()));

        final String finalTenant     = tenantId;
        final String finalCollection = collection;

        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> data = http.objectMapper().readValue(body, Map.class);

                ValidationResult result = schemaValidator.validate(finalTenant, finalCollection, data);
                
                // WS13: Include quality score when QualityScoringService is available
                if (qualityScoringService != null && result.valid()) {
                    // Create a temporary Entity for quality scoring
                    String entityId = "temp-" + System.currentTimeMillis();
                    Entity entity = new Entity(entityId, finalCollection, data);
                    
                    return qualityScoringService.scoreEntity(finalTenant, entity)
                        .then(scoringResponse -> {
                            Map<String, Object> response = new LinkedHashMap<>();
                            response.put("valid", true);
                            response.put("collection", finalCollection);
                            response.put("timestamp", Instant.now().toString());
                            
                            if (scoringResponse.isSuccess()) {
                                response.put("qualityScore", scoringResponse.metrics().getOverallScore());
                                response.put("qualityLevel", scoringResponse.metrics().getQualityLevel().getDisplayName());
                                response.put("qualityMetrics", scoringResponse.metrics().toMap());
                            }
                            
                            return Promise.of(http.jsonResponse(response));
                        })
                        .mapException(ex -> {
                            log.warn("Quality scoring failed for validation, returning basic response", ex);
                            // Fall back to basic validation response if quality scoring fails
                            return http.jsonResponse(Map.of(
                                "valid", true,
                                "collection", finalCollection,
                                "timestamp", Instant.now().toString()
                            ));
                        });
                }
                
                if (result.valid()) {
                    return Promise.of(http.jsonResponse(Map.of(
                        "valid", true,
                        "collection", finalCollection,
                        "timestamp", Instant.now().toString()
                    )));
                } else {
                    return Promise.of(http.jsonResponse(Map.of(
                        "valid", false,
                        "collection", finalCollection,
                        "violations", result.violationSummary(),
                        "timestamp", Instant.now().toString()
                    )));
                }
            } catch (Exception e) {
                log.error("Error parsing validation request tenant={} collection={}", finalTenant, finalCollection, e);
                return Promise.of(http.errorResponse(400, "Invalid request body: " + e.getMessage()));
            }
        });
    }

    /**
     * Validates a batch of entity payloads against the collection's schema without saving.
     *
     * <p>Request body: {@code {"entities": [{...}, ...]}}
     * Response: per-item validation results with overall {@code "allValid"} flag.
     *
     * @param request the incoming HTTP request
     * @return a Promise resolving to the HTTP response
     */
    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleBatchValidateEntities(HttpRequest request) {
        if (schemaValidator == null) {
            return Promise.of(http.serviceUnavailableResponse(
                "Schema validation capability is not configured on this server",
                60));
        }

        String collection = request.getPathParameter("collection");
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();

        Optional<String> tenantErr = ApiInputValidator.validateTenantId(tenantId);
        if (tenantErr.isPresent()) return Promise.of(http.errorResponse(400, tenantErr.get()));
        Optional<String> collErr = ApiInputValidator.validateCollection(collection);
        if (collErr.isPresent()) return Promise.of(http.errorResponse(400, collErr.get()));

        final String finalTenant     = tenantId;
        final String finalCollection = collection;

        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> payload = http.objectMapper().readValue(body, Map.class);

                Object rawEntities = payload.get("entities");
                if (!(rawEntities instanceof List)) {
                    return Promise.of(http.errorResponse(400, "Request body must contain an 'entities' array"));
                }

                List<Map<String, Object>> entityList = (List<Map<String, Object>>) rawEntities;
                Optional<String> batchErr = ApiInputValidator.validateBatchSize(entityList);
                if (batchErr.isPresent()) return Promise.of(http.errorResponse(400, batchErr.get()));

                List<Map<String, Object>> results = new ArrayList<>();
                boolean allValid = true;

                for (int i = 0; i < entityList.size(); i++) {
                    ValidationResult vr = schemaValidator.validate(finalTenant, finalCollection, entityList.get(i));
                    results.add(Map.of(
                        "index", i,
                        "valid", vr.valid(),
                        "violations", vr.valid() ? List.of() : vr.violationSummary()
                    ));
                    if (!vr.valid()) allValid = false;
                }

                return Promise.of(http.jsonResponse(Map.of(
                    "allValid", allValid,
                    "collection", finalCollection,
                    "count", entityList.size(),
                    "results", results,
                    "timestamp", Instant.now().toString()
                )));
            } catch (Exception e) {
                log.error("Error parsing batch validation request tenant={} collection={}", finalTenant, finalCollection, e);
                return Promise.of(http.errorResponse(400, "Invalid request body: " + e.getMessage()));
            }
        });
    }
}
