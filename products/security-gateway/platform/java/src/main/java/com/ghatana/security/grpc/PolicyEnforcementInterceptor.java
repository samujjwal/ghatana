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

import com.ghatana.security.audit.AuditEmitter;
import com.ghatana.security.govern.IamPolicyEnforcer;
import com.ghatana.security.govern.PolicyEnforcementResult;
import com.ghatana.platform.security.model.User;
import com.ghatana.platform.audit.AuditEvent;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.ForwardingServerCall;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.UUID;

/**
 * gRPC interceptor that enforces IAM policies for pattern learning endpoints.
 * Provides authentication, authorization, and audit logging for gRPC service calls.

 *
 * @doc.type class
 * @doc.purpose Policy enforcement interceptor
 * @doc.layer core
 * @doc.pattern Interceptor
*/
public class PolicyEnforcementInterceptor implements ServerInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(PolicyEnforcementInterceptor.class);

    // gRPC context keys for user information
    public static final Context.Key<User> USER_CONTEXT_KEY = Context.key("user");
    public static final Context.Key<String> TENANT_CONTEXT_KEY = Context.key("tenantId");
    public static final Context.Key<String> CORRELATION_CONTEXT_KEY = Context.key("correlationId");

    // Metadata keys for authentication
    private static final Metadata.Key<String> AUTHORIZATION_HEADER =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> TENANT_ID_HEADER =
            Metadata.Key.of("x-tenant-id", Metadata.ASCII_STRING_MARSHALLER);
        private static final Metadata.Key<String> TRACE_ID_HEADER =
            Metadata.Key.of("x-trace-id", Metadata.ASCII_STRING_MARSHALLER);
        private static final Metadata.Key<String> CORRELATION_ID_HEADER =
            Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER);
        private static final String CORRELATION_ID_MDC_KEY = "correlationId";

    private final IamPolicyEnforcer policyEnforcer;
    private final AuditEmitter auditEmitter;
    private final UserAuthenticationService userAuthService;

    public PolicyEnforcementInterceptor(IamPolicyEnforcer policyEnforcer,
                                       AuditEmitter auditEmitter,
                                       UserAuthenticationService userAuthService) {
        this.policyEnforcer = policyEnforcer;
        this.auditEmitter = auditEmitter;
        this.userAuthService = userAuthService;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String methodName = call.getMethodDescriptor().getFullMethodName();
        String correlationId = resolveCorrelationId(headers);
        ServerCall<ReqT, RespT> correlatedCall = wrapCallWithCorrelation(call, correlationId);
        logger.debug("Intercepting gRPC call: {} correlationId={}", methodName, correlationId);
        MDC.put(CORRELATION_ID_MDC_KEY, correlationId);

        try {
            // Extract authentication information from headers
            String authToken = headers.get(AUTHORIZATION_HEADER);
            String tenantId = headers.get(TENANT_ID_HEADER);

            // Authenticate user
            User user = authenticateUser(authToken);
            if (user == null) {
                correlatedCall.close(Status.UNAUTHENTICATED.withDescription("Authentication required"), new Metadata());
                return new ServerCall.Listener<ReqT>() { };
            }

            // Validate tenant ID
            if (tenantId == null || tenantId.trim().isEmpty()) {
                correlatedCall.close(Status.INVALID_ARGUMENT.withDescription("Tenant ID required"), new Metadata());
                return new ServerCall.Listener<ReqT>() { };
            }

            // Authorize based on method
            PolicyEnforcementResult authzResult = authorizeMethod(user, tenantId, methodName);
            if (!authzResult.isGranted()) {
                // Emit audit event for denied access
                if (authzResult.getAuditEvent() != null) {
                    auditEmitter.emitAuditEvent(authzResult.getAuditEvent());
                }

                correlatedCall.close(Status.PERMISSION_DENIED.withDescription(authzResult.getReason()), new Metadata());
                return new ServerCall.Listener<ReqT>() { };
            }

            // Emit audit event for granted access
            if (authzResult.getAuditEvent() != null) {
                auditEmitter.emitAuditEvent(authzResult.getAuditEvent());
            }

            // Continue with the call, setting user and tenant context
            Context contextWithUser = Context.current()
                    .withValue(USER_CONTEXT_KEY, user)
                    .withValue(TENANT_CONTEXT_KEY, tenantId)
                    .withValue(CORRELATION_CONTEXT_KEY, correlationId);

            return Contexts.interceptCall(contextWithUser, correlatedCall, headers, next);

        } catch (Exception e) {
            logger.error("Policy enforcement failed for method: {}", methodName, e);
            correlatedCall.close(Status.INTERNAL.withDescription("Security check failed"), new Metadata());
            return new ServerCall.Listener<ReqT>() { };
        } finally {
            MDC.remove(CORRELATION_ID_MDC_KEY);
        }
    }

    private String resolveCorrelationId(Metadata headers) {
        String correlationId = headers.get(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.trim().isEmpty()) {
            correlationId = headers.get(TRACE_ID_HEADER);
        }
        if (correlationId == null || correlationId.trim().isEmpty()) {
            return UUID.randomUUID().toString();
        }
        return correlationId;
    }

    private <ReqT, RespT> ServerCall<ReqT, RespT> wrapCallWithCorrelation(
            ServerCall<ReqT, RespT> delegate,
            String correlationId
    ) {
        return new ForwardingServerCall.SimpleForwardingServerCall<>(delegate) {
            @Override
            public void sendHeaders(Metadata headers) {
                headers.put(CORRELATION_ID_HEADER, correlationId);
                super.sendHeaders(headers);
            }

            @Override
            public void close(Status status, Metadata trailers) {
                trailers.put(CORRELATION_ID_HEADER, correlationId);
                super.close(status, trailers);
            }
        };
    }

    /**
     * Authenticates a user based on the provided authentication token.
     */
    private User authenticateUser(String authToken) {
        if (authToken == null || authToken.trim().isEmpty()) {
            return null;
        }

        // Remove "Bearer " prefix if present
        if (authToken.startsWith("Bearer ")) {
            authToken = authToken.substring(7);
        }

        try {
            return userAuthService.authenticate(authToken);
        } catch (Exception e) {
            logger.debug("Authentication failed for token", e);
            return null;
        }
    }

    /**
     * Authorizes a user to access a specific gRPC method.
     */
    private PolicyEnforcementResult authorizeMethod(User user, String tenantId, String methodName) {
        // Map gRPC method names to policy enforcement
        switch (methodName) {
            case "ghatana.contracts.learning.v1.PatternLearningService/RecommendPatterns":
                return policyEnforcer.enforcePatternRecommendPolicy(user, tenantId, "patterns/recommend");

            case "ghatana.contracts.learning.v1.PatternLearningService/EvaluatePatterns":
                return policyEnforcer.enforcePatternEvaluatePolicy(user, tenantId, "patterns/evaluate");

            default:
                // For other methods, check if user is authenticated
                if (user.isAuthenticated()) {
                    return PolicyEnforcementResult.granted("Method allowed for authenticated users");
                } else {
                    return PolicyEnforcementResult.denied("Authentication required");
                }
        }
    }

    /**
     * Helper method to emit audit events for pattern learning operations.
     */
    private Promise<Void> emitAuditEvent(AuditEvent auditEvent) {
        return auditEmitter.emitAuditEvent(auditEvent);
    }

    /**
     * Extracts user from current gRPC context.
     * This can be used by service implementations to get the authenticated user.
     */
    public static User getCurrentUser() {
        return USER_CONTEXT_KEY.get();
    }

    /**
     * Extracts tenant ID from current gRPC context.
     * This can be used by service implementations to get the tenant ID.
     */
    public static String getCurrentTenantId() {
        return TENANT_CONTEXT_KEY.get();
    }

    /**
     * Extracts correlation ID from current gRPC context.
     * This can be used by service implementations to link logs and downstream calls.
     */
    public static String getCurrentCorrelationId() {
        return CORRELATION_CONTEXT_KEY.get();
    }
}
