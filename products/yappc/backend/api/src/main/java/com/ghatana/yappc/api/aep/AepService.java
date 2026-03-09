/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module - AEP Integration
 */
package com.ghatana.yappc.api.aep;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AEP Service - Manages communication with AEP (Agentic Event Processor).
 *
 * <p><b>Purpose</b><br>
 * Provides backend service layer for AEP integration. This is the single point where all AEP
 * operations happen:
 * - Publishing events
 * - Querying events
 * - Executing agentic actions
 *
 * <p><b>Architecture</b><br>
 * - Uses AepClient interface (library or service mode)
 * - Handles errors and logging
 * - Can be injected into controllers
 *
 * <p><b>Usage in Controllers</b><br>
 *
 * <pre>
 * @Provides
 * static AepService aepService(AepClient aepClient) {
 *   return new AepService(aepClient);
 * }
 *
 * // In controller:
 * @Inject AepService aepService;
 *
 * String eventId = aepService.publishShapeCreatedEvent(shapeData);
 * </pre>
 *
 * @see AepClient
 * @see AepConfig
  *
 * @doc.type class
 * @doc.purpose aep service
 * @doc.layer product
 * @doc.pattern Service
 */
public class AepService {

  private static final Logger LOG = LoggerFactory.getLogger(AepService.class);
  private final AepClient client;

  public AepService(AepClient client) {
    this.client = client;
  }

  /**
   * Publishes a generic event to AEP.
   *
   * @param eventType event type identifier
   * @param payload event payload as JSON
   * @return Event ID
   * @throws AepException if publishing fails
   */
  public String publishEvent(String eventType, String payload) throws AepException {
    LOG.debug("Publishing event type={}", eventType);
    return client.publishEvent(eventType, payload);
  }

  /**
   * Publishes a shape created event to AEP.
   *
   * @param shapeData Shape data as JSON
   * @return Event ID
   * @throws AepException if publishing fails
   */
  public String publishShapeCreatedEvent(String shapeData) throws AepException {
    LOG.debug("Publishing shape.created event: {}", shapeData);
    return client.publishEvent("shape.created", shapeData);
  }

  /**
   * Publishes a frame created event to AEP.
   *
   * @param frameData Frame data as JSON
   * @return Event ID
   * @throws AepException if publishing fails
   */
  public String publishFrameCreatedEvent(String frameData) throws AepException {
    LOG.debug("Publishing frame.created event: {}", frameData);
    return client.publishEvent("frame.created", frameData);
  }

  /**
   * Publishes a shape modified event to AEP.
   *
   * @param modificationData Modification data as JSON
   * @return Event ID
   * @throws AepException if publishing fails
   */
  public String publishShapeModifiedEvent(String modificationData) throws AepException {
    LOG.debug("Publishing shape.modified event: {}", modificationData);
    return client.publishEvent("shape.modified", modificationData);
  }

  /**
   * Executes an agentic action through AEP.
   *
   * <p><b>Supported Actions</b>:
   * - "auto-layout" - Automatic layout suggestion
   * - "suggest-organization" - Organization suggestion
   * - "detect-patterns" - Pattern detection in current canvas
   *
   * @param actionName The action to execute
   * @param contextJson The action context
   * @return Action result as JSON
   * @throws AepException if execution fails
   */
  public String executeAction(String actionName, String contextJson) throws AepException {
    LOG.debug("Executing action: {}", actionName);
    return client.executeAction(actionName, contextJson);
  }

  /**
   * Queries events from AEP.
   *
   * @param queryJson Query criteria as JSON
   * @return Matching events as JSON array
   * @throws AepException if query fails
   */
  public String queryEvents(String queryJson) throws AepException {
    LOG.debug("Querying events: {}", queryJson);
    return client.queryEvents(queryJson);
  }

  /**
   * Checks if AEP is healthy and available.
   *
   * @return true if healthy, false otherwise
   */
  public boolean isHealthy() {
    try {
      String status = client.healthCheck();
      boolean healthy = "healthy".equalsIgnoreCase(status);
      if (!healthy) {
        LOG.warn("AEP health check returned: {}", status);
      }
      return healthy;
    } catch (AepException e) {
      LOG.error("AEP health check failed", e);
      return false;
    }
  }

  /**
   * Closes the AEP client connection/resources.
   */
  public void close() {
    try {
      client.close();
    } catch (Exception e) {
      LOG.error("Error closing AEP client", e);
    }
  }
}
