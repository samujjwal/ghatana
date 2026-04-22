package com.ghatana.audio.video.infrastructure.persistence.repository;

import com.ghatana.audio.video.infrastructure.persistence.entity.AudioFileEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Unit tests for JpaAudioFileRepository (synchronous per AEP pattern) // GH-90000
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("JpaAudioFileRepository Tests [GH-90000]")
class JpaAudioFileRepositoryTest {

    private EntityManagerFactory emf;
    private EntityManager entityManager;
    private JpaAudioFileRepository repository;

    @BeforeEach
    void setUp() { // GH-90000
        // Use in-memory H2 for unit tests
        emf = Persistence.createEntityManagerFactory("audio-video-test [GH-90000]");
        entityManager = emf.createEntityManager(); // GH-90000
        repository = new JpaAudioFileRepository(entityManager); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (entityManager != null && entityManager.isOpen()) { // GH-90000
            entityManager.close(); // GH-90000
        }
        if (emf != null && emf.isOpen()) { // GH-90000
            emf.close(); // GH-90000
        }
    }

    @Test
    @DisplayName("GIVEN valid entity WHEN save THEN entity is persisted [GH-90000]")
    void testSaveAudioFile() { // GH-90000
        // GIVEN
        String tenantId = "tenant-123";
        UUID userId = UUID.randomUUID(); // GH-90000
        AudioFileEntity entity = new AudioFileEntity( // GH-90000
            UUID.randomUUID(), // GH-90000
            tenantId,
            userId,
            "test-audio.mp3",
            "/storage/test.mp3",
            "mp3"
        );
        entity.setDurationSeconds(120); // GH-90000
        entity.setFileSizeBytes(1024L * 1024L); // GH-90000

        // WHEN
        AudioFileEntity saved = repository.save(tenantId, entity); // GH-90000

        // THEN
        assertThat(saved).isNotNull(); // GH-90000
        assertThat(saved.getId()).isEqualTo(entity.getId()); // GH-90000
        assertThat(saved.getTenantId()).isEqualTo(tenantId); // GH-90000
        assertThat(saved.getStatus()).isEqualTo(AudioFileEntity.ProcessingStatus.PENDING); // GH-90000
    }

    @Test
    @DisplayName("GIVEN saved entity WHEN findById THEN returns entity [GH-90000]")
    void testFindById() { // GH-90000
        // GIVEN
        String tenantId = "tenant-123";
        AudioFileEntity entity = createTestAudioFile(tenantId); // GH-90000
        repository.save(tenantId, entity); // GH-90000

        // WHEN
        var found = repository.findById(tenantId, entity.getId()); // GH-90000

        // THEN
        assertThat(found).isPresent(); // GH-90000
        assertThat(found.get().getId()).isEqualTo(entity.getId()); // GH-90000
        assertThat(found.get().getFileName()).isEqualTo(entity.getFileName()); // GH-90000
    }

