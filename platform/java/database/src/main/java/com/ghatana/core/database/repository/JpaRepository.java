package com.ghatana.core.database.repository;

import com.ghatana.platform.core.util.Preconditions;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Production-grade JPA repository providing comprehensive CRUD, pagination, and query operations.
 *
 * <p><b>Purpose</b><br>
 * Abstract base repository with full-featured JPA operations including named queries,
 * criteria queries, pagination, batch operations, and transaction management. Provides
 * foundation for domain repositories with consistent error handling and logging.
 *
 * <p><b>Architecture Role</b><br>
 * Enhanced adapter in core/database/repository for production JPA persistence.
 * Used by:
 * - Domain Repositories - Extend for entity-specific operations with named queries
 * - Service Layer - Access rich query capabilities (pagination, batch, named queries)
 * - Reporting - Execute complex queries with pagination
 * - Batch Processors - saveAll/deleteAll for bulk operations
 *
 * <p><b>Repository Features</b><br>
 * - <b>CRUD Operations</b>: save, findById, findAll, delete, count, existsById
 * - <b>Named Queries</b>: findByNamedQuery, findSingleByNamedQuery with parameters
 * - <b>Criteria Queries</b>: Type-safe dynamic queries
 * - <b>Pagination</b>: findAll(offset, limit) with page support
 * - <b>Batch Operations</b>: saveAll, deleteAll for bulk processing
 * - <b>Transaction Management</b>: Automatic transaction boundaries
 * - <b>Validation</b>: Parameter validation with Preconditions
 * - <b>Logging</b>: Debug logging for all operations
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // 1. Extend for domain repository
 * @NamedQueries({
 *     @NamedQuery(
 *         name = "User.findByEmail",
 *         query = "SELECT u FROM User u WHERE u.email = :email"
 *     ),
 *     @NamedQuery(
 *         name = "User.findActive",
 *         query = "SELECT u FROM User u WHERE u.active = true"
 *     ),
 *     @NamedQuery(
 *         name = "User.findByTenant",
 *         query = "SELECT u FROM User u WHERE u.tenantId = :tenantId ORDER BY u.createdAt DESC"
 *     )
 * })
 * public class UserRepository extends JpaRepository<User, UserId> {
 *     
 *     public UserRepository(EntityManager em) {
 *         super(em, User.class);
 *     }
 *     
 *     public Optional<User> findByEmail(String email) {
 *         return findSingleByNamedQuery("User.findByEmail", "email", email);
 *     }
 *     
 *     public List<User> findActiveUsers() {
 *         return findByNamedQuery("User.findActive");
 *     }
 *     
 *     public List<User> findByTenantId(String tenantId) {
 *         return findByNamedQuery("User.findByTenant", "tenantId", tenantId);
 *     }
 * }
 *
 * // 2. Use pagination
 * public Page<User> getUsersPage(int page, int size) {
 *     List<User> users = userRepository.findAll(page * size, size);
 *     long total = userRepository.count();
 *     return new Page<>(users, page, size, total);
 * }
 *
 * // 3. Batch operations
 * public List<User> createUsers(List<String> emails) {
 *     List<User> users = emails.stream()
 *         .map(User::new)
 *         .toList();
 *     return userRepository.saveAll(users); // Batch persist
 * }
 *
 * // 4. Named query with multiple parameters
 * public List<User> findActiveByTenant(String tenantId) {
 *     return findByNamedQuery(
 *         "User.findActiveByTenant",
 *         "tenantId", tenantId,
 *         "active", true
 *     );
 * }
 * }</pre>
 *
 * <p><b>Named Query Parameters</b><br>
 * Parameters passed as varargs: {@code "param1", value1, "param2", value2, ...}
 * <pre>{@code
 * // Example: findByNamedQuery("User.search", "name", "John", "minAge", 18)
 * // Maps to: WHERE u.name = :name AND u.age >= :minAge
 * }</pre>
 *
 * <p><b>Transaction Semantics</b><br>
 * - <b>save/delete</b>: Automatically starts transaction if not active
 * - <b>Commit</b>: Auto-commit if transaction started by repository
 * - <b>Rollback</b>: Auto-rollback on exception
 * - <b>Nested</b>: Participates in outer transaction if active
 *
 * <p><b>Entity Lifecycle Detection</b><br>
 * {@code isNew(entity)} determines persist vs merge:
 * - Override in subclass for custom new-entity detection
 * - Default: checks if entity has null/zero ID
 * - New entities → persist(), existing → merge()
 *
 * <p><b>Pagination Best Practices</b><br>
 * - Use {@code findAll(offset, limit)} for large result sets
 * - Calculate pages: offset = page * pageSize
 * - Always combine with {@code count()} for total pages
 * - Consider adding ORDER BY to named queries for consistent pagination
 *
 * <p><b>Performance Considerations</b><br>
 * - Named queries are pre-compiled (faster than dynamic JPQL)
 * - Batch operations reduce DB round-trips
 * - Use pagination to avoid loading large result sets
 * - Prefer criteria queries for dynamic conditions
 *
 * <p><b>Thread Safety</b><br>
 * NOT thread-safe - EntityManager is NOT thread-safe. Use one repository
 * instance per thread or request scope.
 *
 * @param <T> The entity type managed by this repository
 * @param <ID> The entity ID type
 * @see EntityManager
 * @see jakarta.persistence.NamedQuery
 * @see jakarta.persistence.criteria.CriteriaQuery
 * @since 1.0.0
 * @doc.type class
 * @doc.purpose Production-grade JPA repository with named queries and pagination
 * @doc.layer core
 * @doc.pattern Adapter
 */
