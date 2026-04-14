package com.ghatana.audio.video.infrastructure.persistence.integration;

import com.ghatana.audio.video.infrastructure.persistence.entity.AudioFileEntity;
import com.ghatana.audio.video.infrastructure.persistence.entity.TranscriptionEntity;
import com.ghatana.audio.video.infrastructure.persistence.repository.JpaAudioFileRepository;
import com.ghatana.audio.video.infrastructure.persistence.repository.JpaTranscriptionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Integration tests for persistence layer with PostgreSQL Testcontainer
 * @doc.layer test
 * @doc.pattern Integration Test
 */
@DisplayName("Persistence Integration Tests")
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PersistenceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
        DockerImageName.parse("postgres:16-alpine")
    )
    .withDatabaseName("audio_video_test")
    .withUsername("test")
    .withPassword("test");

    private EntityManagerFactory emf;
    private EntityManager entityManager;
    private JpaAudioFileRepository audioFileRepository;
    private JpaTranscriptionRepository transcriptionRepository;

    @BeforeAll
    void setUpClass() {
        // Configure persistence unit with Testcontainer database
        Map<String, String> properties = new HashMap<>();
        properties.put("jakarta.persistence.jdbc.url", postgres.getJdbcUrl());
        properties.put("jakarta.persistence.jdbc.user", postgres.getUsername());
        properties.put("jakarta.persistence.jdbc.password", postgres.getPassword());
        properties.put("jakarta.persistence.jdbc.driver", "org.postgresql.Driver");
        properties.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        properties.put("hibernate.hbm2ddl.auto", "create-drop");
        properties.put("hibernate.show_sql", "true");

        emf = Persistence.createEntityManagerFactory("audio-video-test", properties);
    }

    @BeforeEach
    void setUp() {
        entityManager = emf.createEntityManager();
        audioFileRepository = new JpaAudioFileRepository(entityManager);
        transcriptionRepository = new JpaTranscriptionRepository(entityManager);
    }

    @AfterEach
    void tearDown() {
        if (entityManager != null && entityManager.isOpen()) {
            entityManager.close();
        }
    }

    @AfterAll
    void tearDownClass() {
        if (emf != null && emf.isOpen()) {
            emf.close();
        }
    }

    @Test
    @DisplayName("GIVEN audio file WHEN saved THEN can be retrieved from PostgreSQL")
    void testAudioFilePersistence() {
        // GIVEN
        String tenantId = "tenant-123";
        AudioFileEntity audioFile = new AudioFileEntity(
            UUID.randomUUID(),
            tenantId,
            UUID.randomUUID(),
            "test-recording.wav",
            "/storage/recordings/test.wav",
            "wav"
        );
        audioFile.setDurationSeconds(120);
        audioFile.setFileSizeBytes(1024L * 1024L);

        // WHEN
        AudioFileEntity saved = audioFileRepository.save(tenantId, audioFile);
        entityManager.clear(); // Clear cache to force DB read

        // THEN
        var retrieved = audioFileRepository.findById(tenantId, saved.getId());
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getFileName()).isEqualTo("test-recording.wav");
        assertThat(retrieved.get().getDurationSeconds()).isEqualTo(120);
    }

    @Test
    @DisplayName("GIVEN transcription linked to audio file WHEN saved THEN relationship is persisted")
    void testTranscriptionRelationship() {
        // GIVEN
        String tenantId = "tenant-123";

        // Create and save audio file
        AudioFileEntity audioFile = new AudioFileEntity(
            UUID.randomUUID(),
            tenantId,
            UUID.randomUUID(),
            "interview.mp3",
            "/storage/interviews/interview.mp3",
            "mp3"
        );
        audioFile = audioFileRepository.save(tenantId, audioFile);

        // Create transcription
        TranscriptionEntity transcription = new TranscriptionEntity(
            UUID.randomUUID(),
            tenantId,
            audioFile.getId(),
            "This is a test transcription of the interview.",
            "en"
        );
        transcription.setConfidence(0.95f);
        transcription.setStatus(TranscriptionEntity.TranscriptionStatus.COMPLETED);

        // WHEN
        TranscriptionEntity saved = transcriptionRepository.save(tenantId, transcription);
        entityManager.clear();

        // THEN
        var retrieved = transcriptionRepository.findById(tenantId, saved.getId());
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getAudioFileId()).isEqualTo(audioFile.getId());
        assertThat(retrieved.get().getTranscriptionText()).contains("test transcription");
        assertThat(retrieved.get().getConfidence()).isEqualTo(0.95f);
    }

    @Test
    @DisplayName("GIVEN soft deleted audio file WHEN queried THEN not returned in results")
    void testSoftDeleteExclusion() {
        // GIVEN
        String tenantId = "tenant-123";
        AudioFileEntity audioFile1 = createAudioFile(tenantId, "file1.mp3");
        AudioFileEntity audioFile2 = createAudioFile(tenantId, "file2.mp3");

        audioFileRepository.save(tenantId, audioFile1);
        audioFileRepository.save(tenantId, audioFile2);

        // WHEN - soft delete first file
        boolean deleted = audioFileRepository.softDelete(tenantId, audioFile1.getId());
        entityManager.clear();

        // THEN
        assertThat(deleted).isTrue();

        // Should not be found by findById
        var found = audioFileRepository.findById(tenantId, audioFile1.getId());
        assertThat(found).isEmpty();

        // Count should exclude deleted
        long count = audioFileRepository.countByTenantId(tenantId);
        assertThat(count).isEqualTo(1L);

        // findByTenantId should exclude deleted
        var allFiles = audioFileRepository.findByTenantId(tenantId);
        assertThat(allFiles).hasSize(1);
        assertThat(allFiles.get(0).getFileName()).isEqualTo("file2.mp3");
    }

    @Test
    @DisplayName("GIVEN soft deleted file WHEN hard deleted THEN permanently removed")
    void testHardDeleteAfterSoftDelete() {
        // GIVEN
        String tenantId = "tenant-123";
        AudioFileEntity audioFile = createAudioFile(tenantId, "delete-me.mp3");
        audioFileRepository.save(tenantId, audioFile);

        // Soft delete first
        audioFileRepository.softDelete(tenantId, audioFile.getId());

        // WHEN - hard delete
        boolean hardDeleted = audioFileRepository.hardDelete(tenantId, audioFile.getId());

        // THEN
        assertThat(hardDeleted).isTrue();

        // Count in all files (including deleted) should be 0
        var allFiles = audioFileRepository.findAllByTenantIdIncludingDeleted(tenantId);
        assertThat(allFiles).isEmpty();
    }

    @Test
    @DisplayName("GIVEN multiple tenants WHEN data saved THEN tenant isolation enforced")
    void testTenantIsolation() {
        // GIVEN
        String tenant1 = "tenant-alpha";
        String tenant2 = "tenant-beta";

        AudioFileEntity file1 = createAudioFile(tenant1, "alpha-file.mp3");
        AudioFileEntity file2 = createAudioFile(tenant2, "beta-file.mp3");

        audioFileRepository.save(tenant1, file1);
        audioFileRepository.save(tenant2, file2);

        // WHEN & THEN
        // Tenant 1 should only see their file
        var tenant1Files = audioFileRepository.findByTenantId(tenant1);
        assertThat(tenant1Files).hasSize(1);
        assertThat(tenant1Files.get(0).getFileName()).isEqualTo("alpha-file.mp3");

        // Tenant 2 should only see their file
        var tenant2Files = audioFileRepository.findByTenantId(tenant2);
        assertThat(tenant2Files).hasSize(1);
        assertThat(tenant2Files.get(0).getFileName()).isEqualTo("beta-file.mp3");

        // Cross-tenant access should fail
        var crossAccess = audioFileRepository.findById(tenant1, file2.getId());
        assertThat(crossAccess).isEmpty();
    }

    @Test
    @DisplayName("GIVEN transcription with status WHEN findByStatus THEN returns matching")
    void testFindByStatus() {
        // GIVEN
        String tenantId = "tenant-123";

        TranscriptionEntity completed = createTranscription(tenantId, "Completed text");
        completed.setStatus(TranscriptionEntity.TranscriptionStatus.COMPLETED);

        TranscriptionEntity pending = createTranscription(tenantId, "Pending text");
        pending.setStatus(TranscriptionEntity.TranscriptionStatus.PENDING);

        transcriptionRepository.save(tenantId, completed);
        transcriptionRepository.save(tenantId, pending);

        // WHEN
        var completedTranscriptions = transcriptionRepository.findByStatus(
            tenantId,
            TranscriptionEntity.TranscriptionStatus.COMPLETED
        );

        // THEN
        assertThat(completedTranscriptions).hasSize(1);
        assertThat(completedTranscriptions.get(0).getStatus()).isEqualTo(
            TranscriptionEntity.TranscriptionStatus.COMPLETED
        );
    }

    // Helper methods

    private AudioFileEntity createAudioFile(String tenantId, String fileName) {
        return new AudioFileEntity(
            UUID.randomUUID(),
            tenantId,
            UUID.randomUUID(),
            fileName,
            "/storage/" + fileName,
            fileName.substring(fileName.lastIndexOf('.') + 1)
        );
    }

    private TranscriptionEntity createTranscription(String tenantId, String text) {
        return new TranscriptionEntity(
            UUID.randomUUID(),
            tenantId,
            UUID.randomUUID(),
            text,
            "en"
        );
    }
}
