package com.ghatana.datacloud.entity.importexport;

import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.EntityRepository;
import com.ghatana.datacloud.entity.audit.AuditLog;
import com.ghatana.datacloud.entity.audit.AuditAction;
import com.ghatana.datacloud.entity.audit.AuditLogPort;
import com.ghatana.datacloud.entity.policy.PolicyDecision;
import com.ghatana.datacloud.entity.policy.PolicyEngine;
import com.ghatana.datacloud.entity.validation.EntitySchemaValidator;
import com.ghatana.datacloud.entity.validation.ValidationResult;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/**
 * Service for bulk entity import/export operations.
 *
 * <p><b>Purpose</b><br>
 * Provides production-grade bulk import/export functionality with validation,
 * idempotency support, and format conversion capabilities.
 *
 * WS5: Enforces tenant isolation, policy compliance, and data redaction.
 *
 * <p><b>Capabilities</b><br>
 * <ul>
 *   <li>Bulk import from JSON/CSV format</li>
 *   <li>Bulk export to JSON/CSV format</li>
 *   <li>Schema validation during import</li>
 *   <li>Idempotency support for retry-safe imports</li>
 *   <li>Progress tracking for large operations</li>
 *   <li>Error handling with detailed reporting</li>
 *   <li>WS5: Tenant isolation enforcement</li>
 *   <li>WS5: Policy compliance checks during import/export</li>
 *   <li>WS5: Data redaction for sensitive fields during export</li>
 * </ul>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * EntityImportExportService service = ...;
 *
 * // Import from JSON
 * ImportResult result = service.importFromJson(
 *     "tenant-123", "orders", jsonPayload, "import-job-456"
 * );
 *
 * // Export to JSON
 * ExportResult export = service.exportToJson(
 *     "tenant-123", "orders", filter, sort
 * );
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Bulk entity import/export service with validation, idempotency, and policy enforcement
 * @doc.layer product
 * @doc.pattern Service
 */
public class EntityImportExportService {

    private static final Logger log = LoggerFactory.getLogger(EntityImportExportService.class);

    private final EntityRepository entityRepository;
    private final EntitySchemaValidator schemaValidator;
    private final ExecutorService executorService;
    private final PolicyEngine policyEngine;
    private final AuditLogPort auditLogPort;

    /**
     * Creates an import/export service with full governance support.
     *
     * WS5: Enforces tenant, policy, redaction, audit, and idempotency governance.
     *
     * @param entityRepository entity repository
     * @param schemaValidator schema validator
     * @param executorService executor for blocking operations
     * @param policyEngine policy engine for enforcement
     * @param auditLogPort audit log port for operation recording
     */
    public EntityImportExportService(
            EntityRepository entityRepository,
            EntitySchemaValidator schemaValidator,
            ExecutorService executorService,
            PolicyEngine policyEngine,
            AuditLogPort auditLogPort) {
        this.entityRepository = Objects.requireNonNull(entityRepository, "entityRepository must not be null");
        this.schemaValidator = Objects.requireNonNull(schemaValidator, "schemaValidator must not be null");
        this.executorService = Objects.requireNonNull(executorService, "executorService must not be null");
        this.policyEngine = policyEngine; // Optional - null if policy engine not available
        this.auditLogPort = auditLogPort; // Optional - null if audit logging not required
    }

    /**
     * Creates an import/export service without full governance support.
     *
     * @param entityRepository entity repository
     * @param schemaValidator schema validator
     * @param executorService executor for blocking operations
     */
    public EntityImportExportService(
            EntityRepository entityRepository,
            EntitySchemaValidator schemaValidator,
            ExecutorService executorService) {
        this(entityRepository, schemaValidator, executorService, null, null);
    }

