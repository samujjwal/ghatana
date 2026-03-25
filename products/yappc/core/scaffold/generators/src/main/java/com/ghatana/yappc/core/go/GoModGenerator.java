/*
 * Copyright (c) 2025 Ghatana Platform Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.yappc.core.go;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import io.activej.promise.Promise;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of GoBuildGenerator. Generates go.mod files and
 * Go project scaffolding based on specifications.
 *
 * @doc.type class
 * @doc.purpose Default implementation of Go module build generation
 * @doc.layer platform
 * @doc.pattern Generator
 */
public class GoModGenerator implements GoBuildGenerator {

    private static final Executor BLOCKING_EXECUTOR = Executors.newCachedThreadPool();


    private static final Logger log = LoggerFactory.getLogger(GoModGenerator.class);

    @Override
    public Promise<GeneratedGoProject> generateGoMod(GoBuildSpec spec) {
        return Promise.ofBlocking(BLOCKING_EXECUTOR, () -> {
            log.info("Generating go.mod for module: {}", spec.modulePath());

            String goModContent = generateGoModContent(spec);
            String makefileContent = generateMakefileContent(spec);
            String dockerfileContent = generateDockerfileContent(spec);
            String gitignoreContent = generateGitignoreContent();

            Map<String, String> sourceFiles = generateSourceFiles(spec);
            Map<String, String> testFiles = generateTestFiles(spec);
            Map<String, String> configFiles = generateConfigFiles(spec);

            GeneratedGoProject.GoProjectMetadata metadata = new GeneratedGoProject.GoProjectMetadata(
                    spec.modulePath(),
                    spec.goVersion(),
                    spec.projectType().name(),
                    spec.dependencies() != null ? spec.dependencies().size() : 0,
                    Instant.now().toString());

            return new GeneratedGoProject(
                    goModContent,
                    makefileContent,
                    dockerfileContent,
                    gitignoreContent,
                    sourceFiles,
                    testFiles,
                    configFiles,
                    metadata);
        });
    }

    @Override
    public Promise<GoValidationResult> validateSpec(GoBuildSpec spec) {
        return Promise.ofBlocking(BLOCKING_EXECUTOR, () -> {
            List<String> errors = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            List<String> suggestions = new ArrayList<>();

            // Validate module path
            if (spec.modulePath() == null || spec.modulePath().isBlank()) {
                errors.add("Module path is required");
            } else if (!spec.modulePath().contains("/")) {
                warnings.add("Module path should typically include a domain (e.g., github.com/org/project)");
            }

            // Validate Go version
            if (spec.goVersion() == null || spec.goVersion().isBlank()) {
                errors.add("Go version is required");
            } else {
                String version = spec.goVersion();
                if (!version.matches("1\\.(\\d+)(\\.\\d+)?")) {
                    warnings.add("Go version format should be like '1.21' or '1.21.0'");
                }
            }

            // Check for common dependencies
            if (spec.dependencies() != null && !spec.dependencies().isEmpty()) {
                for (GoBuildSpec.GoDependency dep : spec.dependencies()) {
                    if (dep.version() == null || dep.version().isBlank()) {
                        warnings.add("Dependency '" + dep.path() + "' has no version specified");
                    }
                }
            }

            // Suggest common patterns
            if (spec.projectType() == GoBuildSpec.GoProjectType.SERVICE) {
                suggestions.add("Consider adding structured logging (e.g., go.uber.org/zap)");
                suggestions.add("Consider adding a router (e.g., github.com/go-chi/chi/v5)");
            }

            boolean valid = errors.isEmpty();
            String summary = valid 
                    ? "Validation passed" + (warnings.isEmpty() ? "" : " with " + warnings.size() + " warning(s)")
                    : "Validation failed with " + errors.size() + " error(s)";

            return new GoValidationResult(valid, errors, warnings, suggestions, summary);
        });
    }

