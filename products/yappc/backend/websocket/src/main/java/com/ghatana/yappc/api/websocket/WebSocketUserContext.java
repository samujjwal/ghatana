/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Backend - WebSocket Module
 */
package com.ghatana.yappc.api.websocket;

import org.jetbrains.annotations.NotNull;
import java.util.List;
import java.util.Objects;

/**
 * Lightweight user context for WebSocket connections.
 *
 * <p>This is an intentionally minimal representation carrying only the data
 * needed by the WebSocket layer (identity, tenant, admin flags). It avoids a
 * circular dependency between the {@code websocket} module and the {@code api}
 * module.  The API layer is responsible for constructing this from its own
 * {@code UserContext} before passing it to
 * {@link LifecycleWebSocketHandler#handleConnection}.
 *
 * @param userId    the unique user identifier (non-null)
 * @param tenantId  the tenant the user belongs to (non-null)
 * @param roles     list of role names (non-null, may be empty)
 * @param admin     whether the user has global admin rights
 * @param tenantAdmin whether the user has tenant-scoped admin rights
 *
 * @doc.type record
 * @doc.purpose Minimal user identity for WebSocket connection management
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record WebSocketUserContext(
    @NotNull String userId,
    @NotNull String tenantId,
    @NotNull List<String> roles,
    boolean admin,
    boolean tenantAdmin) {

  public WebSocketUserContext {
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(tenantId, "tenantId");
    Objects.requireNonNull(roles, "roles");
    roles = List.copyOf(roles);
  }

  /** Returns {@code true} if this user has global administrator privileges. */
  public boolean isAdmin() {
    return admin;
  }

  /** Returns {@code true} if this user has tenant-scoped administrator privileges. */
  public boolean isTenantAdmin() {
    return tenantAdmin;
  }

  /** Alias for {@link #userId()} — matches the {@code UserContext.getUserId()} API. */
  public String getUserId() {
    return userId;
  }

  /** Convenience factory for tests and adapters. */
  public static WebSocketUserContext of(String userId, String tenantId) {
    return new WebSocketUserContext(userId, tenantId, List.of(), false, false);
  }
}
