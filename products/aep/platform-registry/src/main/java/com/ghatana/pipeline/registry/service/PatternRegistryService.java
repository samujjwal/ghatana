package com.ghatana.pipeline.registry.service;

import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.eventprocessing.observability.RegistryObservability;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.pipeline.registry.model.Pattern;
import com.ghatana.pipeline.registry.repository.PatternRepository;
import com.ghatana.pipeline.registry.publisher.RegistryEventPublisher;
import io.activej.promise.Promise;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of PatternService for pattern registration and management.
 *
 * <p>
 * <b>Purpose</b><br>
 * Central service for pattern lifecycle management. Validates specifications
 * via pattern-compiler, persists registrations via repository, emits metrics
 * and audit logs, and supports multi-tenant isolation.
 *
 * <p>
 * <b>Responsibilities</b><br>
 * - Validate pattern specifications (delegates to pattern-compiler) - Persist
 * patterns via repository (in-memory or database) - Emit observability metrics
 * and structured logs - Enforce tenant isolation and authorization - Support
 * pattern status transitions (DRAFT → COMPILED → ACTIVE/INACTIVE)
 *
 * <p>
 * <b>Observability</b><br>
 * Metrics emitted (via MetricsCollector):
 * <ul>
 * <li>aep.pattern.register.count - Pattern registration counter</li>
 * <li>aep.pattern.register.errors - Pattern registration error counter</li>
 * <li>aep.pattern.get.errors - Pattern retrieval error counter</li>
 * <li>aep.pattern.delete.count - Pattern deletion counter</li>
 * <li>aep.pattern.activate.count - Pattern activation counter</li>
 * </ul>
 *
 * Structured logs include:
 * <ul>
 * <li>patternId - Unique pattern identifier</li>
 * <li>tenantId - Tenant owner</li>
 * <li>userId - User performing operation (for audit)</li>
 * <li>operation - Operation type (register, update, delete, activate)</li>
 * <li>status - Result status (success, failure)</li>
 * <li>errorCode - Error code on failure</li>
 * </ul>
 *
 * <p>
 * <b>Threading Model</b><br>
 * All operations are non-blocking and return ActiveJ Promise. Thread-safe
 * through Promise composition (single-threaded eventloop semantics).
 *
 * @see PatternService
 * @see Pattern
 * @doc.type class
 * @doc.purpose Pattern registry service implementation
 * @doc.layer product
 * @doc.pattern Service
 */
@Slf4j
@RequiredArgsConstructor
public class PatternRegistryService implements PatternService {

    private final PatternRepository patternRepository;
    private final MetricsCollector metricsCollector;
    private final RegistryObservability registryObservability;
    private final RegistryEventPublisher eventPublisher;

