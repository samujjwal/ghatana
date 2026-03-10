/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.infrastructure.policy;

import com.ghatana.governance.PolicyEngine;
import io.activej.promise.Promise;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fall-through PolicyEngine implementation that permits all requests.
 *
 * <p><b>Purpose</b><br>
 * Used when OPA is unavailable or not yet configured. All policy checks
 * pass with a WARN-level log so operations stay visible in the audit trail
 * even while real enforcement is pending.
 *
 * <p><b>Usage</b><br>
 * Wire this engine via DI when {@code OPA_ENDPOINT} is absent:
 * <pre>{@code
 * PolicyEngine engine = new PermissivePolicyEngine();
 * engine.evaluate("require_approval", ctx).toCompletableFuture().get(); // → true
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Stateless — fully thread-safe.
 *
 * @doc.type class
 * @doc.purpose Permissive fallback PolicyEngine; allows all while logging
 * @doc.layer api
 * @doc.pattern Null Object, Fallback
 */
public final class PermissivePolicyEngine implements PolicyEngine {

  private static final Logger logger = LoggerFactory.getLogger(PermissivePolicyEngine.class);

  public PermissivePolicyEngine() {
    logger.warn(
        "⚠️  PermissivePolicyEngine active — all policy checks are ALLOWED."
            + " Set OPA_ENDPOINT to enable real OPA enforcement.");
  }

  /**
   * {@inheritDoc}
   *
   * <p>Always returns {@code true}. Logs the policy name and context so operators
   * can audit what would be evaluated once a real engine is wired.
   */
  @Override
  @NotNull
  public Promise<Boolean> evaluate(
      @NotNull String policyName, @NotNull Map<String, Object> context) {
    logger.warn(
        "[PERMISSIVE] Policy '{}' would be evaluated against context keys={}; ALLOWED by default.",
        policyName, context.keySet());
    return Promise.of(Boolean.TRUE);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Always returns {@code false} — the permissive engine does not know about concrete policies
   * and treats every policy as absent, relying on the permissive allow-all default.
   */
  @Override
  @NotNull
  public Promise<Boolean> policyExists(@NotNull String policyName) {
    return Promise.of(Boolean.FALSE);
  }
}