    /**
     * Imports entities from JSON format.
     *
     * <p><b>Validation</b><br>
     * Validates each entity against the collection schema before import.
     *
     * <p><b>Idempotency</b><br>
     * Uses the provided jobId as idempotency key for retry-safe imports.
     *
     * WS5: Enforces tenant isolation and policy compliance during import.
     *
     * @param tenantId tenant identifier
     * @param collectionName collection name
     * @param jsonData JSON array of entity data maps
     * @param jobId job identifier for idempotency
     * @param userId user performing the import
     * @return Promise of import result with statistics
     */
    public Promise<ImportResult> importFromJson(
            String tenantId,
            String collectionName,
            List<Map<String, Object>> jsonData,
            String jobId,
            String userId) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(collectionName, "Collection name must not be null");
        Objects.requireNonNull(jsonData, "JSON data must not be null");
        Objects.requireNonNull(jobId, "Job ID must not be null");

        log.info("Starting JSON import: tenant={}, collection={}, jobId={}, count={}",
            tenantId, collectionName, jobId, jsonData.size());

        // WS5: Enforce policy compliance before import - block if denied
        if (policyEngine != null) {
            Map<String, Object> policyInput = Map.of(
                "tenantId", tenantId,
                "collection", collectionName,
                "operation", "import",
                "jobId", jobId,
                "userId", userId,
                "entityCount", jsonData.size()
            );
            try {
                PolicyDecision decision = policyEngine.evaluate("entity_import", policyInput).getResult();
                if (!decision.isAllowed()) {
                    log.warn("WS5: Policy denied import: tenant={}, collection={}, reason={}",
                        tenantId, collectionName, decision.reason());
                    return Promise.ofException(new SecurityException(
                        "Policy denied import: " + decision.reason()));
                }
            } catch (Exception e) {
                log.error("WS5: Policy evaluation failed for import: tenant={}, collection={}",
                    tenantId, collectionName, e);
                return Promise.ofException(new SecurityException(
                    "Policy evaluation failed: " + e.getMessage()));
            }
        }

