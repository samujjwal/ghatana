package com.ghatana.platform.security.rbac;

import com.ghatana.platform.governance.security.Principal;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Filter that enforces RBAC for HTTP endpoints.
 * Uses PolicyService for modern policy-based access control.
 
 *
 * @doc.type class
 * @doc.purpose Rbacfilter
 * @doc.layer core
 * @doc.pattern Filter
*/
public class RBACFilter {
    private static final Logger log = LoggerFactory.getLogger(RBACFilter.class);
    
    private final PolicyService policyService;
    private final String permission;
    private final String resource;

    public RBACFilter(PolicyService policyService, String permission, String resource) {
        this.policyService = policyService;
        this.permission = permission;
        this.resource = resource;
    }

    public AsyncServlet secure(AsyncServlet delegate) {
        return request -> {
            Principal principal = request.getAttachment(Principal.class);
            if (principal == null) {
                log.warn("No principal found in request");
                return Promise.of(unauthorized("Missing or invalid credentials"));
            }

            try {
                if (!policyService.isAuthorized(principal, permission, resource)) {
                    log.warn("Principal {} is not authorized for {}{}", 
                             principal.getTenantId(), 
                             permission,
                             resource != null ? ":" + resource : "");
                    return Promise.of(forbidden("Insufficient permissions"));
                }

                return delegate.serve(request);
            } catch (Exception e) {
                log.error("Error during authorization check", e);
                return Promise.of(forbidden("Authorization check failed"));
            }
        };
    }

    private static HttpResponse unauthorized(String message) {
        return HttpResponse.ofCode(401)
                .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .withJson(String.format("{\"code\":\"UNAUTHORIZED\",\"message\":\"%s\"}", message))
                .build();
    }

    private static HttpResponse forbidden(String message) {
        return HttpResponse.ofCode(403)
                .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .withJson(String.format("{\"code\":\"FORBIDDEN\",\"message\":\"%s\"}", message))
                .build();
    }
}
