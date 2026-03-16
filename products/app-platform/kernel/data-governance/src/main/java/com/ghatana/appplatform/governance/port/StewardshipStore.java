/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.governance.port;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Repository port for stewardship assignment persistence.
 *
 * @doc.type interface
 * @doc.purpose Abstracts steward assignment and action log persistence from domain service
 * @doc.layer product
 * @doc.pattern Port
 */
public interface StewardshipStore {

    void upsertDomainAssignment(String assignmentId, String domainId, String stewardId,
                                int slaDays, Instant slaDeadline) throws Exception;

    void upsertAssetAssignment(String assignmentId, String domainId, String assetId,
                               String stewardId, int slaDays, Instant slaDeadline) throws Exception;

    void insertAction(String actionId, String assignmentId, String stewardId,
                      String actionType) throws Exception;

    void escalateAssignment(String assignmentId) throws Exception;

    Optional<String> resolveEffectiveSteward(String assetId, String domainId) throws Exception;

    List<Map<String, String>> fetchOverdueAssignments() throws Exception;
}
