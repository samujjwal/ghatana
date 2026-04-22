package com.ghatana.audio.video.infrastructure.persistence.repository;

import com.ghatana.audio.video.infrastructure.persistence.entity.TranscriptionEntity;
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
 * @doc.purpose Unit tests for JpaTranscriptionRepository (synchronous per AEP pattern) // GH-90000
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("JpaTranscriptionRepository Tests [GH-90000]")
class JpaTranscriptionRepositoryTest {

    private EntityManagerFactory emf;
    private EntityManager entityManager;
    private JpaTranscriptionRepository repository;

    @BeforeEach
    void setUp() { // GH-90000
        emf = Persistence.createEntityManagerFactory("audio-video-test [GH-90000]");
        entityManager = emf.createEntityManager(); // GH-90000
        repository = new JpaTranscriptionRepository(entityManager); // GH-90000
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
    void testSaveTranscription() { // GH-90000
        // GIVEN
        String tenantId = "tenant-123";
        UUID audioFileId = UUID.randomUUID(); // GH-90000
        TranscriptionEntity entity = new TranscriptionEntity( // GH-90000
            UUID.randomUUID(), // GH-90000
            tenantId,
            audioFileId,
            UUID.randomUUID(), // GH-90000
            "Hello world, this is a test transcription.",
            "en"
        );
        entity.setConfidence(0.95f); // GH-90000

        // WHEN
        TranscriptionEntity saved = repository.save(tenantId, entity); // GH-90000

        // THEN
        assertThat(saved).isNotNull(); // GH-90000
        assertThat(saved.getId()).isEqualTo(entity.getId()); // GH-90000
        assertThat(saved.getTenantId()).isEqualTo(tenantId); // GH-90000
        assertThat(saved.getStatus()).isEqualTo(TranscriptionEntity.TranscriptionStatus.PENDING); // GH-90000
    }

    @Test
    @DisplayName("GIVEN saved entity WHEN findById THEN returns entity [GH-90000]")
    void testFindById() { // GH-90000
        // GIVEN
        String tenantId = "tenant-123";
        TranscriptionEntity entity = createTestTranscription(tenantId); // GH-90000
        repository.save(tenantId, entity); // GH-90000

        // WHEN
        var found = repository.findById(tenantId, entity.getId()); // GH-90000

        // THEN
        assertThat(found).isPresent(); // GH-90000
        assertThat(found.get().getId()).isEqualTo(entity.getId()); // GH-90000
    }

    @Test
    @DisplayName("GIVEN saved transcription WHEN findByAudioFileId THEN returns transcription [GH-90000]")
    void testFindByAudioFileId() { // GH-90000
        // GIVEN
        String tenantId = "tenant-123";
        UUID audioFileId = UUID.randomUUID(); // GH-90000
        TranscriptionEntity entity = new TranscriptionEntity( // GH-90000
            UUID.randomUUID(), // GH-90000
            tenantId,
            audioFileId,
            UUID.randomUUID(), // GH-90000
            "Test transcription text",
            "en"
        );
        repository.save(tenantId, entity); // GH-90000

        // WHEN
        var found = repository.findByAudioFileId(tenantId, audioFileId); // GH-90000

        // THEN
        assertThat(found).isPresent(); // GH-90000
        assertThat(found.get().getAudioFileId()).isEqualTo(audioFileId); // GH-90000
    }

    @Test
    @DisplayName("GIVEN wrong tenant WHEN findById THEN returns empty [GH-90000]")
    void testFindByIdWrongTenant() { // GH-90000
        // GIVEN
        String tenantId = "tenant-123";
        String wrongTenant = "tenant-999";
        TranscriptionEntity entity = createTestTranscription(tenantId); // GH-90000
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
        String tenantId = "tenant-123";

        TranscriptionEntity entity1 = createTestTranscription(tenantId); // GH-90000
        TranscriptionEntity entity2 = createTestTranscription(tenantId); // GH-90000

        repository.save(tenantId, entity1); // GH-90000
        repository.save(tenantId, entity2); // GH-90000

        // WHEN
        var found = repository.findByTenantId(tenantId); // GH-90000

        // THEN
        assertThat(found).hasSize(2); // GH-90000
    }

    @Test
    @DisplayName("GIVEN entities with different statuses WHEN findByStatus THEN returns matching [GH-90000]")
    void testFindByStatus() { // GH-90000
        // GIVEN
        String tenantId = "tenant-123";

        TranscriptionEntity completed = createTestTranscription(tenantId); // GH-90000
        completed.setStatus(TranscriptionEntity.TranscriptionStatus.COMPLETED); // GH-90000

        TranscriptionEntity pending = createTestTranscription(tenantId); // GH-90000
        pending.setStatus(TranscriptionEntity.TranscriptionStatus.PENDING); // GH-90000

        repository.save(tenantId, completed); // GH-90000
        repository.save(tenantId, pending); // GH-90000

        // WHEN
        var completedTranscriptions = repository.findByStatus( // GH-90000
            tenantId,
            TranscriptionEntity.TranscriptionStatus.COMPLETED
        );

        // THEN
        assertThat(completedTranscriptions).hasSize(1); // GH-90000
        assertThat(completedTranscriptions.get(0).getStatus()).isEqualTo( // GH-90000
            TranscriptionEntity.TranscriptionStatus.COMPLETED
        );
    }

    @Test
    @DisplayName("GIVEN saved entity WHEN softDelete THEN entity is marked deleted [GH-90000]")
    void testSoftDelete() { // GH-90000
        // GIVEN
        String tenantId = "tenant-123";
        TranscriptionEntity entity = createTestTranscription(tenantId); // GH-90000
        repository.save(tenantId, entity); // GH-90000

        // WHEN
        boolean deleted = repository.softDelete(tenantId, entity.getId()); // GH-90000

        // THEN
        assertThat(deleted).isTrue(); // GH-90000
        var found = repository.findById(tenantId, entity.getId()); // GH-90000
        assertThat(found).isPresent(); // GH-90000
        assertThat(found.get().isDeleted()).isTrue(); // GH-90000
        assertThat(found.get().getDeletedAt()).isNotNull(); // GH-90000
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
        TranscriptionEntity entity = createTestTranscription(tenantId); // GH-90000
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
    @DisplayName("GIVEN transcription exists WHEN existsByAudioFileId THEN returns true [GH-90000]")
    void testExistsByAudioFileId() { // GH-90000
        // GIVEN
        String tenantId = "tenant-123";
        UUID audioFileId = UUID.randomUUID(); // GH-90000
        TranscriptionEntity entity = new TranscriptionEntity( // GH-90000
            UUID.randomUUID(), // GH-90000
            tenantId,
            audioFileId,
            UUID.randomUUID(), // GH-90000
            "Test text",
            "en"
        );
        repository.save(tenantId, entity); // GH-90000

        // WHEN
        boolean exists = repository.existsByAudioFileId(tenantId, audioFileId); // GH-90000

        // THEN
        assertThat(exists).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("GIVEN no transcription WHEN existsByAudioFileId THEN returns false [GH-90000]")
    void testExistsByAudioFileIdNotFound() { // GH-90000
        // GIVEN
        String tenantId = "tenant-123";
        UUID audioFileId = UUID.randomUUID(); // GH-90000

        // WHEN
        boolean exists = repository.existsByAudioFileId(tenantId, audioFileId); // GH-90000

        // THEN
        assertThat(exists).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("GIVEN saved entities WHEN countByTenantId THEN returns correct count [GH-90000]")
    void testCountByTenantId() { // GH-90000
        // GIVEN
        String tenantId = "tenant-123";
        repository.save(tenantId, createTestTranscription(tenantId)); // GH-90000
        repository.save(tenantId, createTestTranscription(tenantId)); // GH-90000

        // WHEN
        long count = repository.countByTenantId(tenantId); // GH-90000

        // THEN
        assertThat(count).isEqualTo(2L); // GH-90000
    }

    // Helper methods

    private TranscriptionEntity createTestTranscription(String tenantId) { // GH-90000
        return new TranscriptionEntity( // GH-90000
            UUID.randomUUID(), // GH-90000
            tenantId,
            UUID.randomUUID(), // GH-90000
            UUID.randomUUID(), // GH-90000
            "Test transcription " + UUID.randomUUID(), // GH-90000
            "en"
        );
    }
}