public abstract class JpaRepository<T, ID> {
    private static final Logger LOG = LoggerFactory.getLogger(JpaRepository.class);
    
    protected final EntityManager entityManager;
    protected final Class<T> entityClass;
    
    /**
     * Creates a new JpaRepository.
     * 
     * @param entityManager The EntityManager to use
     * @param entityClass The entity class
     */
    protected JpaRepository(EntityManager entityManager, Class<T> entityClass) {
        this.entityManager = Preconditions.requireNonNull(entityManager, "EntityManager cannot be null");
        this.entityClass = Preconditions.requireNonNull(entityClass, "Entity class cannot be null");
        
        LOG.debug("Created repository for entity: {}", entityClass.getSimpleName());
    }
    
    /**
     * Saves an entity (insert or update).
     * 
     * @param entity The entity to save
     * @return The saved entity
     */
    public T save(T entity) {
        Preconditions.requireNonNull(entity, "Entity cannot be null");
        
        LOG.debug("Saving entity: {}", entity);
        jakarta.persistence.EntityTransaction tx = entityManager.getTransaction();
        boolean began = false;
        try {
            if (!tx.isActive()) {
                tx.begin();
                began = true;
            }

            if (isNew(entity)) {
                entityManager.persist(entity);
                LOG.debug("Persisted new entity: {}", entity);
            } else {
                T merged = entityManager.merge(entity);
                LOG.debug("Merged existing entity: {}", merged);
                entity = merged;
            }

            if (began) {
                tx.commit();
            }

            return entity;
        } catch (RuntimeException e) {
            if (began && tx.isActive()) {
                tx.rollback();
            }
            throw e;
        }
    }
    
    /**
     * Saves all entities in the given iterable within a single transaction.
     *
     * <p>All entities are persisted or merged in one transaction to avoid N×round-trips.
     *
     * @param entities The entities to save
     * @return The saved entities
     */
    public List<T> saveAll(Iterable<T> entities) {
        Preconditions.requireNonNull(entities, "Entities cannot be null");

        jakarta.persistence.EntityTransaction tx = entityManager.getTransaction();
        boolean began = false;
        try {
            if (!tx.isActive()) {
                tx.begin();
                began = true;
            }

            java.util.List<T> results = new java.util.ArrayList<>();
            for (T entity : entities) {
                Preconditions.requireNonNull(entity, "Entity in batch cannot be null");
                if (isNew(entity)) {
                    entityManager.persist(entity);
                    results.add(entity);
                } else {
                    results.add(entityManager.merge(entity));
                }
            }

            if (began) {
                tx.commit();
            }
            return java.util.Collections.unmodifiableList(results);
        } catch (RuntimeException e) {
            if (began && tx.isActive()) {
                tx.rollback();
            }
            throw e;
        }
    }
    
    /**
     * Finds an entity by its ID.
     * 
     * @param id The entity ID
     * @return Optional containing the entity if found
     */
    public Optional<T> findById(ID id) {
        Preconditions.requireNonNull(id, "ID cannot be null");
        
        LOG.debug("Finding entity by ID: {}", id);
        
        T entity = entityManager.find(entityClass, id);
        return Optional.ofNullable(entity);
    }
    
    /**
     * Checks if an entity exists by its ID.
     * 
     * @param id The entity ID
     * @return true if the entity exists
     */
    public boolean existsById(ID id) {
        return findById(id).isPresent();
    }
    
    /**
     * Finds all entities.
     * 
     * @return List of all entities
     */
    public List<T> findAll() {
        LOG.debug("Finding all entities of type: {}", entityClass.getSimpleName());
        
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> query = cb.createQuery(entityClass);
        Root<T> root = query.from(entityClass);
        query.select(root);
        
        return entityManager.createQuery(query).getResultList();
    }
    
