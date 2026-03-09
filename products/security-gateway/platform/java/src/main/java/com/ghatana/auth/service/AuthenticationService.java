package com.ghatana.auth.service;

import com.ghatana.platform.domain.auth.AuthResult;
import com.ghatana.platform.domain.auth.Session;
import com.ghatana.platform.domain.auth.SessionId;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.domain.auth.User;
import com.ghatana.platform.domain.auth.UserId;
import io.activej.promise.Promise;

import java.util.Optional;

/**
 * Service interface for authentication operations.
 *
 * @doc.type interface
 * @doc.purpose Authentication service port for login, registration, session management
 * @doc.layer product
 * @doc.pattern Service
 */
public interface AuthenticationService {

    Promise<AuthResult> authenticate(TenantId tenantId, String email, String password);

    Promise<User> register(TenantId tenantId, String email, String password,
                           String displayName, String username);

    Promise<Void> logout(TenantId tenantId, SessionId sessionId);

    Promise<Boolean> validateSession(TenantId tenantId, SessionId sessionId);

    Promise<Session> refreshSession(TenantId tenantId, SessionId sessionId);

    Promise<Void> changePassword(TenantId tenantId, UserId userId,
                                  String currentPassword, String newPassword);

    Promise<Optional<String>> requestPasswordReset(TenantId tenantId, String email);
}