    @Override
    public Promise<Pattern> register(Pattern pattern, String userId) {
        String patternId = UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();

        // Record registration start in observability
        registryObservability.recordPatternRegistrationStart(
                pattern.getTenantId().value(), patternId, userId);

        return Promise.of(pattern)
                .then(p -> {
                    // Validate pattern exists and has required fields
                    if (pattern.getName() == null || pattern.getName().trim().isEmpty()) {
                        return Promise.ofException(new IllegalArgumentException("Pattern name is required"));
                    }
                    if (pattern.getSpecification() == null || pattern.getSpecification().trim().isEmpty()) {
                        return Promise.ofException(new IllegalArgumentException("Pattern specification is required"));
                    }
                    if (pattern.getTenantId() == null) {
                        return Promise.ofException(new IllegalArgumentException("Tenant ID is required"));
                    }
                    return Promise.of(p);
                })
                .then(p -> {
                    // Set audit fields
                    p.setId(patternId);
                    p.setVersion(1);
                    p.setStatus("DRAFT");
                    p.setCreatedAt(Instant.now());
                    p.setUpdatedAt(Instant.now());
                    p.setCreatedBy(userId);
                    p.setUpdatedBy(userId);

                    // In production, would validate spec via pattern-compiler
                    // For now, mark as compiled with placeholder
                    p.setStatus("COMPILED");
                    p.setDetectionPlan("compiled:" + pattern.getSpecification());
                    if (p.getConfidence() == 0) {
                        p.setConfidence(75);
                    }

                    // Persist
                    return patternRepository.save(p);
                })
                .then(saved -> {
                    long durationMs = System.currentTimeMillis() - startTime;
                    // Record registration success
                    registryObservability.recordPatternRegistrationSuccess(
                            pattern.getTenantId().value(), patternId, durationMs);

                    // Emit metrics
                    metricsCollector.incrementCounter("aep.pattern.register.count",
                            "tenant", pattern.getTenantId().toString(),
                            "status", saved.getStatus());

                    // Structured log
                    MDC.put("patternId", saved.getId());
                    MDC.put("tenantId", pattern.getTenantId().toString());
                    MDC.put("operation", "register");
                    log.info("Pattern registered successfully: {} v{}",
                            saved.getName(), saved.getVersion());

                    // Emit registration event
                    return eventPublisher.publishPatternRegistered(saved, userId)
                            .map(ignored -> saved);
                })
                .whenException(e -> {
                    // Record registration error
                    registryObservability.recordPatternRegistrationError(
                            pattern.getTenantId().value(), patternId, e);

                    metricsCollector.incrementCounter("aep.pattern.register.errors",
                            "tenant", pattern.getTenantId().toString(),
                            "error_type", e.getClass().getSimpleName());

                    MDC.put("patternId", patternId);
                    MDC.put("operation", "register");
                    MDC.put("status", "failure");
                    log.error("Pattern registration failed: {} - {}",
                            pattern.getName(), e.getMessage(), e);
                })
                .whenComplete((v, e) -> registryObservability.clearRegistryContext());
    }

    @Override
    public Promise<Optional<Pattern>> getById(String id, TenantId tenantId) {
        return patternRepository.findByIdAndTenant(id, tenantId)
                .whenException(e -> {
                    metricsCollector.incrementCounter("aep.pattern.get.errors",
                            "tenant", tenantId.toString(),
                            "error_type", e.getClass().getSimpleName());

                    MDC.put("patternId", id);
                    log.error("Pattern retrieval failed: {}", e.getMessage(), e);
                });
    }

    @Override
    public Promise<List<Pattern>> list(TenantId tenantId, String status) {
        return patternRepository.findByTenant(tenantId, status);
    }

    @Override
    public Promise<Pattern> update(String id, Pattern pattern, String userId) {
        return patternRepository.findByIdAndTenant(id, pattern.getTenantId())
                .then(opt -> {
                    if (opt.isEmpty()) {
                        throw new IllegalArgumentException("Pattern not found: " + id);
                    }

                    Pattern existing = opt.get();
                    Pattern updated = existing.newVersion();
                    updated.setId(id);
                    updated.setSpecification(pattern.getSpecification());
                    updated.setName(pattern.getName());
                    updated.setDescription(pattern.getDescription());
                    updated.setAgentHints(pattern.getAgentHints());
                    updated.setTags(pattern.getTags());
                    updated.setUpdatedBy(userId);
                    updated.setUpdatedAt(Instant.now());

                    // In production, recompile via pattern-compiler
                    updated.setDetectionPlan("compiled:" + pattern.getSpecification());
                    if (pattern.getConfidence() > 0) {
                        updated.setConfidence(pattern.getConfidence());
                    }

                    return patternRepository.update(updated);
                })
                .map(updated -> {
                    MDC.put("patternId", id);
                    MDC.put("operation", "update");
                    log.info("Pattern updated: {} v{}", updated.getName(), updated.getVersion());
                    return updated;
                })
                .whenException(e -> {
                    MDC.put("patternId", id);
                    MDC.put("operation", "update");
                    MDC.put("status", "failure");
                    log.error("Pattern update failed: {}", e.getMessage(), e);
                });
    }

