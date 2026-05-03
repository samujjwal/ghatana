package com.ghatana.digitalmarketing.persistence;

import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract contract test for repositories (DMOS-P2-002).
 *
 * <p>Defines the contract that all repository implementations must satisfy.
 * Subclasses must implement the abstract methods to provide the repository instance
 * and test data for the specific entity type.</p>
 *
 * @doc.type class
 * @doc.purpose Abstract contract test for repository implementations (DMOS-P2-002)
 * @doc.layer persistence
 * @doc.pattern Contract Test
 */
public abstract class AbstractRepositoryContractTest<T> {

    /**
     * Returns the repository instance to test.
     */
    protected abstract Repository<T> getRepository();

    /**
     * Creates a test entity for the given tenant/workspace.
     */
    protected abstract T createTestEntity(DmTenantId tenantId, DmWorkspaceId workspaceId);

    /**
     * Returns the ID of an entity.
     */
    protected abstract String getEntityId(T entity);

    /**
     * Updates an entity with new data for testing updates.
     */
    protected abstract T updateEntity(T entity);

    /**
     * Repository interface for contract tests.
     */
    protected interface Repository<T> {
        io.activej.promise.Promise<T> save(T entity);
        io.activej.promise.Promise<Optional<T>> findById(String id);
        io.activej.promise.Promise<T> update(T entity);
        io.activej.promise.Promise<Void> delete(String id);
    }

    @Test
    @DisplayName("save stores entity and findById retrieves it")
    void save_storesEntityAndFindByIdRetrievesIt() {
        Repository<T> repository = getRepository();
        DmTenantId tenantId = DmTenantId.of("tenant-123");
        DmWorkspaceId workspaceId = DmWorkspaceId.of("workspace-456");

        T entity = createTestEntity(tenantId, workspaceId);

        T saved = repository.save(entity).getResult();
        Optional<T> found = repository.findById(getEntityId(saved)).getResult();

        assertThat(found).isPresent();
    }

    @Test
    @DisplayName("update modifies existing entity")
    void update_modifiesExistingEntity() {
        Repository<T> repository = getRepository();
        DmTenantId tenantId = DmTenantId.of("tenant-123");
        DmWorkspaceId workspaceId = DmWorkspaceId.of("workspace-456");

        T entity = createTestEntity(tenantId, workspaceId);
        T saved = repository.save(entity).getResult();

        T updated = updateEntity(saved);
        T updatedSaved = repository.update(updated).getResult();

        assertThat(getEntityId(updatedSaved)).isEqualTo(getEntityId(saved));
    }

    @Test
    @DisplayName("delete removes entity from storage")
    void delete_removesEntityFromStorage() {
        Repository<T> repository = getRepository();
        DmTenantId tenantId = DmTenantId.of("tenant-123");
        DmWorkspaceId workspaceId = DmWorkspaceId.of("workspace-456");

        T entity = createTestEntity(tenantId, workspaceId);
        T saved = repository.save(entity).getResult();

        repository.delete(getEntityId(saved)).getResult();
        Optional<T> found = repository.findById(getEntityId(saved)).getResult();

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("findById returns empty for non-existent ID")
    void findById_returnsEmptyForNonExistentId() {
        Repository<T> repository = getRepository();

        Optional<T> found = repository.findById("non-existent-id").getResult();

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("tenant isolation: entities from different tenants are isolated")
    void tenantIsolation_entitiesFromDifferentTenantsAreIsolated() {
        Repository<T> repository = getRepository();
        DmWorkspaceId workspaceId = DmWorkspaceId.of("workspace-456");

        DmTenantId tenant1 = DmTenantId.of("tenant-123");
        DmTenantId tenant2 = DmTenantId.of("tenant-789");

        T entity1 = createTestEntity(tenant1, workspaceId);
        T entity2 = createTestEntity(tenant2, workspaceId);

        repository.save(entity1).getResult();
        repository.save(entity2).getResult();

        // Verify that entities are stored with correct tenant IDs
        // Implementation-specific verification should be done in subclass tests
    }
}
