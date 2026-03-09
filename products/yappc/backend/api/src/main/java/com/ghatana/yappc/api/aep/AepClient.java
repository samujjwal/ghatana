/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module - AEP Integration
 */
package com.ghatana.yappc.api.aep;

/**
 * AEP Client Interface.
 *
 * <p><b>Purpose</b><br>
 * Provides a unified interface for communicating with AEP (Agentic Event Processor) regardless of
 * whether it runs as a library or external service.
 *
 * <p><b>Architecture</b><br>
 * Two implementations:
 * - AepLibraryClient: Direct in-process library calls
 * - AepServiceClient: HTTP-based remote calls
 *
 * <p><b>Backend Responsibilities</b><br>
 * All AEP communication happens in the backend. The frontend never talks to AEP directly.
 *
 * @see AepLibraryClient
 * @see AepServiceClient
  *
 * @doc.type interface
 * @doc.purpose aep client
 * @doc.layer product
 * @doc.pattern Client
 */
public interface AepClient {

  /**
   * Publishes an event to AEP for processing.
   *
   * @param eventType The type of event (e.g., "shape.created", "frame.moved")
   * @param payload The event data as JSON
   * @return Event ID assigned by AEP
   * @throws AepException if publishing fails
   */
  String publishEvent(String eventType, String payload) throws AepException;

  /**
   * Queries events from AEP.
   *
   * @param query The event query (e.g., filter by type, time range)
   * @return JSON array of matching events
   * @throws AepException if query fails
   */
  String queryEvents(String query) throws AepException;

  /**
   * Executes an agentic action through AEP.
   *
   * @param action The action name (e.g., "auto-layout", "suggest-organization")
   * @param context The action context as JSON
   * @return Action result as JSON
   * @throws AepException if execution fails
   */
  String executeAction(String action, String context) throws AepException;

  /**
   * Gets the health status of AEP.
   *
   * @return "healthy" or "unhealthy"
   * @throws AepException if health check fails
   */
  String healthCheck() throws AepException;

  /**
   * Gracefully closes the AEP client.
   */
  void close();
}
