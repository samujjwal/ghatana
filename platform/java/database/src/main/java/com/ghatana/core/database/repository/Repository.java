package com.ghatana.core.database.repository;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

/**
 * Base repository port providing standard CRUD operations for domain entities.
 *
 * <p><b>Purpose</b><br>
 * Defines canonical repository contract following Repository pattern for data access
 * abstraction. Provides type-safe CRUD operations decoupling domain logic from
 * persistence implementation details (JPA, JDBC, NoSQL, etc.).
 *
 * <p><b>Architecture Role</b><br>
 * Port interface in core/database for repository implementations.
 * Used by:
 * - Domain Layer - Access entities through repository abstraction
 * - JpaRepository - JPA/Hibernate implementation
 * - Product Modules - Extend for domain-specific repositories
 *
 * <p><b>Repository Pattern Benefits</b><br>
 * - <b>Abstraction</b>: Hides persistence technology from domain logic
 * - <b>Testability</b>: Easy to mock for unit tests
 * - <b>Swappability</b>: Change persistence without domain code changes
 * - <b>Consistency</b>: Uniform interface across all repositories
 * - <b>Type Safety</b>: Generic types ensure compile-time safety
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // 1. Define domain-specific repository
 * public interface UserRepository extends Repository<User, UserId> {
 *     Optional<User> findByEmail(String email);
 *     List<User> findByTenantId(String tenantId);
 *     List<User> findActiveUsers();
 * }
 *
 * // 2. Implement with JpaRepository
 * public class JpaUserRepository extends JpaRepository<User, UserId>
 *         implements UserRepository {
 *     
 *     public JpaUserRepository(EntityManager em) {
 *         super(em, User.class);
 *     }
 *     
 *     @Override
 *     public Optional<User> findByEmail(String email) {
 *         return findSingleByQuery(
 *             "SELECT u FROM User u WHERE u.email = :email",
 *             query -> query.setParameter("email", email)
 *         );
 *     }
 *     
 *     @Override
 *     public List<User> findByTenantId(String tenantId) {
 *         return findManyByQuery(
 *             "SELECT u FROM User u WHERE u.tenantId = :tenantId",
 *             query -> query.setParameter("tenantId", tenantId)
 *         );
 *     }
 * }
 *
 * // 3. Use in service layer
 * public class UserService {
 *     private final UserRepository userRepository;
 *     
 *     public User createUser(String email, String tenantId) {
 *         User user = new User(email, tenantId);
 *         return userRepository.save(user);
 *     }
 *     
 *     public Optional<User> getUserByEmail(String email) {
 *         return userRepository.findByEmail(email);
 *     }
 * }
 * }</pre>
 *
 * <p><b>CRUD Operations</b><br>
 * - <b>Create</b>: {@code save(entity)} - Insert new entity
 * - <b>Read</b>: {@code findById(id)}, {@code findAll()} - Retrieve entities
 * - <b>Update</b>: {@code save(entity)} - Update existing entity
 * - <b>Delete</b>: {@code delete(entity)}, {@code deleteById(id)} - Remove entities
 * - <b>Query</b>: {@code count()}, {@code existsById(id)} - Check existence
 *
 * <p><b>Implementation Guidelines</b><br>
 * Implementations MUST:
 * - Return {@code Optional.empty()} for not found (never null)
 * - Throw unchecked exceptions for persistence errors
 * - Support transactions (save/delete within transaction)
 * - Validate entity/ID parameters (throw IllegalArgumentException)
 * - Log operations for debugging/audit
 *
 * <p><b>Generic Type Parameters</b><br>
 * - {@code <T>} - Entity type (must be domain entity with identity)
 * - {@code <ID extends Serializable>} - Identity type (Long, UUID, custom value object)
 *
 * <p><b>Thread Safety</b><br>
 * Thread safety depends on implementation. JPA repositories are typically NOT
 * thread-safe (use per-request EntityManager). For concurrent access, use
 * repository per thread or synchronized access.
 *
 * @param <T>  the entity type managed by this repository
 * @param <ID> the type of the entity's identifier (must be Serializable)
 * @see JpaRepository
 * @doc.type interface
 * @doc.purpose Base repository port for CRUD operations
 * @doc.layer core
 * @doc.pattern Port
 */
public interface Repository<T, ID extends Serializable> {
    Optional<T> findById(ID id);
    List<T> findAll();
    <S extends T> S save(S entity);
    void delete(T entity);
    void deleteById(ID id);
    long count();
    boolean existsById(ID id);
}
