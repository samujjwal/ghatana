package com.ghatana.audio.video.infrastructure.persistence.integration;

import com.ghatana.audio.video.infrastructure.persistence.entity.AudioFileEntity;
import com.ghatana.audio.video.infrastructure.persistence.entity.TranscriptionEntity;
import com.ghatana.audio.video.infrastructure.persistence.repository.JpaAudioFileRepository;
import com.ghatana.audio.video.infrastructure.persistence.repository.JpaTranscriptionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import jakarta.persistence.RollbackException;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.DriverManager;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @doc.type class
 * @doc.purpose Integration tests for persistence layer with PostgreSQL Testcontainer
 * @doc.layer test
 * @doc.pattern Integration Test
 */
@DisplayName("Persistence Integration Tests [GH-90000]")
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS) // GH-90000
class PersistenceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>( // GH-90000
        DockerImageName.parse("postgres:16-alpine [GH-90000]")
    )
    .withDatabaseName("audio_video_test [GH-90000]")
    .withUsername("test [GH-90000]")
    .withPassword("test [GH-90000]");

    private EntityManagerFactory emf;
    private EntityManager entityManager;
    private JpaAudioFileRepository audioFileRepository;
    private JpaTranscriptionRepository transcriptionRepository;

    @BeforeAll
    void setUpClass() { // GH-90000
        try (var connection = DriverManager.getConnection( // GH-90000
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()); // GH-90000
             var statement = connection.createStatement()) { // GH-90000
            statement.execute("CREATE SCHEMA IF NOT EXISTS audio_video [GH-90000]");
        } catch (Exception e) { // GH-90000
            throw new RuntimeException("Failed to create test schema audio_video", e); // GH-90000
        }

        // Configure persistence unit with Testcontainer database
        Map<String, String> properties = new HashMap<>(); // GH-90000
        properties.put("jakarta.persistence.jdbc.url", postgres.getJdbcUrl()); // GH-90000
        properties.put("jakarta.persistence.jdbc.user", postgres.getUsername()); // GH-90000
        properties.put("jakarta.persistence.jdbc.password", postgres.getPassword()); // GH-90000
        properties.put("jakarta.persistence.jdbc.driver", "org.postgresql.Driver"); // GH-90000
        properties.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect"); // GH-90000
        properties.put("hibernate.hbm2ddl.auto", "create-drop"); // GH-90000
        properties.put("hibernate.default_schema", "audio_video"); // GH-90000
        properties.put("hibernate.show_sql", "true"); // GH-90000

        emf = Persistence.createEntityManagerFactory("audio-video-test", properties); // GH-90000
    }

    @BeforeEach
    void setUp() { // GH-90000
        entityManager = emf.createEntityManager(); // GH-90000
        audioFileRepository = new JpaAudioFileRepository(entityManager); // GH-90000
        transcriptionRepository = new JpaTranscriptionRepository(entityManager); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (entityManager != null && entityManager.isOpen()) { // GH-90000
            entityManager.close(); // GH-90000
        }
    }

    @AfterAll
    void tearDownClass() { // GH-90000
        if (emf != null && emf.isOpen()) { // GH-90000
            emf.close(); // GH-90000
        }
    }

    @Test
    @DisplayName("GIVEN audio file WHEN saved THEN can be retrieved from PostgreSQL [GH-90000]")
    void testAudioFilePersistence() { // GH-90000
        // GIVEN
        String tenantId = "tenant-123";
        AudioFileEntity audioFile = new AudioFileEntity( // GH-90000
            UUID.randomUUID(), // GH-90000
            tenantId,
            UUID.randomUUID(), // GH-90000
            "test-recording.wav",
            "/storage/recordings/test.wav",
            "wav"
        );
        audioFile.setDurationSeconds(120); // GH-90000
        audioFile.setFileSizeBytes(1024L * 1024L); // GH-90000

        // WHEN
        AudioFileEntity saved = audioFileRepository.save(tenantId, audioFile); // GH-90000
        entityManager.clear(); // Clear cache to force DB read // GH-90000

        // THEN
        var retrieved = audioFileRepository.findById(tenantId, saved.getId()); // GH-90000
        assertThat(retrieved).isPresent(); // GH-90000
        assertThat(retrieved.get().getFileName()).isEqualTo("test-recording.wav [GH-90000]");
        assertThat(retrieved.get().getDurationSeconds()).isEqualTo(120); // GH-90000
    }

    @Test
    @DisplayName("GIVEN transcription linked to audio file WHEN saved THEN relationship is persisted [GH-90000]")
    void testTranscriptionRelationship() { // GH-90000
        // GIVEN
        String tenantId = "tenant-123";

        // Create and save audio file
        AudioFileEntity audioFile = new AudioFileEntity( // GH-90000
            UUID.randomUUID(), // GH-90000
            tenantId,
            UUID.randomUUID(), // GH-90000
            "interview.mp3",
            "/storage/interviews/interview.mp3",
            "mp3"
        );
        audioFile = audioFileRepository.save(tenantId, audioFile); // GH-90000

        // Create transcription
        TranscriptionEntity transcription = new TranscriptionEntity( // GH-90000
            UUID.randomUUID(), // GH-90000
            tenantId,
            audioFile.getId(), // GH-90000
            UUID.randomUUID(), // GH-90000
            "This is a test transcription of the interview.",
            "en"
        );
        transcription.setConfidence(0.95f); // GH-90000
        transcription.setStatus(TranscriptionEntity.TranscriptionStatus.COMPLETED); // GH-90000

        // WHEN
        TranscriptionEntity saved = transcriptionRepository.save(tenantId, transcription); // GH-90000
        entityManager.clear(); // GH-90000

        // THEN
        var retrieved = transcriptionRepository.findById(tenantId, saved.getId()); // GH-90000
        assertThat(retrieved).isPresent(); // GH-90000
        assertThat(retrieved.get().getAudioFileId()).isEqualTo(audioFile.getId()); // GH-90000
        assertThat(retrieved.get().getText()).contains("test transcription [GH-90000]");
        assertThat(retrieved.get().getConfidence()).isEqualTo(0.95f); // GH-90000
    }

    @Test
    @DisplayName("GIVEN soft deleted audio file WHEN queried THEN not returned in results [GH-90000]")
    void testSoftDeleteExclusion() { // GH-90000
        // GIVEN
        String tenantId = "tenant-123";
        AudioFileEntity audioFile1 = createAudioFile(tenantId, "file1.mp3"); // GH-90000
        AudioFileEntity audioFile2 = createAudioFile(tenantId, "file2.mp3"); // GH-90000

        audioFileRepository.save(tenantId, audioFile1); // GH-90000
        audioFileRepository.save(tenantId, audioFile2); // GH-90000

        // WHEN - soft delete first file
        boolean deleted = audioFileRepository.softDelete(tenantId, audioFile1.getId()); // GH-90000
        entityManager.clear(); // GH-90000

        // THEN
        assertThat(deleted).isTrue(); // GH-90000

        // Should not be found by findById
        var found = audioFileRepository.findById(tenantId, audioFile1.getId()); // GH-90000
        assertThat(found).isEmpty(); // GH-90000

        // Count should exclude deleted
        long count = audioFileRepository.countByTenantId(tenantId); // GH-90000
        assertThat(count).isEqualTo(1L); // GH-90000

        // findByTenantId should exclude deleted
        var allFiles = audioFileRepository.findByTenantId(tenantId); // GH-90000
        assertThat(allFiles).hasSize(1); // GH-90000
        assertThat(allFiles.get(0).getFileName()).isEqualTo("file2.mp3 [GH-90000]");
    }

    @Test
    @DisplayName("GIVEN soft deleted file WHEN hard deleted THEN permanently removed [GH-90000]")
    void testHardDeleteAfterSoftDelete() { // GH-90000
        // GIVEN
        String tenantId = "tenant-123";
        AudioFileEntity audioFile = createAudioFile(tenantId, "delete-me.mp3"); // GH-90000
        audioFileRepository.save(tenantId, audioFile); // GH-90000

        // Soft delete first
        audioFileRepository.softDelete(tenantId, audioFile.getId()); // GH-90000

        // WHEN - hard delete
        boolean hardDeleted = audioFileRepository.hardDelete(tenantId, audioFile.getId()); // GH-90000

        // THEN
        assertThat(hardDeleted).isTrue(); // GH-90000

        // Count in all files (including deleted) should be 0 // GH-90000
        var allFiles = audioFileRepository.findAllByTenantIdIncludingDeleted(tenantId); // GH-90000
        assertThat(allFiles).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("GIVEN multiple tenants WHEN data saved THEN tenant isolation enforced [GH-90000]")
    void testTenantIsolation() { // GH-90000
        // GIVEN
        String tenant1 = "tenant-alpha";
        String tenant2 = "tenant-beta";

        AudioFileEntity file1 = createAudioFile(tenant1, "alpha-file.mp3"); // GH-90000
        AudioFileEntity file2 = createAudioFile(tenant2, "beta-file.mp3"); // GH-90000

        audioFileRepository.save(tenant1, file1); // GH-90000
        audioFileRepository.save(tenant2, file2); // GH-90000

        // WHEN & THEN
        // Tenant 1 should only see their file
        var tenant1Files = audioFileRepository.findByTenantId(tenant1); // GH-90000
        assertThat(tenant1Files).hasSize(1); // GH-90000
        assertThat(tenant1Files.get(0).getFileName()).isEqualTo("alpha-file.mp3 [GH-90000]");

        // Tenant 2 should only see their file
        var tenant2Files = audioFileRepository.findByTenantId(tenant2); // GH-90000
        assertThat(tenant2Files).hasSize(1); // GH-90000
        assertThat(tenant2Files.get(0).getFileName()).isEqualTo("beta-file.mp3 [GH-90000]");

        // Cross-tenant access should fail
        var crossAccess = audioFileRepository.findById(tenant1, file2.getId()); // GH-90000
        assertThat(crossAccess).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("GIVEN transcription with status WHEN findByStatus THEN returns matching [GH-90000]")
    void testFindByStatus() { // GH-90000
        // GIVEN
        String tenantId = "tenant-123";

        TranscriptionEntity completed = createTranscription(tenantId, "Completed text"); // GH-90000
        completed.setStatus(TranscriptionEntity.TranscriptionStatus.COMPLETED); // GH-90000

        TranscriptionEntity pending = createTranscription(tenantId, "Pending text"); // GH-90000
        pending.setStatus(TranscriptionEntity.TranscriptionStatus.PENDING); // GH-90000

        transcriptionRepository.save(tenantId, completed); // GH-90000
        transcriptionRepository.save(tenantId, pending); // GH-90000

        // WHEN
        var completedTranscriptions = transcriptionRepository.findByStatus( // GH-90000
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
    @DisplayName("GIVEN concurrent updates WHEN committing stale entity THEN optimistic lock prevents overwrite [GH-90000]")
    void testOptimisticLockingOnConcurrentUpdate() { // GH-90000
        String tenantId = "tenant-lock";
        AudioFileEntity base = createAudioFile(tenantId, "lock.mp3"); // GH-90000
        AudioFileEntity saved = audioFileRepository.save(tenantId, base); // GH-90000

        EntityManager em1 = emf.createEntityManager(); // GH-90000
        EntityManager em2 = emf.createEntityManager(); // GH-90000
        try {
            AudioFileEntity tx1Entity = em1.find(AudioFileEntity.class, saved.getId()); // GH-90000
            AudioFileEntity tx2Entity = em2.find(AudioFileEntity.class, saved.getId()); // GH-90000

            em1.getTransaction().begin(); // GH-90000
            tx1Entity.setFileName("lock-v1.mp3 [GH-90000]");
            em1.getTransaction().commit(); // GH-90000

            em2.getTransaction().begin(); // GH-90000
            tx2Entity.setFileName("lock-v2.mp3 [GH-90000]");
            assertThatThrownBy(() -> em2.getTransaction().commit()) // GH-90000
                .isInstanceOf(RollbackException.class); // GH-90000
        } finally {
            if (em1.getTransaction().isActive()) em1.getTransaction().rollback(); // GH-90000
            if (em2.getTransaction().isActive()) em2.getTransaction().rollback(); // GH-90000
            em1.close(); // GH-90000
            em2.close(); // GH-90000
        }
    }

    @Test
    @DisplayName("GIVEN invalid entity WHEN save fails THEN transaction is rolled back [GH-90000]")
    void testSaveRollbackOnConstraintViolation() { // GH-90000
        String tenantId = "tenant-rollback";
        long before = audioFileRepository.countByTenantId(tenantId); // GH-90000

        AudioFileEntity invalid = createAudioFile(tenantId, "bad.mp3"); // GH-90000
        invalid.setFileName(null); // file_name is NOT NULL // GH-90000

        assertThatThrownBy(() -> audioFileRepository.save(tenantId, invalid)) // GH-90000
            .isInstanceOf(RuntimeException.class) // GH-90000
            .hasMessageContaining("Failed to save AudioFile [GH-90000]");

        long after = audioFileRepository.countByTenantId(tenantId); // GH-90000
        assertThat(after).isEqualTo(before); // GH-90000
    }

    // Helper methods

    private AudioFileEntity createAudioFile(String tenantId, String fileName) { // GH-90000
        return new AudioFileEntity( // GH-90000
            UUID.randomUUID(), // GH-90000
            tenantId,
            UUID.randomUUID(), // GH-90000
            fileName,
            "/storage/" + fileName,
            fileName.substring(fileName.lastIndexOf('.') + 1) // GH-90000
        );
    }

    private TranscriptionEntity createTranscription(String tenantId, String text) { // GH-90000
        return new TranscriptionEntity( // GH-90000
            UUID.randomUUID(), // GH-90000
            tenantId,
            UUID.randomUUID(), // GH-90000
            UUID.randomUUID(), // GH-90000
            text,
            "en"
        );
    }
}
