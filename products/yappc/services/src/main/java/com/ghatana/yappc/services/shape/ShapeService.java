package com.ghatana.yappc.services.shape;

import io.activej.promise.Promise;

/**
 * Service for managing software shapes (components, services, etc.) in YAPPC.
 *
 * @doc.type interface
 * @doc.purpose Contract for shape management operations
 * @doc.layer product
 * @doc.pattern Service
 */
public interface ShapeService {

    /**
     * Counts the total number of shapes.
     *
     * @return promise of the total shape count
     */
    Promise<Long> count();
}