    /**
     * Finds all entities with pagination.
     * 
     * @param offset The offset (0-based)
     * @param limit The maximum number of results
     * @return List of entities within the specified range
     */
    public List<T> findAll(int offset, int limit) {
        Preconditions.requireNonNegative(offset, "Offset cannot be negative");
        Preconditions.requirePositive(limit, "Limit must be positive");
        
        LOG.debug("Finding entities with pagination: offset={}, limit={}", offset, limit);
        
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> query = cb.createQuery(entityClass);
        Root<T> root = query.from(entityClass);
        query.select(root);
        
        return entityManager.createQuery(query)
            .setFirstResult(offset)
            .setMaxResults(limit)
            .getResultList();
    }
    
    /**
     * Counts all entities.
     * 
     * @return The total count of entities
     */
    public long count() {
        LOG.debug("Counting entities of type: {}", entityClass.getSimpleName());
        
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<T> root = query.from(entityClass);
        query.select(cb.count(root));
        
        return entityManager.createQuery(query).getSingleResult();
    }
    
    /**
     * Deletes an entity by its ID.
     * 
     * @param id The entity ID
     * @return true if the entity was deleted, false if not found
     */
    public boolean deleteById(ID id) {
        Preconditions.requireNonNull(id, "ID cannot be null");
        
        LOG.debug("Deleting entity by ID: {}", id);
        Optional<T> entity = findById(id);
        if (entity.isPresent()) {
            jakarta.persistence.EntityTransaction tx = entityManager.getTransaction();
            boolean began = false;
            try {
                if (!tx.isActive()) {
                    tx.begin();
                    began = true;
                }
                entityManager.remove(entity.get());
                if (began) tx.commit();
                LOG.debug("Deleted entity with ID: {}", id);
                return true;
            } catch (RuntimeException e) {
                if (began && tx.isActive()) tx.rollback();
                throw e;
            }
        } else {
            LOG.debug("Entity with ID {} not found for deletion", id);
            return false;
        }
    }
    
    /**
     * Deletes an entity.
     * 
     * @param entity The entity to delete
     */
    public void delete(T entity) {
        Preconditions.requireNonNull(entity, "Entity cannot be null");
        
        LOG.debug("Deleting entity: {}", entity);
        jakarta.persistence.EntityTransaction tx = entityManager.getTransaction();
        boolean began = false;
        try {
            if (!tx.isActive()) {
                tx.begin();
                began = true;
            }

            if (entityManager.contains(entity)) {
                entityManager.remove(entity);
            } else {
                // Entity is detached, merge first then remove
                T managedEntity = entityManager.merge(entity);
                entityManager.remove(managedEntity);
            }

            if (began) tx.commit();
            LOG.debug("Deleted entity: {}", entity);
        } catch (RuntimeException e) {
            if (began && tx.isActive()) tx.rollback();
            throw e;
        }
    }
    
    /**
     * Deletes all entities in the given iterable.
     * 
     * @param entities The entities to delete
     */
    public void deleteAll(Iterable<T> entities) {
        Preconditions.requireNonNull(entities, "Entities cannot be null");
        
        entities.forEach(this::delete);
    }
    
    /**
     * Flushes pending changes to the database.
     */
    public void flush() {
        LOG.debug("Flushing EntityManager");
        entityManager.flush();
    }
    
    /**
     * Refreshes an entity from the database.
     * 
     * @param entity The entity to refresh
     */
    public void refresh(T entity) {
        Preconditions.requireNonNull(entity, "Entity cannot be null");
        
        LOG.debug("Refreshing entity: {}", entity);
        entityManager.refresh(entity);
    }
    
    /**
     * Detaches an entity from the persistence context.
     * 
     * @param entity The entity to detach
     */
    public void detach(T entity) {
        Preconditions.requireNonNull(entity, "Entity cannot be null");
        
        LOG.debug("Detaching entity: {}", entity);
        entityManager.detach(entity);
    }
    
    /**
     * Executes a named query and returns the results.
     * 
     * @param queryName The name of the named query
     * @param parameters The query parameters (name-value pairs)
     * @return List of results
     */
    protected List<T> findByNamedQuery(String queryName, Object... parameters) {
        Preconditions.requireNonBlank(queryName, "Query name cannot be blank");
        Preconditions.requireEvenLength(parameters, "Parameters must be name-value pairs");
        
        LOG.debug("Executing named query: {} with {} parameters", queryName, parameters.length / 2);
        
        TypedQuery<T> query = entityManager.createNamedQuery(queryName, entityClass);
        setParameters(query, parameters);
        
        return query.getResultList();
    }
    
