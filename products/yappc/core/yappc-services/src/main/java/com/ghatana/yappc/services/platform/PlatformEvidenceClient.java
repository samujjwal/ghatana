/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.services.platform;

import com.ghatana.yappc.api.PlatformEvidence;

import java.util.List;

/**
 * Typed client for platform evidence operations.
 * Handles communication with Data Cloud+AEP evidence storage and retrieval services.
 *
 * @doc.type interface
 * @doc.purpose Typed client for platform evidence operations
 * @doc.layer product
 * @doc.pattern Client
 */
public interface PlatformEvidenceClient {

    /**
     * Stores evidence from a platform execution.
     *
     * @param evidence The evidence to store
     * @return true if successful, false otherwise
     */
    boolean storeEvidence(PlatformEvidence evidence);

    /**
     * Searches for evidence (retrieval).
     *
     * @param query The search query
     * @return List of search results
     */
    List<PlatformEvidence.SearchResult> searchEvidence(PlatformEvidence.SearchQuery query);
}
