package com.ghatana.audio.video.infrastructure.persistence.service;

import com.ghatana.audio.video.infrastructure.persistence.entity.TranscriptionEntity;
import com.ghatana.audio.video.infrastructure.persistence.repository.TranscriptionRepository;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.eventloop.Eventloop;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * @doc.type class
 * @doc.purpose Unit tests for TranscriptionService (async service layer per AEP pattern) // GH-90000
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("TranscriptionService Tests")
@ExtendWith(MockitoExtension.class) // GH-90000
class TranscriptionServiceTest extends EventloopTestBase {

    @Mock
    private TranscriptionRepository repository;

    private TranscriptionService service;

    @BeforeEach
    void setUp() { // GH-90000
        Eventloop eventloop = Eventloop.create(); // GH-90000
        service = new TranscriptionService(repository, eventloop, Executors.newSingleThreadExecutor()); // GH-90000
    }

    @Test
    @DisplayName("GIVEN valid entity WHEN save THEN returns saved entity async")
    void testSave() { // GH-90000
        // GIVEN
        String tenantId = "tenant-123";
        TranscriptionEntity entity = createTestTranscription(tenantId); // GH-90000
        when(repository.save(eq(tenantId), any())).thenReturn(entity); // GH-90000

        // WHEN
        TranscriptionEntity result = runPromise(() -> service.save(tenantId, entity)); // GH-90000

        // THEN
        assertThat(result).isNotNull(); // GH-90000
        assertThat(result.getId()).isEqualTo(entity.getId()); // GH-90000
        verify(repository).save(tenantId, entity); // GH-90000
    }

    @Test
    @DisplayName("GIVEN existing entity WHEN findById THEN returns entity async")
    void testFindById() { // GH-90000
        // GIVEN
        String tenantId = "tenant-123";
        TranscriptionEntity entity = createTestTranscription(tenantId); // GH-90000
        when(repository.findById(tenantId, entity.getId())).thenReturn(Optional.of(entity)); // GH-90000

        // WHEN
        Optional<TranscriptionEntity> result = runPromise(() -> service.findById(tenantId, entity.getId())); // GH-90000

        // THEN
        assertThat(result).isPresent(); // GH-90000
        assertThat(result.get().getId()).isEqualTo(entity.getId()); // GH-90000
    }

    @Test
    @DisplayName("GIVEN existing transcription WHEN findByAudioFileId THEN returns entity async")
    void testFindByAudioFileId() { // GH-90000
        // GIVEN
        String tenantId = "tenant-123";
        UUID audioFileId = UUID.randomUUID(); // GH-90000
        TranscriptionEntity entity = createTestTranscription(tenantId); // GH-90000
        entity.setAudioFileId(audioFileId); // GH-90000
        when(repository.findByAudioFileId(tenantId, audioFileId)).thenReturn(Optional.of(entity)); // GH-90000

        // WHEN
        Optional<TranscriptionEntity> result = runPromise(() -> service.findByAudioFileId(tenantId, audioFileId)); // GH-90000

        // THEN
        assertThat(result).isPresent(); // GH-90000
        assertThat(result.get().getAudioFileId()).isEqualTo(audioFileId); // GH-90000
    }

    @Test
    @DisplayName("GIVEN multiple entities WHEN findByTenantId THEN returns list async")
    void testFindByTenantId() { // GH-90000
        // GIVEN
        String tenantId = "tenant-123";
        TranscriptionEntity entity1 = createTestTranscription(tenantId); // GH-90000
        TranscriptionEntity entity2 = createTestTranscription(tenantId); // GH-90000
        when(repository.findByTenantId(tenantId)).thenReturn(List.of(entity1, entity2)); // GH-90000

        // WHEN
        List<TranscriptionEntity> result = runPromise(() -> service.findByTenantId(tenantId)); // GH-90000

        // THEN
        assertThat(result).hasSize(2); // GH-90000
    }

    @Test
    @DisplayName("GIVEN entities with status WHEN findByStatus THEN returns filtered list async")
    void testFindByStatus() { // GH-90000
        // GIVEN
        String tenantId = "tenant-123";
        TranscriptionEntity.TranscriptionStatus status = TranscriptionEntity.TranscriptionStatus.COMPLETED;
        TranscriptionEntity entity = createTestTranscription(tenantId); // GH-90000
        entity.setStatus(status); // GH-90000
        when(repository.findByStatus(tenantId, status)).thenReturn(List.of(entity)); // GH-90000

        // WHEN
        List<TranscriptionEntity> result = runPromise(() -> service.findByStatus(tenantId, status)); // GH-90000

        // THEN
        assertThat(result).hasSize(1); // GH-90000
        assertThat(result.get(0).getStatus()).isEqualTo(status); // GH-90000
    }

    @Test
    @DisplayName("GIVEN entity WHEN softDelete THEN returns true async")
    void testSoftDelete() { // GH-90000
        // GIVEN
        String tenantId = "tenant-123";
        UUID id = UUID.randomUUID(); // GH-90000
        when(repository.softDelete(tenantId, id)).thenReturn(true); // GH-90000

        // WHEN
        Boolean result = runPromise(() -> service.softDelete(tenantId, id)); // GH-90000

        // THEN
        assertThat(result).isTrue(); // GH-90000
        verify(repository).softDelete(tenantId, id); // GH-90000
    }

    @Test
    @DisplayName("GIVEN entity WHEN hardDelete THEN returns true async")
    void testHardDelete() { // GH-90000
        // GIVEN
        String tenantId = "tenant-123";
        UUID id = UUID.randomUUID(); // GH-90000
        when(repository.hardDelete(tenantId, id)).thenReturn(true); // GH-90000

        // WHEN
        Boolean result = runPromise(() -> service.hardDelete(tenantId, id)); // GH-90000

        // THEN
        assertThat(result).isTrue(); // GH-90000
        verify(repository).hardDelete(tenantId, id); // GH-90000
    }

    @Test
    @DisplayName("GIVEN existing transcription WHEN existsByAudioFileId THEN returns true async")
    void testExistsByAudioFileId() { // GH-90000
        // GIVEN
        String tenantId = "tenant-123";
        UUID audioFileId = UUID.randomUUID(); // GH-90000
        when(repository.existsByAudioFileId(tenantId, audioFileId)).thenReturn(true); // GH-90000

        // WHEN
        Boolean result = runPromise(() -> service.existsByAudioFileId(tenantId, audioFileId)); // GH-90000

        // THEN
        assertThat(result).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("GIVEN entities WHEN countByTenantId THEN returns count async")
    void testCountByTenantId() { // GH-90000
        // GIVEN
        String tenantId = "tenant-123";
        when(repository.countByTenantId(tenantId)).thenReturn(3L); // GH-90000

        // WHEN
        Long result = runPromise(() -> service.countByTenantId(tenantId)); // GH-90000

        // THEN
        assertThat(result).isEqualTo(3L); // GH-90000
    }

    // Helper methods

    private TranscriptionEntity createTestTranscription(String tenantId) { // GH-90000
        return new TranscriptionEntity( // GH-90000
            UUID.randomUUID(), // GH-90000
            tenantId,
            UUID.randomUUID(), // GH-90000
            UUID.randomUUID(), // GH-90000
            "Hello world transcription",
            "en"
        );
    }
}
