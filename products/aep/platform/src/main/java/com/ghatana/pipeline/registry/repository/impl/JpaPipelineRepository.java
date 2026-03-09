package com.ghatana.pipeline.registry.repository.impl;

import com.ghatana.platform.core.common.pagination.Page;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.pipeline.registry.model.Pipeline;
import com.ghatana.pipeline.registry.repository.PipelineRepository;
import io.activej.inject.annotation.Inject;
import io.activej.promise.Promise;
import lombok.extern.slf4j.Slf4j;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JPA implementation of PipelineRepository using core/database library.
 *
 * Leverages EntityManager for all database operations with centralized
 * transaction and connection management through core/database abstractions.
 *
 * @doc.type class
 * @doc.purpose Repository adapter for pipeline persistence
 * @doc.layer product
 * @doc.pattern Adapter
 */
@Slf4j
public class JpaPipelineRepository implements PipelineRepository {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final EntityManager entityManager;

    @Inject
    public JpaPipelineRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Promise<Optional<Pipeline>> findById(String id, TenantId tenantId) {
        try {
            Optional<Pipeline> result = entityManager
                    .createQuery(
                            "SELECT p FROM Pipeline p WHERE p.id = :id AND p.tenantId = :tenantId",
                            Pipeline.class)
                    .setParameter("id", id)
                    .setParameter("tenantId", tenantId)
                    .getResultList()
                    .stream()
                    .findFirst();
            return Promise.of(result);
        } catch (Exception e) {
            log.debug("Pipeline not found with id: {} and tenant: {}", id, tenantId, e);
            return Promise.of(Optional.empty());
        }
    }

    @Override
    public Promise<Optional<Pipeline>> findByNameAndVersion(String name, int version, TenantId tenantId) {
        try {
            Optional<Pipeline> result = entityManager
                    .createQuery(
                            "SELECT p FROM Pipeline p WHERE p.name = :name AND p.version = :version AND p.tenantId = :tenantId",
                            Pipeline.class)
                    .setParameter("name", name)
                    .setParameter("version", version)
                    .setParameter("tenantId", tenantId)
                    .getResultList()
                    .stream()
                    .findFirst();
            return Promise.of(result);
        } catch (Exception e) {
            log.debug("Pipeline not found with name: {}, version: {} and tenant: {}", name, version, tenantId, e);
            return Promise.of(Optional.empty());
        }
    }

    @Override
    public Promise<Optional<Pipeline>> findLatestVersion(String name, TenantId tenantId) {
        try {
            Optional<Pipeline> result = entityManager
                    .createQuery(
                            "SELECT p FROM Pipeline p WHERE p.name = :name AND p.tenantId = :tenantId AND p.active = true ORDER BY p.version DESC",
                            Pipeline.class)
                    .setParameter("name", name)
                    .setParameter("tenantId", tenantId)
                    .setMaxResults(1)
                    .getResultList()
                    .stream()
                    .findFirst();
            return Promise.of(result);
        } catch (Exception e) {
            log.debug("No active pipeline found with name: {} and tenant: {}", name, tenantId, e);
            return Promise.of(Optional.empty());
        }
    }

    @Override
    public Promise<List<Pipeline>> findAllVersions(String name, TenantId tenantId) {
        try {
            List<Pipeline> result = entityManager
                    .createQuery(
                            "SELECT p FROM Pipeline p WHERE p.name = :name AND p.tenantId = :tenantId ORDER BY p.version DESC",
                            Pipeline.class)
                    .setParameter("name", name)
                    .setParameter("tenantId", tenantId)
                    .getResultList();
            return Promise.of(result);
        } catch (Exception e) {
            log.error("Error finding all versions of pipeline: {}", name, e);
            return Promise.ofException(new RuntimeException("Failed to find all pipeline versions", e));
        }
    }

    @Override
    public Promise<Page<Pipeline>> findAll(TenantId tenantId, String nameFilter, Boolean activeOnly, int page, int size) {
        try {
            var cb = entityManager.getCriteriaBuilder();

            // Validate and adjust pagination parameters
            int pageSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
            int pageNumber = Math.max(0, page - 1); // Convert to 0-based

            // Count query
            var countQuery = cb.createQuery(Long.class);
            var countRoot = countQuery.from(Pipeline.class);
            countQuery.select(cb.count(countRoot));

            // Main query
            var cq = cb.createQuery(Pipeline.class);
            Root<Pipeline> root = cq.from(Pipeline.class);

            // Build predicates
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));

            List<Predicate> countPredicates = new ArrayList<>();
            countPredicates.add(cb.equal(countRoot.get("tenantId"), tenantId));