    @Test
    @DisplayName("GIVEN wrong tenant WHEN findById THEN returns empty [GH-90000]")
    void testFindByIdWrongTenant() { // GH-90000
        // GIVEN
        String tenantId = "tenant-123";
        String wrongTenant = "tenant-999";
        AudioFileEntity entity = createTestAudioFile(tenantId); // GH-90000
        repository.save(tenantId, entity); // GH-90000

        // WHEN
        var found = repository.findById(wrongTenant, entity.getId()); // GH-90000

        // THEN
        assertThat(found).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("GIVEN multiple entities WHEN findByTenantId THEN returns all for tenant [GH-90000]")
    void testFindByTenantId() { // GH-90000
        // GIVEN
        String tenantId1 = "tenant-123";
        String tenantId2 = "tenant-456";

        AudioFileEntity entity1 = createTestAudioFile(tenantId1); // GH-90000
        AudioFileEntity entity2 = createTestAudioFile(tenantId1); // GH-90000
        AudioFileEntity entity3 = createTestAudioFile(tenantId2); // GH-90000

        repository.save(tenantId1, entity1); // GH-90000
        repository.save(tenantId1, entity2); // GH-90000
        repository.save(tenantId2, entity3); // GH-90000

        // WHEN
        var found = repository.findByTenantId(tenantId1); // GH-90000

        // THEN
        assertThat(found).hasSize(2); // GH-90000
        assertThat(found).extracting("id [GH-90000]").containsExactlyInAnyOrder(entity1.getId(), entity2.getId());
    }

    @Test
    @DisplayName("GIVEN saved entity WHEN softDelete THEN entity is marked deleted [GH-90000]")
    void testSoftDelete() { // GH-90000
        // GIVEN
        String tenantId = "tenant-123";
        AudioFileEntity entity = createTestAudioFile(tenantId); // GH-90000
        repository.save(tenantId, entity); // GH-90000

        // WHEN
        boolean deleted = repository.softDelete(tenantId, entity.getId()); // GH-90000

        // THEN
        assertThat(deleted).isTrue(); // GH-90000
        var found = repository.findById(tenantId, entity.getId()); // GH-90000
        assertThat(found).isEmpty(); // GH-90000

        var includingDeleted = repository.findAllByTenantIdIncludingDeleted(tenantId).stream() // GH-90000
            .filter(audioFile -> audioFile.getId().equals(entity.getId())) // GH-90000
            .findFirst(); // GH-90000
        assertThat(includingDeleted).isPresent(); // GH-90000
        assertThat(includingDeleted.get().isDeleted()).isTrue(); // GH-90000
        assertThat(includingDeleted.get().getDeletedAt()).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("GIVEN no entity WHEN softDelete THEN returns false [GH-90000]")
    void testSoftDeleteNotFound() { // GH-90000
        // GIVEN
        String tenantId = "tenant-123";
        UUID nonExistentId = UUID.randomUUID(); // GH-90000

        // WHEN
        boolean deleted = repository.softDelete(tenantId, nonExistentId); // GH-90000

        // THEN
        assertThat(deleted).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("GIVEN soft deleted entity WHEN hardDelete THEN entity is removed [GH-90000]")
    void testHardDelete() { // GH-90000
        // GIVEN
        String tenantId = "tenant-123";
        AudioFileEntity entity = createTestAudioFile(tenantId); // GH-90000
        repository.save(tenantId, entity); // GH-90000
        repository.softDelete(tenantId, entity.getId()); // GH-90000

        // WHEN
        boolean deleted = repository.hardDelete(tenantId, entity.getId()); // GH-90000

        // THEN
        assertThat(deleted).isTrue(); // GH-90000
        var found = repository.findById(tenantId, entity.getId()); // GH-90000
        assertThat(found).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("GIVEN soft deleted user file WHEN findByUserId THEN excludes deleted records [GH-90000]")
    void testFindByUserIdExcludesSoftDeleted() { // GH-90000
        // GIVEN
        String tenantId = "tenant-123";
        UUID userId = UUID.randomUUID(); // GH-90000

        AudioFileEntity active = new AudioFileEntity( // GH-90000
            UUID.randomUUID(), // GH-90000
            tenantId,
            userId,
            "active.mp3",
            "/storage/active.mp3",
            "mp3"
        );
        AudioFileEntity deleted = new AudioFileEntity( // GH-90000
            UUID.randomUUID(), // GH-90000
            tenantId,
            userId,
            "deleted.mp3",
            "/storage/deleted.mp3",
            "mp3"
        );

        repository.save(tenantId, active); // GH-90000
        repository.save(tenantId, deleted); // GH-90000
        repository.softDelete(tenantId, deleted.getId()); // GH-90000

        // WHEN
        var found = repository.findByUserId(tenantId, userId); // GH-90000

        // THEN
        assertThat(found).hasSize(1); // GH-90000
        assertThat(found.getFirst().getId()).isEqualTo(active.getId()); // GH-90000
        assertThat(found.getFirst().isDeleted()).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("GIVEN saved entities WHEN countByTenantId THEN returns correct count [GH-90000]")
    void testCountByTenantId() { // GH-90000
        // GIVEN
        String tenantId = "tenant-123";
        repository.save(tenantId, createTestAudioFile(tenantId)); // GH-90000
        repository.save(tenantId, createTestAudioFile(tenantId)); // GH-90000

        // WHEN
        long count = repository.countByTenantId(tenantId); // GH-90000

        // THEN
        assertThat(count).isEqualTo(2L); // GH-90000
    }

    @Test
    @DisplayName("GIVEN soft deleted entity not counted WHEN countByTenantId [GH-90000]")
    void testCountByTenantIdExcludesDeleted() { // GH-90000
        // GIVEN
        String tenantId = "tenant-123";
        AudioFileEntity entity1 = createTestAudioFile(tenantId); // GH-90000
        AudioFileEntity entity2 = createTestAudioFile(tenantId); // GH-90000
        repository.save(tenantId, entity1); // GH-90000
        repository.save(tenantId, entity2); // GH-90000
        repository.softDelete(tenantId, entity1.getId()); // GH-90000

        // WHEN
        long count = repository.countByTenantId(tenantId); // GH-90000

        // THEN - count excludes soft deleted
        assertThat(count).isEqualTo(1L); // GH-90000
    }

    // Helper methods

    private AudioFileEntity createTestAudioFile(String tenantId) { // GH-90000
        return new AudioFileEntity( // GH-90000
            UUID.randomUUID(), // GH-90000
            tenantId,
            UUID.randomUUID(), // GH-90000
            "test-audio-" + UUID.randomUUID() + ".mp3", // GH-90000
            "/storage/test.mp3",
            "mp3"
        );
    }
}
