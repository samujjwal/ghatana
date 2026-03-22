package com.ghatana.statestore.core;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.activej.promise.Promise;

/**
 * Core interface for durable state storage with TTL and checkpointing support.
 * 
 * Day 27 Implementation: Supports both RocksDB and Redis backends with 
 * consistent API for pattern engine state persistence.
 */
public interface StateStore {
    
    /**
     * Store a key-value pair with optional TTL.
     * 
     * @param key The storage key
     * @param value The value to store (will be serialized)
     * @param ttl Optional time-to-live duration
     * @return Promise that completes when stored
     */
    Promise<Void> put(String key, Object value, Optional<Duration> ttl);
    
    /**
     * Store a key-value pair without TTL (persistent until deleted).
     */
    default Promise<Void> put(String key, Object value) {
        return put(key, value, Optional.empty());
    }
    
    /**
     * Retrieve a value by key with type safety.
     * 
     * @param key The storage key
     * @param valueType The expected type of the value
     * @return Promise of optional value (empty if not found or expired)
     */
    <T> Promise<Optional<T>> get(String key, Class<T> valueType);
    
    /**
     * Retrieve multiple values by keys.
     * 
     * @param keys Set of keys to retrieve
     * @param valueType Expected type of values
     * @return Promise of map containing found key-value pairs
     */
    <T> Promise<Map<String, T>> getAll(Set<String> keys, Class<T> valueType);
    
    /**
     * Check if a key exists in the store.
     */
    Promise<Boolean> exists(String key);
    
    /**
     * Delete a key from the store.
     */
    Promise<Boolean> delete(String key);
    
    /**
     * Delete multiple keys from the store.
     * 
     * @return Promise of number of keys actually deleted
     */
    Promise<Long> deleteAll(Set<String> keys);
    
    /**
     * Get all keys matching a prefix pattern.
     * 
     * @param prefix The key prefix to match
     * @param limit Maximum number of keys to return (0 for no limit)
     * @return Promise of set of matching keys
     */
    Promise<Set<String>> getKeysByPrefix(String prefix, int limit);
    
    /**
     * Update TTL for an existing key.
     * 
     * @param key The key to update
     * @param ttl New TTL duration
     * @return Promise of true if key existed and TTL was updated
     */
    Promise<Boolean> updateTTL(String key, Duration ttl);
    
    /**
     * Get remaining TTL for a key.
     * 
     * @param key The key to check
     * @return Promise of optional duration (empty if no TTL or key not found)
     */
    Promise<Optional<Duration>> getRemainingTTL(String key);
    
    /**
     * Create a checkpoint of current state.
     * 
     * @param checkpointId Unique identifier for this checkpoint
     * @return Promise that completes when checkpoint is created
     */
    Promise<Void> createCheckpoint(String checkpointId);
    
    /**
     * Restore state from a checkpoint.
     * 
     * @param checkpointId The checkpoint to restore from
     * @return Promise that completes when restoration is finished
     */
    Promise<Void> restoreFromCheckpoint(String checkpointId);
    
    /**
     * List available checkpoints.
     * 
     * @return Promise of map of checkpoint ID to creation timestamp
     */
    Promise<Map<String, Instant>> listCheckpoints();
    
    /**
     * Delete a checkpoint.
     * 
     * @param checkpointId The checkpoint to delete
     * @return Promise of true if checkpoint existed and was deleted
     */
    Promise<Boolean> deleteCheckpoint(String checkpointId);
    
    /**
     * Get approximate size of stored data in bytes.
     */
    Promise<Long> getStorageSize();
    
    /**
     * Get statistics about the state store.
     */
    Promise<StateStoreStats> getStats();
    
    /**
     * Perform compaction/cleanup operations.
     * This may be a no-op for some implementations.
     */
    Promise<Void> compact();
    
    /**
     * Close the state store and release resources.
     */
    Promise<Void> close();
    
    /**
     * Check if the state store is healthy and ready for operations.
     */
    Promise<Boolean> isHealthy();
}