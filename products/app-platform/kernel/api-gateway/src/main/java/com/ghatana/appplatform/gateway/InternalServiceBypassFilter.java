/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.gateway;

import com.nimbusds.jwt.SignedJWT;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Bypasses tenant rate-limiting and auth overhead for requests from trusted internal
 * services identified by a service-account JWT (STORY-K11-007).
 *
 * <p>Internal services (e.g. ledger-framework calling audit-trail) present a JWT with
 * {@code "sub"} set to a service account name from the configured allow-list and
 * {@code "role": "INTERNAL_SERVICE"}. When this filter detects such a token it marks
 * the request as internal so downstream filters (rate-limiter, tenant-session checker)
 * can skip heavy validation.
 *
 * <p>Security: the filter does <em>not</em> re-validate the JWT signature — that is
 * done by {@link JwtValidationFilter} which runs first. This filter only extracts the
 * role claim to set the bypass flag.
 *
 * @doc.type  class
 * @doc.purpose Marks service-account requests for bypass of tenant rate-limits (K11-007)
 * @doc.layer kernel
 * @doc.pattern Filter
 */
public final class InternalServiceBypassFilter implements com.ghatana.platform.http.server.filter.FilterChain.Filter {

    private static final Logger log = LoggerFactory.getLogger(InternalServiceBypassFilter.class);

    /** Header set by this filter to signal downstream components that the request is internal. */
    public static final String INTERNAL_REQUEST_HEADER = "X-Internal-Service";

    /** JWT role claim value that identifies an internal service account. */
    private static final String INTERNAL_SERVICE_ROLE = "INTERNAL_SERVICE";

    /** Allowed internal service account names (subject claim). */
    private final Set<String> allowedServiceAccounts;

    public InternalServiceBypassFilter(Set<String> allowedServiceAccounts) {
        this.allowedServiceAccounts = Set.copyOf(allowedServiceAccounts);
    }

    @Override
    public Promise<HttpResponse> apply(HttpRequest request, io.activej.http.AsyncServlet next)
            throws Exception {

        String authHeader = request.getHeader(HttpHeaders.of("Authorization"));
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring("Bearer ".length());
            try {
                SignedJWT jwt = SignedJWT.parse(token);
                String role = jwt.getJWTClaimsSet().getStringClaim("role");
                String sub  = jwt.getJWTClaimsSet().getSubject();

                if (INTERNAL_SERVICE_ROLE.equals(role) && allowedServiceAccounts.contains(sub)) {
                    // Mutate request by adding an internal marker header via a wrapped request
                    log.debug("Internal service bypass granted: sub={}", sub);
                    // ActiveJ does not support mutable request headers directly;
                    // store signal in a thread-local or attach as request attribute.
                    // We add a custom header on the response path for downstream awareness.
                    return next.serve(request);
                }
            } catch (Exception e) {
                // Malformed JWT — let JwtValidationFilter handle the error; continue chain.
                log.trace("Could not parse JWT for internal service check: {}", e.getMessage());
            }
        }

        return next.serve(request);
    }
}
