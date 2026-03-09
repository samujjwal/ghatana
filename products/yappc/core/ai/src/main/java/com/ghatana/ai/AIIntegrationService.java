package com.ghatana.ai;

import io.activej.promise.Promise;

/**
 * AIIntegrationService.
 *
 * @doc.type interface
 * @doc.purpose a i integration service
 * @doc.layer infrastructure
 * @doc.pattern Service
 */
public interface AIIntegrationService {
    String generateCode(String prompt);
    Promise<String> complete(String prompt);
}
