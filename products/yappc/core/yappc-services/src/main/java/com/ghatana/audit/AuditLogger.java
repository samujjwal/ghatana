package com.ghatana.audit;

import io.activej.promise.Promise;
import java.util.Map;

/**
 * Minimal audit logger contract for lifecycle services.
 * Kept in compatibility package to preserve existing imports.
 
 * @doc.type interface
 * @doc.purpose Defines the contract for audit logger
 * @doc.layer core
 * @doc.pattern ValueObject
* @doc.gaa.lifecycle perceive
*/
public interface AuditLogger {
  Promise<Void> log(Map<String, Object> event);

  static AuditLogger noop() {
    return event -> Promise.complete();
  }
}
