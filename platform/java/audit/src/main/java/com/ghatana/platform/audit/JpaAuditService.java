package com.ghatana.platform.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.promise.Promise;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * JPA-backed durable implementation of {@link AuditService} and {@link AuditQueryService}.
 *
 * <p>Persists audit events via a JPA {@link EntityManager}, mapping between the domain
 * {@link AuditEvent} value object and the {@link AuditEventEntity} persistence entity.
 * The {@code details} map is stored as serialized JSON in a TEXT column.</p>
 *
 * <p>Implements hash-chain integrity verification to ensure audit logs cannot be tampered with.
 * Each event includes a hash of the previous event, creating an append-only chain.</p>
 *
 * <p>All mutating operations run inside the caller-supplied transaction. Read operations
 * work in read-only mode.</p>
 *
 * <p><b>Wiring:</b>
 * <pre>{@code
 * EntityManager em = ...; // from JPA / platform:java:database configuration
 * JpaAuditService auditService = new JpaAuditService(em, new ObjectMapper());
 * }</pre>
 * </p>
 *
 * @see AuditService
 * @see AuditQueryService
 * @see AuditEventEntity
 * @see InMemoryAuditQueryService
 * @doc.type class
 * @doc.purpose JPA-backed durable audit service for production use with hash-chain integrity
 * @doc.layer infrastructure
 * @doc.pattern Service, Repository
 */
