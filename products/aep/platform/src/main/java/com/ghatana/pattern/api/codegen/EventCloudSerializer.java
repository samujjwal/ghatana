package com.ghatana.pattern.api.codegen;

import com.ghatana.eventcore.domain.EventRecord;
import com.ghatana.pattern.api.exception.EventCloudSerializationException;

import java.util.Collection;
import java.util.List;

/**
 * Serializes generated event types to/from EventCloud records.
 *
 * @param <T> concrete event type handled by this serializer
 */
public interface EventCloudSerializer<T> {

    /**
     * Serializes a single generated event into an {@link EventRecord}.
     */
    EventRecord serialize(T event) throws EventCloudSerializationException;

    /**
     * Serializes a batch of generated events.
     */
    List<EventRecord> serializeBatch(Collection<T> events) throws EventCloudSerializationException;

    /**
     * Rehydrates a generated event from an {@link EventRecord}.
     */
    T deserialize(EventRecord record) throws EventCloudSerializationException;

    /**
     * Rehydrates a batch of generated events.
     */
    List<T> deserializeBatch(List<EventRecord> records) throws EventCloudSerializationException;
}