    @Override
    public Promise<GoImprovementSuggestions> suggestImprovements(GoBuildSpec spec) {
        return Promise.ofBlocking(BLOCKING_EXECUTOR, () -> {
            List<GoImprovementSuggestions.DependencyUpdate> updates = new ArrayList<>();
            List<GoImprovementSuggestions.SecurityIssue> securityIssues = List.of();
            List<String> performanceHints = new ArrayList<>();
            List<String> bestPractices = new ArrayList<>();
            List<String> deprecations = new ArrayList<>();

            // Add best practices based on project type
            switch (spec.projectType()) {
                case SERVICE -> {
                    bestPractices.add("Use context.Context for request cancellation and timeouts");
                    bestPractices.add("Implement graceful shutdown with signal handling");
                    bestPractices.add("Add health check endpoints for container orchestration");
                    performanceHints.add("Consider using connection pooling for database connections");
                }
                case CLI -> {
                    bestPractices.add("Use cobra or urfave/cli for command-line parsing");
                    bestPractices.add("Support configuration via environment variables and flags");
                }
                case LIBRARY -> {
                    bestPractices.add("Ensure all public APIs are well-documented");
                    bestPractices.add("Avoid global state and package-level variables");
                }
                default -> {}
            }

            // General best practices
            bestPractices.add("Run 'go mod tidy' to clean up unused dependencies");
            bestPractices.add("Use 'go vet' and 'staticcheck' for code analysis");

            return new GoImprovementSuggestions(
                    updates,
                    securityIssues,
                    performanceHints,
                    bestPractices,
                    deprecations);
        });
    }

    @Override
    public Promise<GoProjectScaffold> generateProjectScaffold(GoBuildSpec spec) {
        return Promise.ofBlocking(BLOCKING_EXECUTOR, () -> {
            Map<String, String> sourceFiles = new HashMap<>();
            Map<String, String> testFiles = new HashMap<>();
            Map<String, String> internalFiles = new HashMap<>();
            Map<String, String> pkgFiles = new HashMap<>();
            Map<String, String> cmdFiles = new HashMap<>();
            List<String> directories = new ArrayList<>();

            // Standard Go project layout
            directories.add("cmd/" + spec.projectName());
            directories.add("internal");
            directories.add("pkg");

            switch (spec.projectType()) {
                case SERVICE -> {
                    directories.add("internal/config");
                    directories.add("internal/handler");
                    directories.add("internal/middleware");
                    directories.add("internal/router");
                    
                    cmdFiles.put("cmd/" + spec.projectName() + "/main.go", generateServiceMainGo(spec));
                    internalFiles.put("internal/config/config.go", generateConfigGo(spec));
                    internalFiles.put("internal/router/router.go", generateRouterGo(spec));
                    internalFiles.put("internal/handler/health.go", generateHealthHandlerGo(spec));
                }
                case CLI -> {
                    directories.add("internal/cmd");
                    
                    cmdFiles.put("cmd/" + spec.projectName() + "/main.go", generateCliMainGo(spec));
                }
                case LIBRARY -> {
                    sourceFiles.put(spec.projectName() + ".go", generateLibraryGo(spec));
                    testFiles.put(spec.projectName() + "_test.go", generateLibraryTestGo(spec));
                }
                default -> {
                    cmdFiles.put("cmd/" + spec.projectName() + "/main.go", generateBasicMainGo(spec));
                }
            }

            return new GoProjectScaffold(
                    sourceFiles,
                    testFiles,
                    internalFiles,
                    pkgFiles,
                    cmdFiles,
                    directories);
        });
    }

    @Override
    public Promise<GoAnalysisResult> analyzeProject(String projectPath) {
        return Promise.ofBlocking(BLOCKING_EXECUTOR, () -> {
            // Placeholder for actual project analysis
            return new GoAnalysisResult(
                    "unknown",
                    "1.21",
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    0,
                    0.0,
                    List.of(),
                    Map.of());
        });
    }

    // Private helper methods for content generation

