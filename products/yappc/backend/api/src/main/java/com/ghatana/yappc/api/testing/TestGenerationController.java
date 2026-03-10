/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.yappc.api.testing;

import static io.activej.http.HttpMethod.GET;
import static io.activej.http.HttpMethod.POST;

import com.ghatana.yappc.api.common.ApiResponse;
import com.ghatana.yappc.api.common.JsonUtils;
import com.ghatana.yappc.api.testing.dto.CoverageAnalysisRequest;
import com.ghatana.yappc.api.testing.dto.TestGenerationRequest;
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
 * Test Generation Controller - Generate unit/integration tests.
 * 
 * <p>Endpoints:
 * <ul>
 *   <li>POST /api/testing/generate - Generate tests from source code</li>
 *   <li>POST /api/testing/coverage - Analyze test coverage gaps</li>
 *   <li>GET /api/testing/templates - List test templates</li>
 * </ul>
 * 
 * @doc.type class
 * @doc.purpose HTTP API for test generation
 * @doc.layer product
 * @doc.pattern Controller
 */
public class TestGenerationController extends AbstractModule {

        private static final Logger log = LoggerFactory.getLogger(TestGenerationController.class);

        private static final String BASE_PATH = "/api/testing";
        private static final String BASE_PATH_V1 = "/api/v1/testing";
    
    private final TestGenerationService testGenerationService;
    
    @Inject
    public TestGenerationController(TestGenerationService testGenerationService) {
        this.testGenerationService = testGenerationService;
    }

        @Provides
        RoutingServlet testGenerationServlet(Reactor reactor) {
                return RoutingServlet.builder(reactor)
                                .with(POST, BASE_PATH + "/generate", this::generateTests)
                                .with(POST, BASE_PATH + "/coverage", this::analyzeCoverage)
                                .with(GET, BASE_PATH + "/templates", this::listTemplates)
                                // Back-compat / v1 alias
                                .with(POST, BASE_PATH_V1 + "/generate", this::generateTests)
                                .with(POST, BASE_PATH_V1 + "/coverage", this::analyzeCoverage)
                                .with(GET, BASE_PATH_V1 + "/templates", this::listTemplates)
                                .build();
        }
    
    /**
     * Generate tests from source code.
     * 
     * @param request Test generation request
     * @return Test generation result
     */
        public Promise<HttpResponse> generateTests(HttpRequest httpRequest) {
        return JsonUtils.parseBody(httpRequest, TestGenerationRequest.class)
                .then(
                        request -> {
                            log.info(
                                    "Generating tests: framework={}, type={}",
                                    request.framework(),
                                    request.testType());
                            return testGenerationService.generateTests(request)
                                    .map(ApiResponse::ok);
                        });
    }
    
    /**
     * Analyze test coverage gaps.
     * 
     * @param request Coverage analysis request
     * @return Coverage gaps report
     */
        public Promise<HttpResponse> analyzeCoverage(HttpRequest httpRequest) {
        return JsonUtils.parseBody(httpRequest, CoverageAnalysisRequest.class)
                .then(
                        request -> {
                            log.info("Analyzing coverage: path={}", request.projectPath());
                            return testGenerationService.analyzeCoverage(request)
                                    .map(ApiResponse::ok);
                        });
    }
    
    /**
     * List available test templates.
     * 
     * @param framework Optional framework filter (junit, jest, pytest)
     * @return List of test templates
     */
        public Promise<HttpResponse> listTemplates(HttpRequest httpRequest) {
                String framework = httpRequest.getQueryParameter("framework");
                log.info("Listing test templates: framework={}", framework);

                return testGenerationService.listTemplates(framework)
                                .map(ApiResponse::ok);
    }
}
