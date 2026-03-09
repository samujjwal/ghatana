/*
 * Copyright (c) 2024 Ghatana Inc.
 * All rights reserved.
 *
 * This source code and the accompanying materials are the confidential
 * and proprietary information of Ghatana Inc. ("Confidential Information").
 * You shall not disclose such Confidential Information and shall use it
 * only in accordance with the terms of the license agreement you entered
 * into with Ghatana Inc.
 *
 * Unauthorized copying of this file, via any medium, is strictly prohibited.
 * Proprietary and confidential.
 */
package com.ghatana.security.grpc;

import com.ghatana.platform.security.model.User;

/**
 * Service interface for user authentication in gRPC interceptors.
 * Implementations handle token validation and user lookup.
 
 *
 * @doc.type interface
 * @doc.purpose User authentication service
 * @doc.layer core
 * @doc.pattern Service
*/
public interface UserAuthenticationService {
    
    /**
     * Authenticates a user based on the provided token.
     *
     * @param token the authentication token (JWT, API key, etc.)
     * @return the authenticated User object, or null if authentication fails
     * @throws AuthenticationException if authentication fails due to an error
     */
    User authenticate(String token) throws AuthenticationException;
    
    /**
     * Validates if a token is still valid (not expired, not revoked).
     *
     * @param token the token to validate
     * @return true if the token is valid, false otherwise
     */
    boolean validateToken(String token);
    
    /**
     * Extracts the user ID from a token without full authentication.
     * Used for audit logging when authentication fails.
     *
     * @param token the token to extract user ID from
     * @return the user ID if extractable, null otherwise
     */
    String extractUserId(String token);
}