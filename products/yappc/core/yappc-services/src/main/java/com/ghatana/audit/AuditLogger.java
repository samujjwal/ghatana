package com.ghatana.audit;

import io.activej.promise.Promise;
import java.util.Map;

/**
 * Minimal audit logger contract for lifecycle services.
 *
 * <p>P1-10: Removed no-op default to ensure production audit is always configured.
 * Controllers must receive a real AuditLogger instance from DI, never a no-op.
 *
 * @doc.type interface
 * @doc.purpose Defines the contract for audit logger
 * @doc.layer core
 * @doc.pattern ValueObject
* @doc.gaa.lifecycle perceive
*/
public interface AuditLogger {
  Promise<Void> log(Map<String, Object> event);
}
