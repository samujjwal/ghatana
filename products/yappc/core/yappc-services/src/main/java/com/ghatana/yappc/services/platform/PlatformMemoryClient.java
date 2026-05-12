/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.services.platform;

import com.ghatana.yappc.api.PlatformMemory;

/**
 * Typed client for platform memory operations.
 * Handles communication with Data Cloud+AEP memory storage and retrieval services.
 *
 * @doc.type interface
 * @doc.purpose Typed client for platform memory operations
 * @doc.layer product
 * @doc.pattern Client
 */
public interface PlatformMemoryClient {

    /**
     * Stores memory in the platform (write proposal).
     *
     * @param memory The memory to store
     * @return true if successful, false otherwise
     */
    boolean storeMemory(PlatformMemory memory);

    /**
     * Retrieves memory summary from the platform.
     *
     * @param memoryId The memory ID
     * @return PlatformMemory containing the memory summary
     */
    PlatformMemory retrieveMemorySummary(String memoryId);

    /**
     * Retrieves full memory from the platform.
     *
     * @param memoryId The memory ID
     * @return PlatformMemory containing the memory data
     */
    PlatformMemory retrieveMemory(String memoryId);

    /**
     * Deletes memory from the platform.
     *
     * @param memoryId The memory ID
     * @return true if successful, false otherwise
     */
    boolean deleteMemory(String memoryId);
}