    private String generateGoModContent(GoBuildSpec spec) {
        StringBuilder sb = new StringBuilder();
        sb.append("module ").append(spec.modulePath()).append("\n\n");
        sb.append("go ").append(spec.goVersion()).append("\n");

        if (spec.dependencies() != null && !spec.dependencies().isEmpty()) {
            sb.append("\nrequire (\n");
            for (GoBuildSpec.GoDependency dep : spec.dependencies()) {
                sb.append("\t").append(dep.path());
                if (dep.version() != null && !dep.version().isBlank()) {
                    sb.append(" ").append(dep.version());
                }
                if (dep.indirect()) {
                    sb.append(" // indirect");
                }
                sb.append("\n");
            }
            sb.append(")\n");
        }

        return sb.toString();
    }

    private String generateMakefileContent(GoBuildSpec spec) {
        String projectName = spec.projectName() != null ? spec.projectName() : "app";
        String modulePath = spec.modulePath();

        return """
                # Generated by YAPPC Go Module Generator
                
                BINARY_NAME=%s
                MODULE=%s
                GO_VERSION=%s
                
                .PHONY: all build clean test lint run dev help
                
                all: build
                
                ## build: Build the binary
                build:
                	@echo "Building..."
                	go build -o bin/$(BINARY_NAME) ./cmd/$(BINARY_NAME)
                
                ## clean: Clean build artifacts
                clean:
                	@echo "Cleaning..."
                	rm -rf bin/
                	go clean
                
                ## test: Run tests
                test:
                	@echo "Testing..."
                	go test -v -race -coverprofile=coverage.out ./...
                
                ## lint: Run linters
                lint:
                	@echo "Linting..."
                	go vet ./...
                	staticcheck ./...
                
                ## run: Run the application
                run: build
                	@echo "Running..."
                	./bin/$(BINARY_NAME)
                
                ## dev: Run with hot reload (requires air)
                dev:
                	@echo "Starting development server..."
                	air
                
                ## tidy: Tidy dependencies
                tidy:
                	go mod tidy
                
                ## deps: Download dependencies
                deps:
                	go mod download
                
                ## help: Show this help
                help:
                	@echo "Available targets:"
                	@sed -n 's/^## //p' $(MAKEFILE_LIST) | column -t -s ':'
                """.formatted(projectName, modulePath, spec.goVersion());
    }

    private String generateDockerfileContent(GoBuildSpec spec) {
        String projectName = spec.projectName() != null ? spec.projectName() : "app";
        
        return """
                # Generated by YAPPC Go Module Generator
                # Build stage
                FROM golang:%s-alpine AS builder
                
                WORKDIR /app
                
                # Copy go mod files
                COPY go.mod go.sum ./
                RUN go mod download
                
                # Copy source code
                COPY . .
                
                # Build the binary
                RUN CGO_ENABLED=0 GOOS=linux go build -a -installsuffix cgo -o /app/bin/%s ./cmd/%s
                
                # Final stage
                FROM alpine:latest
                
                RUN apk --no-cache add ca-certificates tzdata
                
                WORKDIR /app
                
                COPY --from=builder /app/bin/%s .
                
                EXPOSE 8080
                
                ENTRYPOINT ["./%s"]
                """.formatted(spec.goVersion(), projectName, projectName, projectName, projectName);
    }

    private String generateGitignoreContent() {
        return """
                # Generated by YAPPC Go Module Generator
                
                # Binaries
                bin/
                *.exe
                *.exe~
                *.dll
                *.so
                *.dylib
                
                # Test binary
                *.test
                
                # Coverage
                coverage.out
                coverage.html
                
                # Go workspace
                go.work
                
                # IDE
                .idea/
                .vscode/
                *.swp
                *.swo
                
                # OS
                .DS_Store
                Thumbs.db
                
                # Environment
                .env
                .env.local
                
                # Vendor (if not using vendoring)
                # vendor/
                """;
    }