    /**
     * Executes a named query and returns a single result.
     * 
     * @param queryName The name of the named query
     * @param parameters The query parameters (name-value pairs)
     * @return Optional containing the result if found
     */
    protected Optional<T> findSingleByNamedQuery(String queryName, Object... parameters) {
        Preconditions.requireNonBlank(queryName, "Query name cannot be blank");
        Preconditions.requireEvenLength(parameters, "Parameters must be name-value pairs");
        
        LOG.debug("Executing named query for single result: {} with {} parameters", 
                queryName, parameters.length / 2);
        
        TypedQuery<T> query = entityManager.createNamedQuery(queryName, entityClass);
        setParameters(query, parameters);
        
        try {
            return Optional.of(query.getSingleResult());
        } catch (NoResultException e) {
            LOG.debug("No result found for named query: {}", queryName);
            return Optional.empty();
        }
    }
    
    /**
     * Executes a JPQL query and returns the results.
     * 
     * @param jpql The JPQL query string
     * @param parameters The query parameters (name-value pairs)
     * @return List of results
     */
    protected List<T> findByJpql(String jpql, Object... parameters) {
        Preconditions.requireNonBlank(jpql, "JPQL cannot be blank");
        Preconditions.requireEvenLength(parameters, "Parameters must be name-value pairs");
        
        LOG.debug("Executing JPQL query: {} with {} parameters", jpql, parameters.length / 2);
        
        TypedQuery<T> query = entityManager.createQuery(jpql, entityClass);
        setParameters(query, parameters);
        
        return query.getResultList();
    }
    
    /**
     * Executes a JPQL query and returns a single result.
     * 
     * @param jpql The JPQL query string
     * @param parameters The query parameters (name-value pairs)
     * @return Optional containing the result if found
     */
    protected Optional<T> findSingleByJpql(String jpql, Object... parameters) {
        Preconditions.requireNonBlank(jpql, "JPQL cannot be blank");
        Preconditions.requireEvenLength(parameters, "Parameters must be name-value pairs");
        
        LOG.debug("Executing JPQL query for single result: {} with {} parameters", 
                jpql, parameters.length / 2);
        
        TypedQuery<T> query = entityManager.createQuery(jpql, entityClass);
        setParameters(query, parameters);
        
        try {
            return Optional.of(query.getSingleResult());
        } catch (NoResultException e) {
            LOG.debug("No result found for JPQL query: {}", jpql);
            return Optional.empty();
        }
    }
    
    /**
     * Executes an update or delete query.
     * 
     * @param jpql The JPQL update/delete query
     * @param parameters The query parameters (name-value pairs)
     * @return The number of affected rows
     */
    protected int executeUpdate(String jpql, Object... parameters) {
        Preconditions.requireNonBlank(jpql, "JPQL cannot be blank");
        Preconditions.requireEvenLength(parameters, "Parameters must be name-value pairs");
        
        LOG.debug("Executing update query: {} with {} parameters", jpql, parameters.length / 2);
        
        Query query = entityManager.createQuery(jpql);
        setParameters(query, parameters);
        
        int affectedRows = query.executeUpdate();
        LOG.debug("Update query affected {} rows", affectedRows);
        
        return affectedRows;
    }
    
    /**
     * Gets the EntityManager used by this repository.
     * 
     * @return The EntityManager
     */
    protected EntityManager getEntityManager() {
        return entityManager;
    }
    
    /**
     * Gets the entity class managed by this repository.
     * 
     * @return The entity class
     */
    protected Class<T> getEntityClass() {
        return entityClass;
    }
    
    /**
     * Determines if an entity is new (not yet persisted).
     * 
     * <p>Subclasses can override this method to provide custom logic
     * for determining if an entity is new.
     * 
     * @param entity The entity to check
     * @return true if the entity is new
     */
    protected boolean isNew(T entity) {
        // Default implementation - override in subclasses if needed
        // Use JPA identifier when available to determine if entity is new.
        try {
            Object id = entityManager.getEntityManagerFactory()
                    .getPersistenceUnitUtil()
                    .getIdentifier(entity);

            if (id == null) {
                return true;
            }

            // If an entity with the same id exists in the persistence context or database,
            // consider it not new. This avoids attempting to persist a different instance
            // with the same identifier which leads to NonUniqueObjectException.
            T found = entityManager.find(entityClass, id);
            return found == null;
        } catch (RuntimeException e) {
            // Fallback to contains() semantics if identifier resolution fails for any reason
            LOG.debug("isNew detection by identifier failed, falling back to contains(): {}", e.getMessage());
            return !entityManager.contains(entity);
        }
    }
    
    /**
     * Sets parameters on a query from name-value pairs.
     * 
     * @param query The query to set parameters on
     * @param parameters The parameters as name-value pairs
     */
    private void setParameters(Query query, Object... parameters) {
        for (int i = 0; i < parameters.length; i += 2) {
            String paramName = (String) parameters[i];
            Object paramValue = parameters[i + 1];
            query.setParameter(paramName, paramValue);
            LOG.trace("Set parameter: {} = {}", paramName, paramValue);
        }
    }
}
