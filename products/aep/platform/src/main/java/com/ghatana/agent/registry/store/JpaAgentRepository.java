package com.ghatana.agent.registry.store;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JPA-backed repository for persisting and querying agent entities.
 *
 * @doc.type class
 * @doc.purpose Provides CRUD and query operations for agent entities using JPA EntityManager
 * @doc.layer product
 * @doc.pattern Repository
 */
public class JpaAgentRepository {
    private static final Logger log = LoggerFactory.getLogger(JpaAgentRepository.class);
    
    @PersistenceContext
    private EntityManager entityManager;
    
    public JpaAgentRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }
    
    /**
     * Save a new agent entity or update an existing one.
     *
     * @param entity The agent entity to save
     * @return The saved entity
     */
    public JpaAgentEntity save(JpaAgentEntity entity) {
        if (entity == null) {
            throw new IllegalArgumentException("Entity cannot be null");
        }
        
        try {
            if (entity.getId() == null) {
                entityManager.persist(entity);
                return entity;
            } else {
                return entityManager.merge(entity);
            }
        } catch (Exception e) {
            log.error("Failed to save agent entity: {}", entity.getId(), e);
            throw new RuntimeException("Failed to save agent entity: " + e.getMessage(), e);
        }
    }
    
    /**
     * Find an agent by ID.
     *
     * @param id The ID of the agent to find
     * @return An optional containing the agent if found, empty otherwise
     */
    public Optional<JpaAgentEntity> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        
        try {
            JpaAgentEntity entity = entityManager.find(JpaAgentEntity.class, id);
            return Optional.ofNullable(entity != null && !entity.isDeleted() ? entity : null);
        } catch (Exception e) {
            log.error("Failed to find agent by id: {}", id, e);
            throw new RuntimeException("Failed to find agent by id: " + e.getMessage(), e);
        }
    }
    
    /**
     * Check if an agent with the given ID exists.
     *
     * @param id The ID to check
     * @return true if an agent with the ID exists, false otherwise
     */
    public boolean existsById(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        
        try {
            return entityManager.createQuery(
                    "SELECT COUNT(a) > 0 FROM JpaAgentEntity a WHERE a.id = :id AND a.deleted = false",
                    Boolean.class)
                .setParameter("id", id)
                .getSingleResult();
        } catch (Exception e) {
            log.error("Failed to check if agent exists: {}", id, e);
            throw new RuntimeException("Failed to check if agent exists: " + e.getMessage(), e);
        }
    }
    
    /**
     * Find all agents, optionally including deleted ones.
     *
     * @return A list of all agents
     */
    public List<JpaAgentEntity> findAll() {
        try {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<JpaAgentEntity> query = cb.createQuery(JpaAgentEntity.class);
            Root<JpaAgentEntity> root = query.from(JpaAgentEntity.class);
            
            // Filter out deleted agents by default
            query.select(root)
                .where(cb.equal(root.get("deleted"), false))
                .orderBy(cb.asc(root.get("name")));
                
            return entityManager.createQuery(query).getResultList();
        } catch (Exception e) {
            log.error("Failed to find all agents", e);
            throw new RuntimeException("Failed to find all agents: " + e.getMessage(), e);
        }
    }
    
    /**
     * Soft delete an agent by ID.
     *
     * @param id The ID of the agent to delete
     * @return true if the agent was deleted, false otherwise
     */
    public boolean softDelete(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        
        try {
            JpaAgentEntity entity = entityManager.find(JpaAgentEntity.class, id);
            if (entity != null && !entity.isDeleted()) {
                entity.setDeleted(true);
                entity.setDeletedAt(java.time.Instant.now());
                entityManager.merge(entity);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("Failed to soft delete agent: {}", id, e);
            throw new RuntimeException("Failed to soft delete agent: " + e.getMessage(), e);
        }
    }

    /**
     * Find an agent by ID.
     *
     * @param id The agent ID
     * @param includeDeleted Whether to include soft-deleted agents
     * @return Optional containing the agent if found
     */
    public Optional<JpaAgentEntity> findById(String id, boolean includeDeleted) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("ID cannot be null or empty");
        }
        
        try {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<JpaAgentEntity> cq = cb.createQuery(JpaAgentEntity.class);
            Root<JpaAgentEntity> root = cq.from(JpaAgentEntity.class);

            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("id"), id));
            
            if (!includeDeleted) {
                predicates.add(cb.isFalse(root.get("deleted")));
            }

            cq.where(predicates.toArray(new Predicate[0]));
            
            try {
                JpaAgentEntity result = entityManager.createQuery(cq).getSingleResult();
                return Optional.ofNullable(result);
            } catch (Exception e) {
                log.debug("Agent not found with id: {}", id, e);
                return Optional.empty();
            }
        } catch (Exception e) {
            log.error("Failed to find agent by ID: {}", id, e);
            throw new RuntimeException("Failed to find agent by ID: " + e.getMessage(), e);
        }
    }
    
    public List<JpaAgentEntity> findByAgentId(String agentId) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<JpaAgentEntity> cq = cb.createQuery(JpaAgentEntity.class);
        Root<JpaAgentEntity> root = cq.from(JpaAgentEntity.class);
        
        List<Predicate> predicates = new ArrayList<>();
        if (agentId != null) {
            predicates.add(cb.equal(root.get("name"), agentId));
        }
        predicates.add(cb.isFalse(root.get("deleted")));
        
        cq.where(predicates.toArray(new Predicate[0]));
        return entityManager.createQuery(cq).getResultList();
    }

    /**
     * Find agents by tenant ID with filtering and pagination.
     *
     * @param tenantId The tenant ID (optional)
     * @param tags List of tags to filter by (optional)
     * @param implementationType Implementation type to filter by (optional)
     * @param includeDeprecated Whether to include deprecated agents
     * @param includeDeleted Whether to include soft-deleted agents
     * @param offset Pagination offset
     * @param limit Pagination limit
     * @return List of matching agent entities
     */
    public List<JpaAgentEntity> findByTenantId(
            String tenantId,
            List<String> tags,
            String implementationType,
            boolean includeDeprecated,
            boolean includeDeleted,
            int offset,
            int limit) {
        
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<JpaAgentEntity> query = cb.createQuery(JpaAgentEntity.class);
        Root<JpaAgentEntity> root = query.from(JpaAgentEntity.class);
        
        List<Predicate> predicates = new ArrayList<>();
        
        // Apply tenant filter if specified
        if (tenantId != null && !tenantId.isEmpty()) {
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
        }
        
        // Apply implementation type filter if specified
        if (implementationType != null && !implementationType.isEmpty()) {
            predicates.add(cb.equal(root.get("implementationType"), implementationType));
        }
        
        // Apply tag filter if specified
        if (tags != null && !tags.isEmpty()) {
            Join<JpaAgentEntity, String> tagJoin = root.join("tags");
            predicates.add(tagJoin.in(tags));
        }
        
        // Filter out deprecated agents if requested
        if (!includeDeprecated) {
            predicates.add(cb.equal(root.get("deprecated"), false));
        }
        
        // Filter out deleted agents if requested
        if (!includeDeleted) {
            predicates.add(cb.equal(root.get("deleted"), false));
        }
        
        query.where(predicates.toArray(new Predicate[0]));
        query.orderBy(cb.asc(root.get("name")));
        
        TypedQuery<JpaAgentEntity> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult(offset);
        typedQuery.setMaxResults(limit);
        
        return typedQuery.getResultList();
    }

    /**
     * Count the total number of agents matching the given filters.
     *
     * @param tenantId The tenant ID (optional)
     * @param tags List of tags to filter by (optional)
     * @param implementationType Implementation type to filter by (optional)
     * @param includeDeprecated Whether to include deprecated agents
     * @param includeDeleted Whether to include soft-deleted agents
     * @return Total count of matching agents
     */
    public long countByTenantId(
            String tenantId,
            List<String> tags,
            String implementationType,
            boolean includeDeprecated,
            boolean includeDeleted) {
        
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<JpaAgentEntity> root = query.from(JpaAgentEntity.class);
        
        List<Predicate> predicates = new ArrayList<>();
        
        // Apply tenant filter if specified
        if (tenantId != null && !tenantId.isEmpty()) {
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
        }
        
        // Apply implementation type filter if specified
        if (implementationType != null && !implementationType.isEmpty()) {
            predicates.add(cb.equal(root.get("implementationType"), implementationType));
        }
        
        // Apply tag filter if specified
        if (tags != null && !tags.isEmpty()) {
            Join<JpaAgentEntity, String> tagJoin = root.join("tags");
            predicates.add(tagJoin.in(tags));
        }
        
        // Filter out deprecated agents if requested
        if (!includeDeprecated) {
            predicates.add(cb.equal(root.get("deprecated"), false));
        }
        
        // Filter out deleted agents if requested
        if (!includeDeleted) {
            predicates.add(cb.equal(root.get("deleted"), false));
        }
        
        query.select(cb.count(root));
        query.where(predicates.toArray(new Predicate[0]));
        
        return entityManager.createQuery(query).getSingleResult();
    }

    /**
     * Soft delete an agent by ID.
     *
     * @param id The agent ID
     * @return true if the agent was found and deleted, false otherwise
     */
    public boolean softDeleteById(String id) {
        Optional<JpaAgentEntity> agentOpt = findById(id, false);
        if (agentOpt.isPresent()) {
            JpaAgentEntity agent = agentOpt.get();
            agent.setDeleted(true);
            entityManager.merge(agent);
            return true;
        }
        return false;
    }

    /**
     * Hard delete an agent by ID.
     *
     * @param id The agent ID
     * @return true if the agent was found and deleted, false otherwise
     */
    public boolean hardDeleteById(String id) {
        Optional<JpaAgentEntity> agentOpt = findById(id, true);
        if (agentOpt.isPresent()) {
            entityManager.remove(agentOpt.get());
            return true;
        }
        return false;
    }
}
