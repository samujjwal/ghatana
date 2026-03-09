package com.ghatana.platform.audit;

import io.activej.promise.Promise;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * JPA-backed durable implementation of {@link AuditService} and {@link AuditQueryService}.
 *
 * <p>Replaces {@link InMemoryAuditQueryService} for production deployments where audit
 * records must survive application restarts and be queryable over time. Persists
 * {@link AuditEvent} records via a JPA {@link EntityManager}.</p>
 *
 * <p>All mutating operations run inside the caller-supplied transaction. Read operations
 * work outside a transaction (read-only).</p>
 *
 * <p><b>Wiring:</b> Bind this class in the DI module as the primary {@code AuditService}
 * and {@code AuditQueryService} implementation for production profiles:
 * <pre>{@code
 * EntityManager em = ...; // from JPA configuration
 * JpaAuditService auditService = new JpaAuditService(em);
 * }</pre>
 * </p>
 *
 * @see AuditService
 * @see AuditQueryService
 * @see InMemoryAuditQueryService
 * @doc.type class
 * @doc.purpose JPA-backed durable audit service for production use
 * @doc.layer infrastructure
 * @doc.pattern Service, Repository
 */
public class JpaAuditService implements AuditService, AuditQueryService {

    private final EntityManager entityManager;

    public JpaAuditService(EntityManager entityManager) {
        this.entityManager = Objects.requireNonNull(entityManager, "EntityManager cannot be null");
    }

    // -------------------------------------------------------------------------
    // AuditService — write side
    // -------------------------------------------------------------------------

    @Override
    public Promise<Void> record(AuditEvent event) {
        Objects.requireNonNull(event, "event cannot be null");
        try {
            entityManager.persist(event);
            return Promise.complete();
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }

    // -------------------------------------------------------------------------
    // AuditQueryService — read side
    // -------------------------------------------------------------------------

    @Override
    public Promise<List<AuditEvent>> findByTenantId(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        TypedQuery<AuditEvent> q = entityManager.createQuery(
                "SELECT e FROM AuditEvent e WHERE e.tenantId = :tid ORDER BY e.timestamp DESC",
                AuditEvent.class);
        q.setParameter("tid", tenantId);
        return Promise.of(q.getResultList());
    }

    @Override
    public Promise<List<AuditEvent>> findByTenantId(String tenantId, int offset, int limit) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        TypedQuery<AuditEvent> q = entityManager.createQuery(
                "SELECT e FROM AuditEvent e WHERE e.tenantId = :tid ORDER BY e.timestamp DESC",
                AuditEvent.class);
        q.setParameter("tid", tenantId);
        q.setFirstResult(offset);
        q.setMaxResults(limit);
        return Promise.of(q.getResultList());
    }

    @Override
    public Promise<List<AuditEvent>> findByResource(String tenantId, String resourceType, String resourceId) {
        TypedQuery<AuditEvent> q = entityManager.createQuery(
                "SELECT e FROM AuditEvent e WHERE e.tenantId = :tid"
                + " AND e.resourceType = :rt AND e.resourceId = :rid ORDER BY e.timestamp DESC",
                AuditEvent.class);
        q.setParameter("tid", tenantId);
        q.setParameter("rt", resourceType);
        q.setParameter("rid", resourceId);
        return Promise.of(q.getResultList());
    }

    @Override
    public Promise<List<AuditEvent>> findByPrincipal(String tenantId, String principal) {
        TypedQuery<AuditEvent> q = entityManager.createQuery(
                "SELECT e FROM AuditEvent e WHERE e.tenantId = :tid AND e.principal = :p ORDER BY e.timestamp DESC",
                AuditEvent.class);
        q.setParameter("tid", tenantId);
        q.setParameter("p", principal);
        return Promise.of(q.getResultList());
    }

    @Override
    public Promise<List<AuditEvent>> findByEventType(String tenantId, String eventType) {
        TypedQuery<AuditEvent> q = entityManager.createQuery(
                "SELECT e FROM AuditEvent e WHERE e.tenantId = :tid AND e.eventType = :et ORDER BY e.timestamp DESC",
                AuditEvent.class);
        q.setParameter("tid", tenantId);
        q.setParameter("et", eventType);
        return Promise.of(q.getResultList());
    }

    @Override
    public Promise<List<AuditEvent>> findByTimeRange(String tenantId, Instant from, Instant to) {
        TypedQuery<AuditEvent> q = entityManager.createQuery(
                "SELECT e FROM AuditEvent e WHERE e.tenantId = :tid"
                + " AND e.timestamp >= :from AND e.timestamp <= :to ORDER BY e.timestamp DESC",
                AuditEvent.class);
        q.setParameter("tid", tenantId);
        q.setParameter("from", from);
        q.setParameter("to", to);
        return Promise.of(q.getResultList());
    }

    @Override
    public Promise<Optional<AuditEvent>> findById(String tenantId, String eventId) {
        AuditEvent found = entityManager.find(AuditEvent.class, eventId);
        if (found != null && !found.getTenantId().equals(tenantId)) {
            return Promise.of(Optional.empty());
        }
        return Promise.of(Optional.ofNullable(found));
    }

    @Override
    public Promise<Long> countByTenantId(String tenantId) {
        TypedQuery<Long> q = entityManager.createQuery(
                "SELECT COUNT(e) FROM AuditEvent e WHERE e.tenantId = :tid", Long.class);
        q.setParameter("tid", tenantId);
        return Promise.of(q.getSingleResult());
    }

    @Override
    public Promise<List<AuditEvent>> search(String tenantId, AuditSearchCriteria criteria) {
        StringBuilder jpql = new StringBuilder(
                "SELECT e FROM AuditEvent e WHERE e.tenantId = :tid");
        if (criteria.resourceType() != null) jpql.append(" AND e.resourceType = :rt");
        if (criteria.resourceId()   != null) jpql.append(" AND e.resourceId = :rid");
        if (criteria.principal()    != null) jpql.append(" AND e.principal = :p");
        if (criteria.eventType()    != null) jpql.append(" AND e.eventType = :et");
        if (criteria.fromDate()     != null) jpql.append(" AND e.timestamp >= :from");
        if (criteria.toDate()       != null) jpql.append(" AND e.timestamp <= :to");
        if (criteria.success()      != null) jpql.append(" AND e.success = :s");
        jpql.append(" ORDER BY e.timestamp DESC");

        TypedQuery<AuditEvent> q = entityManager.createQuery(jpql.toString(), AuditEvent.class);
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
        return Promise.of(q.getResultList());
    }
}
