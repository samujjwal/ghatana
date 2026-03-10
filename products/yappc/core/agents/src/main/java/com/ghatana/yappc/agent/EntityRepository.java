package com.ghatana.yappc.agent;

import io.activej.promise.Promise;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Data-Cloud entity repository adapter for SDLC workflow steps.
 *
 * <p>Implement using Data-Cloud Entity APIs (from libs:database or products:data-cloud). All
 * operations return ActiveJ Promise for non-blocking execution.
 *
 * @doc.type interface
 * @doc.purpose Repository adapter for Data-Cloud entity persistence
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface EntityRepository {

  /**
   * Upserts (insert or update) a document in a collection.
   *
   * @param collection target collection name
   * @param tenantId tenant identifier for isolation
   * @param id document ID
   * @param doc document data as a map
   * @return Promise that completes when upsert is done
   */
  Promise<Void> upsert(String collection, String tenantId, String id, Map<String, Object> doc);

  /**
   * Gets a document by ID.
   *
   * @param collection target collection name
   * @param tenantId tenant identifier for isolation
   * @param id document ID
   * @return Promise of optional document (empty if not found)
   */
  Promise<Optional<Map<String, Object>>> get(String collection, String tenantId, String id);

  /**
   * Queries documents with filter.
   *
   * @param collection target collection name
   * @param tenantId tenant identifier for isolation
   * @param filter query filter as map
   * @param limit maximum results to return
   * @return Promise of matching documents
   */
  Promise<List<Map<String, Object>>> query(
      String collection, String tenantId, Map<String, Object> filter, int limit);
}
