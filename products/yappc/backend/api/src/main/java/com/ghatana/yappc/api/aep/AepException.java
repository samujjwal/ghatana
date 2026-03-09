/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module - AEP Integration
 */
package com.ghatana.yappc.api.aep;

/**
 * Exception thrown when AEP (Agentic Event Processor) operations fail.
 *
 * <p>Indicates issues with:
 * - AEP library initialization (LIBRARY mode)
 * - AEP service communication (SERVICE mode)
 * - Event processing or action execution
  *
 * @doc.type class
 * @doc.purpose aep exception
 * @doc.layer product
 * @doc.pattern Exception
 */
public class AepException extends Exception {

  private static final long serialVersionUID = 1L;

  public AepException(String message) {
    super(message);
  }

  public AepException(String message, Throwable cause) {
    super(message, cause);
  }

  public AepException(Throwable cause) {
    super(cause);
  }
}
