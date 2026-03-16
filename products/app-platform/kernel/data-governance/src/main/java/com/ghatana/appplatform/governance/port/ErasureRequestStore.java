/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.governance.port;

import com.ghatana.appplatform.governance.RightToErasureHandlerService.ErasureStatus;
import com.ghatana.appplatform.governance.RightToErasureHandlerService.ProofCertificate;

import java.time.Instant;
import java.util.Optional;

/**
 * Repository port for erasure request persistence.
 *
 * @doc.type interface
 * @doc.purpose Abstracts GDPR erasure request persistence from domain service
 * @doc.layer product
 * @doc.pattern Port
 */
public interface ErasureRequestStore {

    void persistRequest(String requestId, String clientId, ErasureStatus status,
                        String holdReason, Instant initiatedAt) throws Exception;

    void markInProgress(String requestId) throws Exception;

    void persistCompletion(String requestId, ErasureStatus status,
                           ProofCertificate cert, Instant completedAt) throws Exception;

    Optional<ErasureRequestRow> findRequest(String requestId) throws Exception;

    record ErasureRequestRow(String clientId, ErasureStatus status, String holdReason,
                             Instant initiatedAt, Instant completedAt) {}
}