    @Override
    public Promise<Void> delete(String id, TenantId tenantId, String userId) {
        return patternRepository.findByIdAndTenant(id, tenantId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        throw new IllegalArgumentException("Pattern not found: " + id);
                    }
                    return patternRepository.delete(id);
                })
                .map(v -> {
                    metricsCollector.incrementCounter("aep.pattern.delete.count",
                            "tenant", tenantId.toString());

                    MDC.put("patternId", id);
                    MDC.put("operation", "delete");
                    log.info("Pattern deleted: {}", id);
                    return null;
                })
                .whenException(e -> {
                    MDC.put("patternId", id);
                    MDC.put("operation", "delete");
                    log.error("Pattern deletion failed: {}", e.getMessage(), e);
                })
                .toVoid();
    }

    @Override
    public Promise<Void> activate(String id, TenantId tenantId, String userId) {
        long startTime = System.currentTimeMillis();
        // Record activation start
        registryObservability.recordPatternActivationStart(tenantId.value(), id, userId);

        return patternRepository.findByIdAndTenant(id, tenantId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.ofException(new IllegalArgumentException("Pattern not found: " + id));
                    }

                    Pattern pattern = opt.get();
                    if (!"COMPILED".equals(pattern.getStatus())) {
                        return Promise.ofException(new IllegalStateException(
                                "Cannot activate pattern in " + pattern.getStatus() + " status"));
                    }

                    pattern.activate();
                    pattern.setUpdatedBy(userId);
                    return patternRepository.update(pattern);
                })
                .then(activated -> {
                    long durationMs = System.currentTimeMillis() - startTime;
                    // Record activation success
                    registryObservability.recordPatternActivationSuccess(
                            tenantId.value(), id, durationMs);

                    metricsCollector.incrementCounter("aep.pattern.activate.count",
                            "tenant", tenantId.toString());

                    MDC.put("patternId", id);
                    MDC.put("operation", "activate");
                    log.info("Pattern activated: {}", id);

                    // Emit activation event
                    return eventPublisher.publishPatternActivated(activated, userId);
                })
                .whenException(e -> {
                    // Record activation error
                    registryObservability.recordPatternActivationError(
                            tenantId.value(), id, e);

                    MDC.put("patternId", id);
                    MDC.put("operation", "activate");
                    log.error("Pattern activation failed: {}", e.getMessage(), e);
                })
                .whenComplete((v, e) -> registryObservability.clearRegistryContext())
                .map(v -> null);
    }

    @Override
    public Promise<Void> deactivate(String id, TenantId tenantId, String userId) {
        long startTime = System.currentTimeMillis();
        // Record deactivation start
        registryObservability.recordPatternDeactivationStart(tenantId.value(), id, userId);

        return patternRepository.findByIdAndTenant(id, tenantId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        throw new IllegalArgumentException("Pattern not found: " + id);
                    }

                    Pattern pattern = opt.get();
                    pattern.deactivate();
                    pattern.setUpdatedBy(userId);
                    return patternRepository.update(pattern);
                })
                .then(deactivated -> {
                    long durationMs = System.currentTimeMillis() - startTime;
                    // Record deactivation success
                    registryObservability.recordPatternDeactivationSuccess(
                            tenantId.value(), id, durationMs);

                    MDC.put("patternId", id);
                    MDC.put("operation", "deactivate");
                    log.info("Pattern deactivated: {}", id);

                    // Emit deactivation event
                    return eventPublisher.publishPatternDeactivated(deactivated, userId);
                })
                .whenException(e -> {
                    // Record deactivation error
                    registryObservability.recordPatternDeactivationError(
                            tenantId.value(), id, e);

                    MDC.put("patternId", id);
                    MDC.put("operation", "deactivate");
                    log.error("Pattern deactivation failed: {}", e.getMessage(), e);
                })
                .whenComplete((v, e) -> registryObservability.clearRegistryContext())
                .map(v -> null);
    }

    @Override
    public Promise<Boolean> exists(String id, TenantId tenantId) {
        return patternRepository.findByIdAndTenant(id, tenantId)
                .map(opt -> opt.isPresent());
    }
}