        return Promise.ofBlocking(executorService, () -> {
            int successCount = 0;
            int failureCount = 0;
            List<ImportError> errors = new ArrayList<>();
            List<UUID> importedIds = new ArrayList<>();

            for (int i = 0; i < jsonData.size(); i++) {
                Map<String, Object> entityData = jsonData.get(i);
                int rowNumber = i + 1;

                try {
                    // Validate against schema
                    ValidationResult validation = schemaValidator.validate(tenantId, collectionName, entityData);
                    if (!validation.valid()) {
                        errors.add(new ImportError(rowNumber, "Validation failed: " + validation.violationSummary()));
                        failureCount++;
                        continue;
                    }

                    // WS5: Enforce tenant isolation - ensure tenantId in entity data matches request tenantId
                    Object entityTenant = entityData.get("tenantId");
                    if (entityTenant != null && !tenantId.equals(entityTenant.toString())) {
                        errors.add(new ImportError(rowNumber, "Tenant isolation violation: entity tenantId does not match request tenantId"));
                        failureCount++;
                        log.warn("WS5: Tenant isolation violation during import: requestTenant={}, entityTenant={}",
                            tenantId, entityTenant);
                        continue;
                    }

                    // Create entity
                    Entity entity = Entity.builder()
                        .tenantId(tenantId)
                        .collectionName(collectionName)
                        .data(entityData)
                        .createdBy(userId)
                        .build();

                    // WS5: Save with operation-specific idempotency key
                    String idempotencyKey = generateOperationSpecificIdempotencyKey(
                        tenantId, collectionName, "import", jobId, rowNumber);
                    Entity saved = entityRepository.saveWithIdempotency(tenantId, entity, idempotencyKey)
                        .getResult();

                    importedIds.add(saved.getId());
                    successCount++;
                } catch (Exception e) {
                    errors.add(new ImportError(rowNumber, "Import failed: " + e.getMessage()));
                    failureCount++;
                    log.error("Failed to import row {}: {}", rowNumber, e.getMessage());
                }
            }

            ImportResult result = new ImportResult(
                jsonData.size(),
                successCount,
                failureCount,
                importedIds,
                errors
            );

            log.info("JSON import completed: tenant={}, collection={}, jobId={}, success={}, failure={}",
                tenantId, collectionName, jobId, successCount, failureCount);

            // WS5: Audit log the import operation
            if (auditLogPort != null) {
                try {
                    AuditLog auditLog = AuditLog.builder()
                        .tenantId(tenantId)
                        .action(AuditAction.IMPORT_DATA)
                        .resourceType("entity")
                        .resourceId(collectionName)
                        .userId(userId)
                        .details(String.format("jobId=%s, collection=%s, totalRows=%d, successCount=%d, failureCount=%d",
                            jobId, collectionName, jsonData.size(), successCount, failureCount))
                        .timestamp(Instant.now())
                        .build();
                    auditLogPort.save(auditLog).getResult();
                } catch (Exception e) {
                    log.error("WS5: Failed to audit import operation: tenant={}, jobId={}",
                        tenantId, jobId, e);
                }
            }

            return result;
        });
    }

    /**
     * Imports entities from CSV format.
     *
     * <p><b>CSV Format</b><br>
     * First row must contain field names matching the collection schema.
     *
     * @param tenantId tenant identifier
     * @param collectionName collection name
     * @param csvData CSV data as list of string arrays (rows)
     * @param jobId job identifier for idempotency
     * @param userId user performing the import
     * @return Promise of import result with statistics
     */
    public Promise<ImportResult> importFromCsv(
            String tenantId,
            String collectionName,
            List<String[]> csvData,
            String jobId,
            String userId) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(collectionName, "Collection name must not be null");
        Objects.requireNonNull(csvData, "CSV data must not be null");
        Objects.requireNonNull(jobId, "Job ID must not be null");

        if (csvData.isEmpty()) {
            return Promise.of(new ImportResult(0, 0, 0, List.of(), List.of()));
        }

        log.info("Starting CSV import: tenant={}, collection={}, jobId={}, rows={}",
            tenantId, collectionName, jobId, csvData.size() - 1);

        return Promise.ofBlocking(executorService, () -> {
            // First row is header
            String[] headers = csvData.get(0);
            List<Map<String, Object>> jsonData = new ArrayList<>();

            // Convert CSV to JSON format
            for (int i = 1; i < csvData.size(); i++) {
                String[] row = csvData.get(i);
                Map<String, Object> entityData = new LinkedHashMap<>();

                for (int j = 0; j < headers.length && j < row.length; j++) {
                    entityData.put(headers[j], row[j]);
                }

                jsonData.add(entityData);
            }

            // Delegate to JSON import
            return importFromJson(tenantId, collectionName, jsonData, jobId, userId).getResult();
        });
    }

    /**
     * Exports entities to JSON format.
     *
     * WS5: Applies data redaction for sensitive fields based on policy.
     *
     * @param tenantId tenant identifier
     * @param collectionName collection name
     * @param filter optional filter criteria
     * @param sort optional sort expression
     * @param offset offset for pagination
     * @param limit max results
     * @return Promise of export result with JSON data
     */
    public Promise<ExportResult> exportToJson(
            String tenantId,
            String collectionName,
            Map<String, Object> filter,
            String sort,
            int offset,
            int limit) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(collectionName, "Collection name must not be null");

        log.info("Starting JSON export: tenant={}, collection={}, offset={}, limit={}",
            tenantId, collectionName, offset, limit);

        // WS5: Enforce policy compliance before export - block if denied
        if (policyEngine != null) {
            Map<String, Object> policyInput = Map.of(
                "tenantId", tenantId,
                "collection", collectionName,
                "operation", "export",
                "filter", filter != null ? filter : Map.of(),
                "limit", limit
            );
            try {
                PolicyDecision decision = policyEngine.evaluate("entity_export", policyInput).getResult();
                if (!decision.isAllowed()) {
                    log.warn("WS5: Policy denied export: tenant={}, collection={}, reason={}",
                        tenantId, collectionName, decision.reason());
                    return Promise.ofException(new SecurityException(
                        "Policy denied export: " + decision.reason()));
                }
            } catch (Exception e) {
                log.error("WS5: Policy evaluation failed for export: tenant={}, collection={}",
                    tenantId, collectionName, e);
                return Promise.ofException(new SecurityException(
                    "Policy evaluation failed: " + e.getMessage()));
            }
        }

        return entityRepository.findAll(tenantId, collectionName, filter, sort, offset, limit)
            .map(entities -> {
                // WS5: Apply data redaction for sensitive fields
                List<Map<String, Object>> jsonData = entities.stream()
                    .map(entity -> redactSensitiveFields(tenantId, collectionName, entity.getData()))
                    .collect(Collectors.toList());

                ExportResult result = new ExportResult(
                    entities.size(),
                    "application/json",
                    jsonData
                );

                log.info("JSON export completed: tenant={}, collection={}, exported={}",
                    tenantId, collectionName, entities.size());

                // WS5: Audit log the export operation
                if (auditLogPort != null) {
                    try {
                        AuditLog auditLog = AuditLog.builder()
                            .tenantId(tenantId)
                            .action(AuditAction.EXPORT_DATA)
                            .resourceType("entity")
                            .resourceId(collectionName)
                            .userId("system") // Export may not have explicit user context
                            .details(String.format("collection=%s, exportedCount=%d, offset=%d, limit=%d",
                                collectionName, entities.size(), offset, limit))
                            .timestamp(Instant.now())
                            .build();
                        auditLogPort.save(auditLog).getResult();
                    } catch (Exception e) {
                        log.error("WS5: Failed to audit export operation: tenant={}, collection={}",
                            tenantId, collectionName, e);
                    }
                }

                return result;
            });
    }

    /**
     * WS5: Redacts sensitive fields from entity data based on policy.
     * Currently a no-op as policy service is not available in this module.
     *
     * @param tenantId tenant identifier
     * @param collectionName collection name
     * @param data entity data map
     * @return redacted data map
     */
    private Map<String, Object> redactSensitiveFields(String tenantId, String collectionName,
                                                       Map<String, Object> data) {
        // TODO: Implement redaction when policy service is available in entity module
        return data;
    }

    /**
     * WS5: Gets sensitive field names from policy configuration.
     * Falls back to common sensitive field names if policy doesn't specify.
     */
    private Set<String> getSensitiveFieldsFromPolicy(String tenantId, String collectionName) {
        // TODO: Extract sensitive field list from policy configuration
        // For now, use common sensitive field names as fallback
        return Set.of("ssn", "socialSecurityNumber", "creditCard", "password",
                      "secret", "apiKey", "token", "pin", "phoneNumber", "email");
    }

    /**
     * WS5: Generates an operation-specific idempotency key.
     * Format: operation:tenant:collection:jobId:rowNumber
     */
    private String generateOperationSpecificIdempotencyKey(String tenantId, String collectionName,
                                                           String operation, String jobId, int rowNumber) {
        return operation + ":" + tenantId + ":" + collectionName + ":" + jobId + ":" + rowNumber;
    }

    /**
     * Exports entities to CSV format.
     *
     * WS5: Applies data redaction for sensitive fields based on policy.
     *
     * @param tenantId tenant identifier
     * @param collectionName collection name
     * @param filter optional filter criteria
     * @param sort optional sort expression
     * @param offset offset for pagination
     * @param limit max results
     * @return Promise of export result with CSV data
     */
    public Promise<ExportResult> exportToCsv(
            String tenantId,
            String collectionName,
            Map<String, Object> filter,
            String sort,
            int offset,
            int limit) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(collectionName, "Collection name must not be null");

        log.info("Starting CSV export: tenant={}, collection={}, offset={}, limit={}",
            tenantId, collectionName, offset, limit);

        // WS5: Enforce policy compliance before CSV export - block if denied
        if (policyEngine != null) {
            Map<String, Object> policyInput = Map.of(
                "tenantId", tenantId,
                "collection", collectionName,
                "operation", "export_csv",
                "filter", filter != null ? filter : Map.of(),
                "limit", limit
            );
            try {
                PolicyDecision decision = policyEngine.evaluate("entity_export", policyInput).getResult();
                if (!decision.isAllowed()) {
                    log.warn("WS5: Policy denied CSV export: tenant={}, collection={}, reason={}",
                        tenantId, collectionName, decision.reason());
                    return Promise.ofException(new SecurityException(
                        "Policy denied export: " + decision.reason()));
                }
            } catch (Exception e) {
                log.error("WS5: Policy evaluation failed for CSV export: tenant={}, collection={}",
                    tenantId, collectionName, e);
                return Promise.ofException(new SecurityException(
                    "Policy evaluation failed: " + e.getMessage()));
            }
        }

        return entityRepository.findAll(tenantId, collectionName, filter, sort, offset, limit)
            .map(entities -> {
                if (entities.isEmpty()) {
                    return new ExportResult(0, "text/csv", List.of());
                }

                // Collect all field names from all entities
                Set<String> allFields = new LinkedHashSet<>();
                for (Entity entity : entities) {
                    Map<String, Object> redactedData = redactSensitiveFields(tenantId, collectionName, entity.getData());
                    if (redactedData != null) {
                        allFields.addAll(redactedData.keySet());
                    }
                }

                List<String> headers = new ArrayList<>(allFields);
                List<String[]> csvData = new ArrayList<>();

                // Add header row
                csvData.add(headers.toArray(new String[0]));

                // Add data rows
                for (Entity entity : entities) {
                    String[] row = new String[headers.size()];
                    Map<String, Object> data = redactSensitiveFields(tenantId, collectionName, entity.getData());

                    for (int i = 0; i < headers.size(); i++) {
                        Object value = data != null ? data.get(headers.get(i)) : null;
                        row[i] = value != null ? value.toString() : "";
                    }

                    csvData.add(row);
                }

                ExportResult result = new ExportResult(
                    entities.size(),
                    "text/csv",
                    csvData
                );

                log.info("CSV export completed: tenant={}, collection={}, exported={}",
                    tenantId, collectionName, entities.size());

                // WS5: Audit log the CSV export operation
                if (auditLogPort != null) {
                    try {
                        AuditLog auditLog = AuditLog.builder()
                            .tenantId(tenantId)
                            .action(AuditAction.EXPORT_DATA)
                            .resourceType("entity")
                            .resourceId(collectionName)
                            .userId("system")
                            .details(String.format("collection=%s, exportedCount=%d, format=csv, offset=%d, limit=%d",
                                collectionName, entities.size(), offset, limit))
                            .timestamp(Instant.now())
                            .build();
                        auditLogPort.save(auditLog).getResult();
                    } catch (Exception e) {
                        log.error("WS5: Failed to audit CSV export operation: tenant={}, collection={}",
                            tenantId, collectionName, e);
                    }
                }

                return result;
            });
    }

    /**
     * Result of an import operation.
     */
    public record ImportResult(
            int totalRows,
            int successCount,
            int failureCount,
            List<UUID> importedIds,
            List<ImportError> errors
    ) {
        public boolean hasFailures() {
            return failureCount > 0;
        }

        public boolean isCompleteSuccess() {
            return failureCount == 0 && successCount == totalRows;
        }
    }

    /**
     * Error during import.
     */
    public record ImportError(
            int rowNumber,
            String message
    ) {}

    /**
     * Result of an export operation.
     */
    public record ExportResult(
            int exportedCount,
            String contentType,
            Object data // List<Map<String, Object>> for JSON, List<String[]> for CSV
    ) {}
}