    private Map<String, String> generateSourceFiles(GoBuildSpec spec) {
        Map<String, String> files = new HashMap<>();
        
        switch (spec.projectType()) {
            case SERVICE -> files.put("cmd/" + spec.projectName() + "/main.go", generateServiceMainGo(spec));
            case CLI -> files.put("cmd/" + spec.projectName() + "/main.go", generateCliMainGo(spec));
            case LIBRARY -> files.put(spec.projectName() + ".go", generateLibraryGo(spec));
            default -> files.put("main.go", generateBasicMainGo(spec));
        }
        
        return files;
    }

    private Map<String, String> generateTestFiles(GoBuildSpec spec) {
        Map<String, String> files = new HashMap<>();
        
        if (spec.projectType() == GoBuildSpec.GoProjectType.LIBRARY) {
            files.put(spec.projectName() + "_test.go", generateLibraryTestGo(spec));
        } else {
            files.put("main_test.go", generateBasicTestGo(spec));
        }
        
        return files;
    }

    private Map<String, String> generateConfigFiles(GoBuildSpec spec) {
        Map<String, String> files = new HashMap<>();
        files.put(".air.toml", generateAirConfig(spec));
        return files;
    }

    private String generateServiceMainGo(GoBuildSpec spec) {
        return """
                package main
                
                import (
                	"context"
                	"log"
                	"net/http"
                	"os"
                	"os/signal"
                	"syscall"
                	"time"
                )
                
                func main() {
                	// Create server
                	mux := http.NewServeMux()
                	
                	// Health check endpoint
                	mux.HandleFunc("/health", func(w http.ResponseWriter, r *http.Request) {
                		w.WriteHeader(http.StatusOK)
                		w.Write([]byte(`{"status":"ok"}`))
                	})
                	
                	// Hello endpoint
                	mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
                		w.Write([]byte("Hello from %s!"))
                	})
                	
                	server := &http.Server{
                		Addr:         ":8080",
                		Handler:      mux,
                		ReadTimeout:  10 * time.Second,
                		WriteTimeout: 10 * time.Second,
                	}
                	
                	// Start server in goroutine
                	go func() {
                		log.Printf("Starting server on %%s", server.Addr)
                		if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
                			log.Fatalf("Server error: %%v", err)
                		}
                	}()
                	
                	// Wait for interrupt signal
                	quit := make(chan os.Signal, 1)
                	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
                	<-quit
                	
                	log.Println("Shutting down server...")
                	
                	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
                	defer cancel()
                	
                	if err := server.Shutdown(ctx); err != nil {
                		log.Fatalf("Server shutdown error: %%v", err)
                	}
                	
                	log.Println("Server stopped")
                }
                """.formatted(spec.projectName());
    }

    private String generateCliMainGo(GoBuildSpec spec) {
        return """
                package main
                
                import (
                	"fmt"
                	"os"
                )
                
                var version = "dev"
                
                func main() {
                	if len(os.Args) > 1 && os.Args[1] == "version" {
                		fmt.Printf("%s version %%s\\n", version)
                		return
                	}
                	
                	fmt.Println("Hello from %s!")
                	fmt.Println("Use '%s version' to see version info")
                }
                """.formatted(spec.projectName(), spec.projectName(), spec.projectName());
    }

    private String generateLibraryGo(GoBuildSpec spec) {
        String packageName = spec.projectName().replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        return """
                // Package %s provides ...
                package %s
                
                // Version is the library version
                const Version = "0.1.0"
                
                // Example is an example function
                func Example() string {
                	return "Hello from %s!"
                }
                """.formatted(packageName, packageName, spec.projectName());
    }

    private String generateBasicMainGo(GoBuildSpec spec) {
        return """
                package main
                
                import "fmt"
                
                func main() {
                	fmt.Println("Hello from %s!")
                }
                """.formatted(spec.projectName());
    }

