package com.ghatana.appplatform.audit;

import com.ghatana.appplatform.audit.domain.AuditEntry;
import com.ghatana.appplatform.audit.port.AuditTrailStore;
import com.ghatana.kernel.audit.CrossScopeAuditService;
import com.ghatana.kernel.audit.CrossScopeAuditService.AuditEventStore;
import com.ghatana.kernel.audit.CrossScopeAuditService.ScopeAuditRecord;
import com.ghatana.kernel.policy.ClassificationDescriptor;
import com.ghatana.kernel.scope.ScopeDescriptor;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Adapter that bridges AppPlatform's sophisticated audit system to the canonical kernel audit contract.
 *
 * <p>Per KERNEL_APP_PLATFORM_CONVERGENCE_ADR, AppPlatform must implement canonical kernel contracts
 * rather than creating parallel abstractions. This adapter wraps AppPlatform's {@link AuditTrailStore}
 * to provide the {@link AuditEventStore} interface required by {@link CrossScopeAuditService}.</p>
 *
 * <p>Translation mapping:</p>
 * <ul>
 *   <li>ScopeAuditRecord → AuditEntry (with enhanced metadata)</li>
 *   <li>Canonical scope/classification → AppPlatform audit metadata</li>
 *   <li>Policy-driven retention → AppPlatform retention policies</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Adapter bridging AppPlatform audit to canonical kernel audit contract
 * @doc.layer app-platform
 * @doc.pattern Adapter
 */
public class CanonicalAuditAdapter implements AuditEventStore {

    private final AuditTrailStore appPlatformStore;

    /**
     * Creates the canonical audit adapter.
     *
     * @param appPlatformStore AppPlatform's sophisticated audit store implementation
     */
    public CanonicalAuditAdapter(AuditTrailStore appPlatformStore) {
        this.appPlatformStore = appPlatformStore;
    }

    @Override
    public Promise<Void> store(ScopeAuditRecord record) {
        // Translate canonical audit record to AppPlatform audit entry
        AuditEntry appPlatformEntry = translateToAppPlatformAudit(record);
        
        // Store using AppPlatform's audit trail store
        return appPlatformStore.log(appPlatformEntry)
                .map(receipt -> {
                    // Receipt contains hash chain proof - we don't need it for canonical interface
                    return null;
                });
    }

    @Override
    public Promise<Set<ScopeAuditRecord>> query(Instant startDate, Instant endDate,
                                                ScopeDescriptor sourceScope,
                                                ScopeDescriptor targetScope) {
        // AppPlatform's audit store doesn't support the canonical query interface directly
        // For now, return empty set - this would need to be implemented in AppPlatform
        // to support scope-based queries
        return Promise.of(Set.of());
    }

    /**
     * Translates a canonical ScopeAuditRecord to AppPlatform's AuditEntry format.
     */
    private AuditEntry translateToAppPlatformAudit(ScopeAuditRecord record) {
        // Build AppPlatform audit entry from canonical record
        return AuditEntry.builder()
                .id(UUID.randomUUID().toString()) // Generate new ID for AppPlatform
                .tenantId(record.getTenantId())
                .timestamp(record.getTimestamp())
                .eventType(record.getEventType())
                .actor(createActor(record))
                .resource(createResource(record))
                .outcome(AuditEntry.Outcome.SUCCESS) // Assume success for canonical audit
                .metadata(enhanceMetadata(record))
                .build();
    }

    private AuditEntry.Actor createActor(ScopeAuditRecord record) {
        return AuditEntry.Actor.builder()
                .id(record.getUserId())
                .type(AuditEntry.ActorType.USER)
                .sourceScope(record.getSourceScope().getScopeId())
                .build();
    }

    private AuditEntry.Resource createResource(ScopeAuditRecord record) {
        return AuditEntry.Resource.builder()
                .type(record.getTargetScope().getScopeType().name().toLowerCase())
                .id(record.getTargetScope().getScopeId())
                .capabilityId(record.getCapabilityId())
                .build();
    }

    private java.util.Map<String, Object> enhanceMetadata(ScopeAuditRecord record) {
        java.util.Map<String, Object> metadata = new java.util.HashMap<>(record.getMetadata());
        
        // Add canonical scope and classification metadata
        metadata.put("canonical.sourceScope", record.getSourceScope());
        metadata.put("canonical.targetScope", record.getTargetScope());
        metadata.put("canonical.classification", record.getClassification());
        metadata.put("canonical.retentionYears", record.getRetentionYears());
        metadata.put("canonical.storageTier", record.getStorageTier());
        metadata.put("canonical.signature", record.getSignature());
        
        return metadata;
    }
}
