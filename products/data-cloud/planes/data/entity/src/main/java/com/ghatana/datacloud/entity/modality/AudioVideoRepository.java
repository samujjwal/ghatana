package com.ghatana.datacloud.entity.modality;

import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for audio-video entity operations.
 *
 * @doc.type interface
 * @doc.purpose Repository port for audio-video entity persistence
 * @doc.layer product
 * @doc.pattern Repository Port (Domain Layer)
 */
public interface AudioVideoRepository {

    /**
     * Finds an audio-video entity by ID.
     *
     * @param id the entity ID
     * @return Promise of optional audio-video entity
     */
    Promise<Optional<AudioVideoEntity>> findById(UUID id);

    /**
     * Finds audio-video entities by tenant and collection.
     *
     * @param tenantId tenant identifier
     * @param collectionName collection name
     * @return Promise of list of audio-video entities
     */
    Promise<List<AudioVideoEntity>> findByTenantAndCollection(String tenantId, String collectionName);

    /**
     * Finds audio-video entities by modality.
     *
     * @param tenantId tenant identifier
     * @param modality the media modality
     * @return Promise of list of audio-video entities
     */
    Promise<List<AudioVideoEntity>> findByModality(String tenantId, AudioVideoEntity.MediaModality modality);

    /**
     * Finds audio-video entities by transcoding status.
     *
     * @param tenantId tenant identifier
     * @param status the transcoding status
     * @return Promise of list of audio-video entities
     */
    Promise<List<AudioVideoEntity>> findByTranscodingStatus(String tenantId, AudioVideoEntity.TranscodingStatus status);

    /**
     * Saves an audio-video entity.
     *
     * @param entity the audio-video entity
     * @return Promise of saved entity
     */
    Promise<AudioVideoEntity> save(AudioVideoEntity entity);

    /**
     * Deletes an audio-video entity.
     *
     * @param id the entity ID
     * @return Promise of void
     */
    Promise<Void> delete(UUID id);

    /**
     * Updates transcoding status.
     *
     * @param id the entity ID
     * @param status the new status
     * @param variants transcoding variants (if complete)
     * @return Promise of updated entity
     */
    Promise<AudioVideoEntity> updateTranscodingStatus(UUID id, AudioVideoEntity.TranscodingStatus status, String variants);
}