            if (nameFilter != null && !nameFilter.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + nameFilter.toLowerCase() + "%"));
                countPredicates.add(cb.like(cb.lower(countRoot.get("name")), "%" + nameFilter.toLowerCase() + "%"));
            }

            if (activeOnly != null && activeOnly) {
                predicates.add(cb.isTrue(root.get("active")));
                countPredicates.add(cb.isTrue(countRoot.get("active")));
            }

            // Apply predicates
            countQuery.where(countPredicates.toArray(new Predicate[0]));
            cq.where(predicates.toArray(new Predicate[0]));

            // Order by name and version
            cq.orderBy(cb.asc(root.get("name")), cb.desc(root.get("version")));

            // Execute queries
            long total = entityManager.createQuery(countQuery).getSingleResult();
            List<Pipeline> results = entityManager.createQuery(cq)
                    .setFirstResult(pageNumber * pageSize)
                    .setMaxResults(pageSize)
                    .getResultList();

            // Create Page with correct parameter order: content, pageSize, pageNumber, totalElements
            Page<Pipeline> resultPage = new Page<>(results, pageSize, page, total);
            return Promise.of(resultPage);
        } catch (Exception e) {
            log.error("Error finding all pipelines", e);
            return Promise.ofException(new RuntimeException("Failed to find all pipelines", e));
        }
    }

    @Override
    public Promise<Pipeline> save(Pipeline pipeline) {
        try {
            Pipeline saved;
            if (pipeline.getId() == null) {
                entityManager.persist(pipeline);
                saved = pipeline;
            } else {
                saved = entityManager.merge(pipeline);
            }
            return Promise.of(saved);
        } catch (Exception e) {
            log.error("Error saving pipeline: {}", pipeline.getId(), e);
            return Promise.ofException(new RuntimeException("Failed to save pipeline", e));
        }
    }

    @Override
    public Promise<Void> delete(String id, TenantId tenantId, boolean softDelete, String deletedBy) {
        return findById(id, tenantId).then(maybePipeline -> {
            if (maybePipeline.isEmpty()) {
                return Promise.of(null);
            }

            Pipeline pipeline = maybePipeline.get();
            if (softDelete) {
                pipeline.setActive(false);
                pipeline.setUpdatedAt(Instant.now());
                pipeline.setUpdatedBy(deletedBy);
                return save(pipeline).map(p -> null);
            } else {
                try {
                    entityManager.remove(entityManager.contains(pipeline) ? pipeline : entityManager.merge(pipeline));
                    return Promise.of(null);
                } catch (Exception e) {
                    log.error("Error deleting pipeline: {}", id, e);
                    return Promise.ofException(new RuntimeException("Failed to delete pipeline", e));
                }
            }
        });
    }

    @Override
    public Promise<Integer> nextVersion(String name, TenantId tenantId) {
        return findLatestVersion(name, tenantId)
                .map(maybePipeline -> maybePipeline.map(Pipeline::getVersion).orElse(0) + 1);
    }

    @Override
    public Promise<Boolean> exists(String id, TenantId tenantId) {
        try {
            boolean result = entityManager
                    .createQuery(
                            "SELECT COUNT(p) > 0 FROM Pipeline p WHERE p.id = :id AND p.tenantId = :tenantId",
                            Boolean.class)
                    .setParameter("id", id)
                    .setParameter("tenantId", tenantId)
                    .getSingleResult();
            return Promise.of(result);
        } catch (Exception e) {
            log.error("Error checking if pipeline exists: {}", id, e);
            return Promise.ofException(new RuntimeException("Failed to check if pipeline exists", e));
        }
    }

    @Override
    public Promise<Long> countStructuredConfigPipelines(TenantId tenantId) {
        try {
            long count = entityManager
                    .createQuery(
                            "SELECT COUNT(p) FROM Pipeline p WHERE p.tenantId = :tenantId AND p.structuredConfig IS NOT NULL",
                            Long.class)
                    .setParameter("tenantId", tenantId)
                    .getSingleResult();
            return Promise.of(count);
        } catch (Exception e) {
            log.error("Error counting structured config pipelines", e);
            return Promise.ofException(new RuntimeException("Failed to count structured config pipelines", e));
        }
    }

    @Override
    public Promise<Long> countLegacyConfigPipelines(TenantId tenantId) {
        try {
            long count = entityManager
                    .createQuery(
                            "SELECT COUNT(p) FROM Pipeline p WHERE p.tenantId = :tenantId AND p.structuredConfig IS NULL AND p.config IS NOT NULL",
                            Long.class)
                    .setParameter("tenantId", tenantId)
                    .getSingleResult();
            return Promise.of(count);
        } catch (Exception e) {
            log.error("Error counting legacy config pipelines", e);
            return Promise.ofException(new RuntimeException("Failed to count legacy config pipelines", e));
        }
    }
}