public class JpaAuditService implements AuditService, AuditQueryService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;
    private final AuditIntegrityService integrityService;

    public JpaAuditService(EntityManager entityManager, ObjectMapper objectMapper) {
        this(entityManager, objectMapper, new AuditIntegrityService());
    }

    public JpaAuditService(EntityManager entityManager, ObjectMapper objectMapper, AuditIntegrityService integrityService) {
        this.entityManager = Objects.requireNonNull(entityManager, "EntityManager cannot be null");
        this.objectMapper  = Objects.requireNonNull(objectMapper,  "ObjectMapper cannot be null");
        this.integrityService = Objects.requireNonNull(integrityService, "IntegrityService cannot be null");
    }

    // -------------------------------------------------------------------------
    // AuditService — write side
    // -------------------------------------------------------------------------

    @Override
    public Promise<Void> record(AuditEvent event) {
        Objects.requireNonNull(event, "event cannot be null");
        try {
            // Get the previous event's hash for chain integrity
            String previousHash = getLatestChainHash(event.getTenantId());
            AuditEventEntity entity = toEntity(event, previousHash);
            entityManager.persist(entity);
            return Promise.complete();
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }

    /**
     * Gets the chain hash of the most recent audit event for a tenant.
     *
     * @param tenantId the tenant ID
     * @return the chain hash, or null if no events exist
     */
    private String getLatestChainHash(String tenantId) {
        try {
            AuditEventEntity latest = entityManager.createQuery(
                    "SELECT e FROM AuditEventEntity e WHERE e.tenantId = :tid ORDER BY e.timestamp DESC",
                    AuditEventEntity.class)
                    .setParameter("tid", tenantId)
                    .setMaxResults(1)
                    .getSingleResult();
            return latest != null ? latest.getChainHash() : null;
        } catch (Exception e) {
            return null; // No events exist or query failed
        }
    }

    // -------------------------------------------------------------------------
    // AuditQueryService — read side
    // -------------------------------------------------------------------------

    @Override
    public Promise<List<AuditEvent>> findByTenantId(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        return Promise.of(toEvents(entityManager.createQuery(
                "SELECT e FROM AuditEventEntity e WHERE e.tenantId = :tid ORDER BY e.timestamp DESC",
                AuditEventEntity.class)
                .setParameter("tid", tenantId)
                .getResultList()));
    }

    @Override
    public Promise<List<AuditEvent>> findByTenantId(String tenantId, int offset, int limit) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        return Promise.of(toEvents(entityManager.createQuery(
                "SELECT e FROM AuditEventEntity e WHERE e.tenantId = :tid ORDER BY e.timestamp DESC",
                AuditEventEntity.class)
                .setParameter("tid", tenantId)
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList()));
    }

    @Override
    public Promise<List<AuditEvent>> findByResource(String tenantId, String resourceType, String resourceId) {
        return Promise.of(toEvents(entityManager.createQuery(
                "SELECT e FROM AuditEventEntity e WHERE e.tenantId = :tid"
                        + " AND e.resourceType = :rt AND e.resourceId = :rid ORDER BY e.timestamp DESC",
                AuditEventEntity.class)
                .setParameter("tid", tenantId)
                .setParameter("rt",  resourceType)
                .setParameter("rid", resourceId)
                .getResultList()));
    }

    @Override
    public Promise<List<AuditEvent>> findByPrincipal(String tenantId, String principal) {
        return Promise.of(toEvents(entityManager.createQuery(
                "SELECT e FROM AuditEventEntity e WHERE e.tenantId = :tid AND e.principal = :p ORDER BY e.timestamp DESC",
                AuditEventEntity.class)
                .setParameter("tid", tenantId)
                .setParameter("p",   principal)
                .getResultList()));
    }

    @Override
    public Promise<List<AuditEvent>> findByEventType(String tenantId, String eventType) {
        return Promise.of(toEvents(entityManager.createQuery(
                "SELECT e FROM AuditEventEntity e WHERE e.tenantId = :tid AND e.eventType = :et ORDER BY e.timestamp DESC",
                AuditEventEntity.class)
                .setParameter("tid", tenantId)
                .setParameter("et",  eventType)
                .getResultList()));
    }

    @Override
    public Promise<List<AuditEvent>> findByTimeRange(String tenantId, Instant from, Instant to) {
        return Promise.of(toEvents(entityManager.createQuery(
                "SELECT e FROM AuditEventEntity e WHERE e.tenantId = :tid"
                        + " AND e.timestamp >= :from AND e.timestamp <= :to ORDER BY e.timestamp DESC",
                AuditEventEntity.class)
                .setParameter("tid",  tenantId)
                .setParameter("from", from)
                .setParameter("to",   to)
                .getResultList()));
    }

    @Override
    public Promise<Optional<AuditEvent>> findById(String tenantId, String eventId) {
        AuditEventEntity found = entityManager.find(AuditEventEntity.class, eventId);
        if (found != null && !found.getTenantId().equals(tenantId)) {
            return Promise.of(Optional.empty());
        }
        return Promise.of(found == null ? Optional.empty() : Optional.of(toDomain(found)));
    }

    @Override
    public Promise<Long> countByTenantId(String tenantId) {
        TypedQuery<Long> q = entityManager.createQuery(
                "SELECT COUNT(e) FROM AuditEventEntity e WHERE e.tenantId = :tid", Long.class);
        q.setParameter("tid", tenantId);
        return Promise.of(q.getSingleResult());
    }

    @Override
    public Promise<List<AuditEvent>> search(String tenantId, AuditSearchCriteria criteria) {
        StringBuilder jpql = new StringBuilder(
                "SELECT e FROM AuditEventEntity e WHERE e.tenantId = :tid");
        if (criteria.resourceType() != null) jpql.append(" AND e.resourceType = :rt");
        if (criteria.resourceId()   != null) jpql.append(" AND e.resourceId = :rid");
        if (criteria.principal()    != null) jpql.append(" AND e.principal = :p");
        if (criteria.eventType()    != null) jpql.append(" AND e.eventType = :et");
        if (criteria.fromDate()     != null) jpql.append(" AND e.timestamp >= :from");
        if (criteria.toDate()       != null) jpql.append(" AND e.timestamp <= :to");
        if (criteria.success()      != null) jpql.append(" AND e.success = :s");
        jpql.append(" ORDER BY e.timestamp DESC");

        TypedQuery<AuditEventEntity> q = entityManager.createQuery(jpql.toString(), AuditEventEntity.class);
        q.setParameter("tid", tenantId);
        if (criteria.resourceType() != null) q.setParameter("rt",   criteria.resourceType());
        if (criteria.resourceId()   != null) q.setParameter("rid",  criteria.resourceId());
        if (criteria.principal()    != null) q.setParameter("p",    criteria.principal());
        if (criteria.eventType()    != null) q.setParameter("et",   criteria.eventType());
        if (criteria.fromDate()     != null) q.setParameter("from", criteria.fromDate());
        if (criteria.toDate()       != null) q.setParameter("to",   criteria.toDate());
        if (criteria.success()      != null) q.setParameter("s",    criteria.success());
        q.setFirstResult(criteria.offset());
        q.setMaxResults(criteria.limit());
        return Promise.of(toEvents(q.getResultList()));
    }

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    private AuditEventEntity toEntity(AuditEvent event, String previousHash) {
        String detailsJson;
        try {
            detailsJson = objectMapper.writeValueAsString(event.getDetails());
        } catch (JsonProcessingException e) {
            detailsJson = "{}";
        }
        
        // Create entity without hash first
        AuditEventEntity entity = new AuditEventEntity(
                event.getId(), event.getTenantId(), event.getEventType(),
                event.getPrincipal(), event.getResourceType(), event.getResourceId(),
                event.getSuccess(), event.getTimestamp(), detailsJson, previousHash, null);
        
        // Compute and set the chain hash
        String chainHash = integrityService.computeChainHash(entity, previousHash);
        return new AuditEventEntity(
                event.getId(), event.getTenantId(), event.getEventType(),
                event.getPrincipal(), event.getResourceType(), event.getResourceId(),
                event.getSuccess(), event.getTimestamp(), detailsJson, previousHash, chainHash);
    }

    private AuditEvent toDomain(AuditEventEntity entity) {
        Map<String, Object> details;
        try {
            details = entity.getDetailsJson() != null
                    ? objectMapper.readValue(entity.getDetailsJson(), MAP_TYPE)
                    : Collections.emptyMap();
        } catch (JsonProcessingException e) {
            details = Collections.emptyMap();
        }
        return AuditEvent.builder()
                .id(entity.getId())
                .tenantId(entity.getTenantId())
                .eventType(entity.getEventType())
                .principal(entity.getPrincipal())
                .resourceType(entity.getResourceType())
                .resourceId(entity.getResourceId())
                .success(entity.getSuccess())
                .timestamp(entity.getTimestamp())
                .details(details)
                .build();
    }

    private List<AuditEvent> toEvents(List<AuditEventEntity> entities) {
        return entities.stream().map(this::toDomain).toList();
    }
}
