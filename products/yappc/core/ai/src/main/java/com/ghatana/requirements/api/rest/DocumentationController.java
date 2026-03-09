package com.ghatana.requirements.api.rest;

import com.ghatana.platform.http.server.response.ResponseBuilder;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Controller for API documentation endpoints.
 *
 * <p><b>Purpose</b><br>
 * Serves OpenAPI specification and API documentation for developers.
 * Provides interactive API documentation and specification downloads.
 *
 * <p><b>Endpoints</b><br>
 * - GET /api/docs - OpenAPI specification (YAML)
 * - GET /api/docs/swagger - Swagger UI redirect
 *
 * @doc.type class
 * @doc.purpose API documentation controller
 * @doc.layer product
 * @doc.pattern Controller
 * @since 1.0.0
 */
public final class DocumentationController {
    private static final Logger logger = LoggerFactory.getLogger(DocumentationController.class);
    
    /**
     * Serve OpenAPI specification.
     *
     * @param request HTTP request
     * @return OpenAPI YAML specification
     */
    public Promise<HttpResponse> getOpenApiSpec(HttpRequest request) {
        logger.debug("Serving OpenAPI specification");
        
        try {
            InputStream inputStream = getClass().getResourceAsStream("/openapi.yaml");
            if (inputStream == null) {
                logger.error("OpenAPI specification file not found");
                return Promise.of(
                    ResponseBuilder.notFound()
                        .text("API documentation not found")
                        .build()
                );
            }
            
            String spec = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            
            return Promise.of(
                ResponseBuilder.ok()
                    .header("Content-Type", "application/vnd.oai.openapi;version=3.0.3")
                    .header("Cache-Control", "public, max-age=3600") // Cache for 1 hour
                    .text(spec)
                    .build()
            );
            
        } catch (Exception e) {
            logger.error("Failed to serve OpenAPI specification", e);
            return Promise.of(
                ResponseBuilder.internalServerError()
                    .text("Failed to load API documentation")
                    .build()
            );
        }
    }
    
    /**
     * Redirect to Swagger UI.
     *
     * @param request HTTP request
     * @return Redirect to Swagger UI
     */
    public Promise<HttpResponse> getSwaggerUi(HttpRequest request) {
        logger.debug("Redirecting to Swagger UI");
        
        // In production, this would redirect to a hosted Swagger UI
        // For now, return a simple HTML page with the spec URL
        String html = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>AI Requirements Tool API Documentation</title>
                <style>
                    body { font-family: Arial, sans-serif; margin: 40px; }
                    .container { max-width: 800px; margin: 0 auto; }
                    .spec-link { 
                        background: #007bff; 
                        color: white; 
                        padding: 12px 24px; 
                        text-decoration: none; 
                        border-radius: 4px; 
                        display: inline-block;
                        margin: 10px 0;
                    }
                    .spec-link:hover { background: #0056b3; }
                    .info { 
                        background: #f8f9fa; 
                        padding: 20px; 
                        border-radius: 4px; 
                        margin: 20px 0;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>AI Requirements Tool API Documentation</h1>
                    
                    <div class="info">
                        <h2>Available Documentation</h2>
                        <p>This API provides comprehensive project and requirements management capabilities with AI-powered features.</p>
                        
                        <h3>Authentication</h3>
                        <p>All endpoints (except health checks) require JWT authentication:</p>
                        <code>Authorization: Bearer {jwt-token}</code>
                        
                        <h3>Base URL</h3>
                        <p><code>https://api.ghatana.com/api/v1</code></p>
                    </div>
                    
                    <h2>API Specification</h2>
                    <a href="/api/docs" class="spec-link">Download OpenAPI Specification (YAML)</a>
                    <br>
                    <a href="/api/docs" class="spec-link" download="openapi.yaml">Download OpenAPI Specification (YAML)</a>
                    
                    <div class="info">
                        <h2>Using the Specification</h2>
                        <p>You can use the OpenAPI specification with:</p>
                        <ul>
                            <li><a href="https://swagger.io/tools/swagger-ui/" target="_blank">Swagger UI</a></li>
                            <li><a href="https://insomnia.rest/" target="_blank">Insomnia REST Client</a></li>
                            <li><a href="https://www.postman.com/" target="_blank">Postman</a></li>
                            <li><a href="https://github.com/OpenAPITools/openapi-generator" target="_blank">OpenAPI Generator</a></li>
                        </ul>
                        
                        <h3>Quick Start</h3>
                        <ol>
                            <li>Download the OpenAPI specification using the link above</li>
                            <li>Import it into your favorite API client (Swagger UI, Postman, etc.)</li>
                            <li>Configure authentication with your JWT token</li>
                            <li>Start exploring the API!</li>
                        </ol>
                    </div>
                    
                    <div class="info">
                        <h2>Key Features</h2>
                        <ul>
                            <li><strong>Workspace Management:</strong> Create and manage collaborative workspaces</li>
                            <li><strong>Project Management:</strong> Full project lifecycle with status tracking</li>
                            <li><strong>Requirements Management:</strong> Create, update, and organize requirements</li>
                            <li><strong>AI-Powered Suggestions:</strong> Get intelligent requirement improvements</li>
                            <li><strong>Similarity Analysis:</strong> Find similar requirements using AI</li>
                            <li><strong>Data Export:</strong> Export requirements in multiple formats</li>
                            <li><strong>GraphQL API:</strong> Flexible querying with GraphQL</li>
                        </ul>
                    </div>
                </div>
            </body>
            </html>
            """;
        
        return Promise.of(
            ResponseBuilder.ok()
                .header("Content-Type", "text/html; charset=UTF-8")
                .text(html)
                .build()
        );
    }
}
