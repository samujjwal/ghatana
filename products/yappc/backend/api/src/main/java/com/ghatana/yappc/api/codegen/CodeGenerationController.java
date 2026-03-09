package com.ghatana.yappc.api.codegen;

import com.ghatana.yappc.api.codegen.dto.CodeGenerationRequest;
import com.ghatana.yappc.api.common.ApiResponse;
import com.ghatana.yappc.api.common.JsonUtils;
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

import static io.activej.http.HttpMethod.POST;

/**
 * Code Generation Controller - AI-powered code generation from specifications.
 *
 * <p>Provides endpoints for:
 *
 * <ul>
 *   <li>Generating REST controllers from OpenAPI specs</li>
 *   <li>Generating GraphQL resolvers from schemas</li>
 *   <li>Generating JPA entities from database schemas</li>
 *   <li>Previewing generated code before committing</li>
 * </ul>
 *
 * <p>Features:
 *
 * <ul>
 *   <li>Multi-format input (OpenAPI, GraphQL, SQL, JSON Schema)</li>
 *   <li>Template-based generation with customization</li>
 *   <li>Code preview with syntax highlighting</li>
 *   <li>Validation before generation</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose REST API for AI-powered code generation
 * @doc.layer product
 * @doc.pattern Controller
 */
public class CodeGenerationController extends AbstractModule {

  private static final Logger log = LoggerFactory.getLogger(CodeGenerationController.class);
  private static final String BASE_PATH = "/api/v1/codegen";

  private final CodeGenerationService codeGenService;

  @Inject
  public CodeGenerationController(CodeGenerationService codeGenService) {
    this.codeGenService = codeGenService;
  }

  @Provides
  RoutingServlet codeGenServlet(Reactor reactor) {
    return RoutingServlet.builder(reactor)
        .with(POST, BASE_PATH + "/from-openapi", this::generateFromOpenAPI)
        .with(POST, BASE_PATH + "/from-graphql", this::generateFromGraphQL)
        .with(POST, BASE_PATH + "/from-schema", this::generateFromSchema)
        .with(POST, BASE_PATH + "/preview", this::previewCode)
        .build();
  }

  /**
   * POST /api/v1/codegen/from-openapi
   *
   * <p>Generate REST controllers and DTOs from OpenAPI specification.
   */
  private Promise<HttpResponse> generateFromOpenAPI(HttpRequest request) {
    log.info("Generating code from OpenAPI specification");

    Promise<HttpResponse> promise =
        JsonUtils.parseBody(request, CodeGenerationRequest.class)
            .then(
                genRequest -> {
                  if (!genRequest.isValid()) {
                    return Promise.of(ApiResponse.badRequest("Invalid code generation request"));
                  }

                  return codeGenService
                      .generateFromOpenAPI(genRequest)
                      .map(
                          result -> {
                            log.info(
                                "OpenAPI code generation completed: {} files generated",
                                result.generatedFiles().size());
                            return ApiResponse.ok(result);
                          });
                });

    return ApiResponse.wrap(promise);
  }

  /**
   * POST /api/v1/codegen/from-graphql
   *
   * <p>Generate GraphQL resolvers and types from GraphQL schema.
   */
  private Promise<HttpResponse> generateFromGraphQL(HttpRequest request) {
    log.info("Generating code from GraphQL schema");

    Promise<HttpResponse> promise =
        JsonUtils.parseBody(request, CodeGenerationRequest.class)
            .then(
                genRequest -> {
                  if (!genRequest.isValid()) {
                    return Promise.of(ApiResponse.badRequest("Invalid code generation request"));
                  }

                  return codeGenService
                      .generateFromGraphQL(genRequest)
                      .map(
                          result -> {
                            log.info(
                                "GraphQL code generation completed: {} files generated",
                                result.generatedFiles().size());
                            return ApiResponse.ok(result);
                          });
                });

    return ApiResponse.wrap(promise);
  }

  /**
   * POST /api/v1/codegen/from-schema
   *
   * <p>Generate JPA entities from database schema or JSON Schema.
   */
  private Promise<HttpResponse> generateFromSchema(HttpRequest request) {
    log.info("Generating code from database/JSON schema");

    Promise<HttpResponse> promise =
        JsonUtils.parseBody(request, CodeGenerationRequest.class)
            .then(
                genRequest -> {
                  if (!genRequest.isValid()) {
                    return Promise.of(ApiResponse.badRequest("Invalid code generation request"));
                  }

                  return codeGenService
                      .generateFromSchema(genRequest)
                      .map(
                          result -> {
                            log.info(
                                "Schema code generation completed: {} files generated",
                                result.generatedFiles().size());
                            return ApiResponse.ok(result);
                          });
                });

    return ApiResponse.wrap(promise);
  }

  /**
   * POST /api/v1/codegen/preview
   *
   * <p>Preview generated code without writing to disk.
   */
  private Promise<HttpResponse> previewCode(HttpRequest request) {
    log.info("Previewing code generation");

    Promise<HttpResponse> promise =
        JsonUtils.parseBody(request, CodeGenerationRequest.class)
            .then(
                genRequest -> {
                  if (!genRequest.isValid()) {
                    return Promise.of(ApiResponse.badRequest("Invalid code generation request"));
                  }

                  return codeGenService
                      .previewGeneration(genRequest)
                      .map(
                          preview -> {
                            log.info("Code preview generated: {} files", preview.files().size());
                            return ApiResponse.ok(preview);
                          });
                });

    return ApiResponse.wrap(promise);
  }
}
