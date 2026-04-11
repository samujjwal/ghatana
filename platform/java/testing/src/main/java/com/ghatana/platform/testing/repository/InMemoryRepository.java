package com.ghatana.platform.testing.repository;

import com.ghatana.core.database.repository.Repository;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Generic in-memory repository implementation for testing.
 *
 * @doc.type class
 * @doc.purpose Provides an in-memory repository implementation for testing
 * @doc.layer platform
 * @doc.pattern Repository
 * @param <T> Entity type
 * @param <ID> Entity identifier type
 */
public class InMemoryRepository<T, ID extends Serializable> implements Repository<T, ID> {

    protected final Map<ID, T> storage = new ConcurrentHashMap<>();
    protected final Function<T, ID> idExtractor;

    public InMemoryRepository(Function<T, ID> idExtractor) {
        this.idExtractor = Objects.requireNonNull(idExtractor, "idExtractor must not be null");
    }

    @Override
    public long count() {
        return storage.size();
    }

    @Override
    public <S extends T> S save(S entity) {
        Objects.requireNonNull(entity, "entity must not be null");
        ID id = idExtractor.apply(entity);
        Objects.requireNonNull(id, "Entity ID must not be null");
        storage.put(id, entity);
        return entity;
    }

    @Override
    public void deleteById(ID id) {
        Objects.requireNonNull(id, "id must not be null");
        storage.remove(id);
    }

    @Override
    public void delete(T entity) {
        Objects.requireNonNull(entity, "entity must not be null");
        ID id = idExtractor.apply(entity);
        storage.remove(id);
    }

    @Override
    public boolean existsById(ID id) {
        Objects.requireNonNull(id, "id must not be null");
        return storage.containsKey(id);
    }

    @Override
    public List<T> findAll() {
        return new ArrayList<>(storage.values());
    }

    @Override
    public Optional<T> findById(ID id) {
        Objects.requireNonNull(id, "id must not be null");
        return Optional.ofNullable(storage.get(id));
    }

    // Additional methods for testing (not in Repository interface)

    /**
     * Delete all entities.
     */
    public void deleteAll() {
        storage.clear();
    }

    /**
     * Delete all entities in the iterable.
     */
    public void deleteAll(Iterable<? extends T> entities) {
        Objects.requireNonNull(entities, "entities must not be null");
        for (T entity : entities) {
            delete(entity);
        }
    }

    /**
     * Delete all entities by their IDs.
     */
    public void deleteAllById(Iterable<? extends ID> ids) {
        Objects.requireNonNull(ids, "ids must not be null");
        for (ID id : ids) {
            deleteById(id);
        }
    }

    /**
     * Find all entities by their IDs.
     */
    public List<T> findAllById(Iterable<ID> ids) {
        Objects.requireNonNull(ids, "ids must not be null");
        List<T> results = new ArrayList<>();
        for (ID id : ids) {
            findById(id).ifPresent(results::add);
        }
        return results;
    }

    /**
     * Save all entities.
     */
    public List<T> saveAll(Iterable<T> entities) {
        Objects.requireNonNull(entities, "entities must not be null");
        List<T> results = new ArrayList<>();
        for (T entity : entities) {
            results.add(save(entity));
        }
        return results;
    }

    /**
     * Find entities matching the given predicate.
     */
    public List<T> findByPredicate(Predicate<T> predicate) {
        return storage.values().stream()
                .filter(predicate)
                .collect(Collectors.toList());
    }

    /**
     * Find a single entity matching the predicate.
     */
    public Optional<T> findOneByPredicate(Predicate<T> predicate) {
        return storage.values().stream()
                .filter(predicate)
                .findFirst();
    }

    /**
     * Check if any entity matches the predicate.
     */
    public boolean exists(Predicate<T> predicate) {
        return storage.values().stream().anyMatch(predicate);
    }

    /**
     * Get all IDs in the repository.
     */
    public Set<ID> findAllIds() {
        return new HashSet<>(storage.keySet());
    }

    /**
     * Reset the repository (clear all data).
     */
    public void reset() {
        storage.clear();
    }

    /**
     * Get the current number of entities.
     */
    public int size() {
        return storage.size();
    }

    /**
     * Check if repository is empty.
     */
    public boolean isEmpty() {
        return storage.isEmpty();
    }
}
