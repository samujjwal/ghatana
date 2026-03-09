/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.yappc.api.testing;

import static io.activej.http.HttpMethod.POST;

import com.ghatana.yappc.api.common.ApiResponse;
import com.ghatana.yappc.api.common.JsonUtils;
import com.ghatana.yappc.api.testing.dto.DependencyScanRequest;
import com.ghatana.yappc.api.testing.dto.SASTRequest;
import com.ghatana.yappc.api.testing.dto.SecurityScanRequest;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import io.activej.inject.annotation.Inject;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import io.activej.promise.Promise;
import io.activej.reactor.Reactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Security Testing Controller - Security scanning and vulnerability testing.
 * 
 * <p>Endpoints:
 * <ul>
 *   <li>POST /api/testing/security/scan - Run security scan</li>
 *   <li>POST /api/testing/security/dependencies - Scan dependencies for vulnerabilities</li>
 *   <li>POST /api/testing/security/sast - Run SAST analysis</li>
 * </ul>
 * 
 * @doc.type class
 * @doc.purpose Security testing API
 * @doc.layer product
 * @doc.pattern Controller
 */
public class SecurityTestController extends AbstractModule {
    
    private static final Logger log = LoggerFactory.getLogger(SecurityTestController.class);

    private static final String BASE_PATH = "/api/testing/security";
    private static final String BASE_PATH_V1 = "/api/v1/testing/security";
    
    private final SecurityTestService securityTestService;
    
    @Inject
    public SecurityTestController(SecurityTestService securityTestService) {
        this.securityTestService = securityTestService;
    }

    @Provides
    RoutingServlet securityTestingServlet(Reactor reactor) {
        return RoutingServlet.builder(reactor)
                .with(POST, BASE_PATH + "/scan", this::runSecurityScan)
                .with(POST, BASE_PATH + "/dependencies", this::scanDependencies)
                .with(POST, BASE_PATH + "/sast", this::runSAST)
                // Back-compat / v1 alias
                .with(POST, BASE_PATH_V1 + "/scan", this::runSecurityScan)
                .with(POST, BASE_PATH_V1 + "/dependencies", this::scanDependencies)
                .with(POST, BASE_PATH_V1 + "/sast", this::runSAST)
                .build();
    }
    
    /**
     * Run comprehensive security scan.
     * 
     * @param request Security scan request
     * @return Security scan results
     */
    private Promise<HttpResponse> runSecurityScan(HttpRequest httpRequest) {
        return JsonUtils.parseBody(httpRequest, SecurityScanRequest.class)
            .then(
                request -> {
                    log.info("Running security scan: path={}", request.projectPath());
                    return securityTestService.runSecurityScan(request)
                        .map(ApiResponse::ok);
                });
    }
    
    /**
     * Scan dependencies for known vulnerabilities.
     * 
     * @param request Dependency scan request
     * @return Vulnerability report
     */
    private Promise<HttpResponse> scanDependencies(HttpRequest httpRequest) {
        return JsonUtils.parseBody(httpRequest, DependencyScanRequest.class)
            .then(
                request -> {
                    log.info("Scanning dependencies: path={}", request.projectPath());
                    return securityTestService.scanDependencies(request)
                        .map(ApiResponse::ok);
                });
    }
    
    /**
     * Run Static Application Security Testing (SAST).
     * 
     * @param request SAST request
     * @return SAST findings
     */
    private Promise<HttpResponse> runSAST(HttpRequest httpRequest) {
        return JsonUtils.parseBody(httpRequest, SASTRequest.class)
            .then(
                request -> {
                    log.info(
                        "Running SAST: path={}, rules={}",
                        request.projectPath(),
                        request.ruleSet());
                    return securityTestService.runSAST(request)
                        .map(ApiResponse::ok);
                });
    }
}