    private String generateConfigGo(GoBuildSpec spec) {
        return """
                package config
                
                import (
                	"os"
                )
                
                // Config holds application configuration
                type Config struct {
                	Port     string
                	LogLevel string
                }
                
                // Load returns configuration from environment
                func Load() *Config {
                	return &Config{
                		Port:     getEnv("PORT", "8080"),
                		LogLevel: getEnv("LOG_LEVEL", "info"),
                	}
                }
                
                func getEnv(key, defaultValue string) string {
                	if value := os.Getenv(key); value != "" {
                		return value
                	}
                	return defaultValue
                }
                """;
    }

    private String generateRouterGo(GoBuildSpec spec) {
        return """
                package router
                
                import (
                	"net/http"
                )
                
                // New creates a new router with all routes configured
                func New() http.Handler {
                	mux := http.NewServeMux()
                	
                	// Add routes here
                	mux.HandleFunc("/health", healthHandler)
                	mux.HandleFunc("/", homeHandler)
                	
                	return mux
                }
                
                func healthHandler(w http.ResponseWriter, r *http.Request) {
                	w.Header().Set("Content-Type", "application/json")
                	w.WriteHeader(http.StatusOK)
                	w.Write([]byte(`{"status":"ok"}`))
                }
                
                func homeHandler(w http.ResponseWriter, r *http.Request) {
                	w.Write([]byte("Welcome to %s"))
                }
                """.formatted(spec.projectName());
    }

    private String generateHealthHandlerGo(GoBuildSpec spec) {
        return """
                package handler
                
                import (
                	"encoding/json"
                	"net/http"
                )
                
                // HealthResponse represents the health check response
                type HealthResponse struct {
                	Status  string `json:"status"`
                	Version string `json:"version,omitempty"`
                }
                
                // HealthHandler handles health check requests
                func HealthHandler(version string) http.HandlerFunc {
                	return func(w http.ResponseWriter, r *http.Request) {
                		resp := HealthResponse{
                			Status:  "ok",
                			Version: version,
                		}
                		
                		w.Header().Set("Content-Type", "application/json")
                		w.WriteHeader(http.StatusOK)
                		json.NewEncoder(w).Encode(resp)
                	}
                }
                """;
    }

    private String generateLibraryTestGo(GoBuildSpec spec) {
        String packageName = spec.projectName().replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        return """
                package %s
                
                import "testing"
                
                func TestExample(t *testing.T) {
                	result := Example()
                	expected := "Hello from %s!"
                	
                	if result != expected {
                		t.Errorf("Expected %%q, got %%q", expected, result)
                	}
                }
                """.formatted(packageName, spec.projectName());
    }

    private String generateBasicTestGo(GoBuildSpec spec) {
        return """
                package main
                
                import "testing"
                
                func TestMain(t *testing.T) {
                	// Add tests here
                	t.Log("Tests passing")
                }
                """;
    }

    private String generateAirConfig(GoBuildSpec spec) {
        String projectName = spec.projectName() != null ? spec.projectName() : "app";
        return """
                # Generated by YAPPC Go Module Generator
                # Config for Air hot reload - https://github.com/cosmtrek/air
                
                root = "."
                tmp_dir = "tmp"
                
                [build]
                  bin = "./tmp/%s"
                  cmd = "go build -o ./tmp/%s ./cmd/%s"
                  delay = 1000
                  exclude_dir = ["assets", "tmp", "vendor", "node_modules"]
                  exclude_file = []
                  exclude_regex = ["_test.go"]
                  exclude_unchanged = false
                  follow_symlink = false
                  full_bin = ""
                  include_dir = []
                  include_ext = ["go", "tpl", "tmpl", "html"]
                  kill_delay = "0s"
                  log = "build-errors.log"
                  send_interrupt = false
                  stop_on_error = true
                
                [color]
                  build = "yellow"
                  main = "magenta"
                  runner = "green"
                  watcher = "cyan"
                
                [log]
                  time = false
                
                [misc]
                  clean_on_exit = false
                """.formatted(projectName, projectName, projectName);
    }
}
