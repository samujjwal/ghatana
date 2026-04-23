package com.ghatana.audio.video.infrastructure.persistence.service;

import com.ghatana.audio.video.infrastructure.persistence.entity.AudioFileEntity;
import com.ghatana.audio.video.infrastructure.persistence.repository.AudioFileRepository;
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
 * @doc.purpose Unit tests for AudioFileService (async service layer per AEP pattern) // GH-90000
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("AudioFileService Tests")
@ExtendWith(MockitoExtension.class) // GH-90000
class AudioFileServiceTest extends EventloopTestBase {

    @Mock
    private AudioFileRepository repository;

    private AudioFileService service;

    @BeforeEach
    void setUp() { // GH-90000
        Eventloop eventloop = Eventloop.create(); // GH-90000
        service = new AudioFileService(repository, eventloop, Executors.newSingleThreadExecutor()); // GH-90000
    }

    @Test
    @DisplayName("GIVEN valid entity WHEN save THEN returns saved entity async")
    void testSave() { // GH-90000
        // GIVEN
        String tenantId = "tenant-123";
        AudioFileEntity entity = createTestAudioFile(tenantId); // GH-90000
        when(repository.save(eq(tenantId), any())).thenReturn(entity); // GH-90000

        // WHEN
        AudioFileEntity result = runPromise(() -> service.save(tenantId, entity)); // GH-90000

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
        AudioFileEntity entity = createTestAudioFile(tenantId); // GH-90000
        when(repository.findById(tenantId, entity.getId())).thenReturn(Optional.of(entity)); // GH-90000

        // WHEN
        Optional<AudioFileEntity> result = runPromise(() -> service.findById(tenantId, entity.getId())); // GH-90000

        // THEN
        assertThat(result).isPresent(); // GH-90000
        assertThat(result.get().getId()).isEqualTo(entity.getId()); // GH-90000
    }

    @Test
    @DisplayName("GIVEN non-existent entity WHEN findById THEN returns empty async")
    void testFindByIdNotFound() { // GH-90000
        // GIVEN
        String tenantId = "tenant-123";
        UUID id = UUID.randomUUID(); // GH-90000
        when(repository.findById(tenantId, id)).thenReturn(Optional.empty()); // GH-90000

        // WHEN
        Optional<AudioFileEntity> result = runPromise(() -> service.findById(tenantId, id)); // GH-90000

        // THEN
        assertThat(result).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("GIVEN multiple entities WHEN findByTenantId THEN returns list async")
    void testFindByTenantId() { // GH-90000
        // GIVEN
        String tenantId = "tenant-123";
        AudioFileEntity entity1 = createTestAudioFile(tenantId); // GH-90000
        AudioFileEntity entity2 = createTestAudioFile(tenantId); // GH-90000
        when(repository.findByTenantId(tenantId)).thenReturn(List.of(entity1, entity2)); // GH-90000

        // WHEN
        List<AudioFileEntity> result = runPromise(() -> service.findByTenantId(tenantId)); // GH-90000

        // THEN
        assertThat(result).hasSize(2); // GH-90000
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
    @DisplayName("GIVEN non-existent entity WHEN softDelete THEN returns false async")
    void testSoftDeleteNotFound() { // GH-90000
        // GIVEN
        String tenantId = "tenant-123";
        UUID id = UUID.randomUUID(); // GH-90000
        when(repository.softDelete(tenantId, id)).thenReturn(false); // GH-90000

        // WHEN
        Boolean result = runPromise(() -> service.softDelete(tenantId, id)); // GH-90000

        // THEN
        assertThat(result).isFalse(); // GH-90000
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
    @DisplayName("GIVEN existing entity WHEN existsById THEN returns true async")
    void testExistsById() { // GH-90000
        // GIVEN
        String tenantId = "tenant-123";
        UUID id = UUID.randomUUID(); // GH-90000
        when(repository.existsById(tenantId, id)).thenReturn(true); // GH-90000

        // WHEN
        Boolean result = runPromise(() -> service.existsById(tenantId, id)); // GH-90000

        // THEN
        assertThat(result).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("GIVEN entities WHEN countByTenantId THEN returns count async")
    void testCountByTenantId() { // GH-90000
        // GIVEN
        String tenantId = "tenant-123";
        when(repository.countByTenantId(tenantId)).thenReturn(5L); // GH-90000

        // WHEN
        Long result = runPromise(() -> service.countByTenantId(tenantId)); // GH-90000

        // THEN
        assertThat(result).isEqualTo(5L); // GH-90000
    }

    // Helper methods

    private AudioFileEntity createTestAudioFile(String tenantId) { // GH-90000
        return new AudioFileEntity( // GH-90000
            UUID.randomUUID(), // GH-90000
            tenantId,
            UUID.randomUUID(), // GH-90000
            "test-audio.mp3",
            "/storage/test.mp3",
            "mp3"
        );
    }
}
