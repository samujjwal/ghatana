# YAPPC - Development & Build Automation
# Usage: make <target>
# Examples:
#   make setup          # First-time setup
#   make dev            # Start full development environment
#   make build          # Build all services
#   make test           # Run all tests
#   make clean          # Clean build artifacts

.PHONY: help setup verify build build-standalone build-fast clean test dev start-infra start-backend start-frontend stop lint format docker-up docker-down

# Colors for output
BLUE := \033[0;34m
GREEN := \033[0;32m
YELLOW := \033[1;33m
RED := \033[0;31m
NC := \033[0m

# Default target
.DEFAULT_GOAL := help

## help: Display this help message
help:
	@echo "$(BLUE)╔════════════════════════════════════════════════════════════╗$(NC)"
	@echo "$(BLUE)║             YAPPC Development Commands                     ║$(NC)"
	@echo "$(BLUE)╚════════════════════════════════════════════════════════════╝$(NC)"
	@echo ""
	@grep -E '^## ' $(MAKEFILE_LIST) | sed 's/## //' | awk 'BEGIN {FS = ": "}; {printf "  $(GREEN)%-20s$(NC) %s\n", $$1, $$2}'
	@echo ""

## setup: Initial project setup (run once)
setup:
	@echo "$(BLUE)═══ Setting up YAPPC development environment ═══$(NC)"
	@./tools/scripts/verify-dev-environment.sh
	@echo "$(BLUE)═══ Installing dependencies ═══$(NC)"
	@cd frontend && pnpm install
	@echo "$(GREEN)✓ Setup complete!$(NC)"

## verify: Verify development environment
verify:
	@./tools/scripts/verify-dev-environment.sh

## build: Build all services (Java + Frontend) via monorepo Gradle
build:
	@echo "$(BLUE)═══ Building YAPPC with standardized structure ═══$(NC)"
	@../../gradlew :products:yappc:build -x test --parallel
	@echo "$(BLUE)═══ Building frontend ═══$(NC)"
	@cd frontend && pnpm build:web
	@echo "$(GREEN)✓ Build complete!$(NC)"

## build-standalone: Build in standalone mode (from products/yappc/)
build-standalone:
	@echo "$(BLUE)═══ Building YAPPC in standalone mode ═══$(NC)"
	@./gradlew clean build -x test --parallel
	@cd frontend && pnpm build:web
	@echo "$(GREEN)✓ Standalone build complete!$(NC)"

## build-fast: Fast build (skip tests and checks)
build-fast:
	@echo "$(BLUE)═══ Fast building all services ═══$(NC)"
	@./gradlew build -x test -x check --parallel
	@cd frontend && pnpm build:web
	@echo "$(GREEN)✓ Fast build complete!$(NC)"

## clean: Clean all build artifacts
clean:
	@echo "$(BLUE)═══ Cleaning build artifacts ═══$(NC)"
	@./gradlew clean
	@cd frontend && pnpm run clean:generated || true
	@rm -rf frontend/node_modules/.cache
	@echo "$(GREEN)✓ Clean complete!$(NC)"

## test: Run all tests (Java + Frontend)
test:
	@echo "$(BLUE)═══ Running Java tests ═══$(NC)"
	@./gradlew test
	@echo "$(BLUE)═══ Running frontend tests ═══$(NC)"
	@cd frontend && pnpm test
	@echo "$(GREEN)✓ All tests passed!$(NC)"

## test-java: Run Java tests only
test-java:
	@./gradlew test

## test-frontend: Run frontend tests only
test-frontend:
	@cd frontend && pnpm test

## test-e2e: Run end-to-end tests
test-e2e:
	@cd frontend && pnpm test:e2e

## lint: Lint all code
lint:
	@echo "$(BLUE)═══ Linting Java code ═══$(NC)"
	@./gradlew checkstyleMain pmdMain || true
	@echo "$(BLUE)═══ Linting frontend code ═══$(NC)"
	@cd frontend && pnpm lint
	@echo "$(GREEN)✓ Lint complete!$(NC)"

## format: Format all code
format:
	@echo "$(BLUE)═══ Formatting Java code ═══$(NC)"
	@./gradlew spotlessApply
	@echo "$(BLUE)═══ Formatting frontend code ═══$(NC)"
	@cd frontend && pnpm format
	@echo "$(GREEN)✓ Format complete!$(NC)"

## start-infra: Start infrastructure services (Postgres, Redis, etc.)
start-infra:
	@echo "$(BLUE)═══ Starting infrastructure services ═══$(NC)"
	@./start-infra.sh || docker compose up -d postgres redis
	@echo "$(GREEN)✓ Infrastructure started!$(NC)"

## start-backend: Start all backend services
start-backend:
	@echo "$(BLUE)═══ Starting backend services ═══$(NC)"
	@docker compose --profile backend up -d
	@echo "$(GREEN)✓ Backend services started!$(NC)"
	@echo "$(YELLOW)Services available at:$(NC)"
	@echo "  • Domain API:         http://localhost:8080"
	@echo "  • AI Requirements:    http://localhost:8081"
	@echo "  • Lifecycle API:      http://localhost:8082"
	@echo "  • Backend API:        http://localhost:8000"

## start-frontend: Start frontend development server
start-frontend:
	@echo "$(BLUE)═══ Starting frontend ═══$(NC)"
	@cd frontend && pnpm dev:web

## dev: Start full development environment (infra + backend + frontend in parallel)
dev: start-infra start-backend
	@echo "$(GREEN)✓ Backend services started$(NC)"
	@echo "$(YELLOW)Starting frontend... (press Ctrl+C to stop all)$(NC)"
	@cd frontend && pnpm dev:web

## stop: Stop all services
stop:
	@echo "$(BLUE)═══ Stopping all services ═══$(NC)"
	@docker compose down
	@echo "$(GREEN)✓ All services stopped!$(NC)"

## docker-up: Start all services with Docker Compose
docker-up:
	@echo "$(BLUE)═══ Starting all services with Docker Compose ═══$(NC)"
	@docker compose --profile full up -d
	@echo "$(GREEN)✓ All services started!$(NC)"

## docker-down: Stop and remove all Docker containers
docker-down:
	@echo "$(BLUE)═══ Stopping Docker Compose services ═══$(NC)"
	@docker compose down -v
	@echo "$(GREEN)✓ Docker services stopped!$(NC)"

## logs: Show logs from all services
logs:
	@docker compose logs -f

## logs-java: Show logs from Java services
logs-java:
	@docker compose logs -f domain ai-requirements-api lifecycle-api

## logs-node: Show logs from Node.js services
logs-node:
	@docker compose logs -f backend-api

## ps: Show running services
ps:
	@docker compose ps

## health: Check health of all services
health:
	@echo "$(BLUE)═══ Checking service health ═══$(NC)"
	@echo "Domain API (8080):"
	@curl -s http://localhost:8080/health || echo "$(RED)✗ Not responding$(NC)"
	@echo ""
	@echo "AI Requirements API (8081):"
	@curl -s http://localhost:8081/health || echo "$(RED)✗ Not responding$(NC)"
	@echo ""
	@echo "Lifecycle API (8082):"
	@curl -s http://localhost:8082/health || echo "$(RED)✗ Not responding$(NC)"
	@echo ""
	@echo "Backend API (8000):"
	@curl -s http://localhost:8000/health || echo "$(RED)✗ Not responding$(NC)"

## typecheck: Run TypeScript type checking
typecheck:
	@cd frontend && pnpm typecheck

## coverage: Run tests with coverage
coverage:
	@echo "$(BLUE)═══ Running Java tests with coverage ═══$(NC)"
	@./gradlew test jacocoTestReport
	@echo "$(BLUE)═══ Running frontend tests with coverage ═══$(NC)"
	@cd frontend && pnpm test:coverage
	@echo "$(GREEN)✓ Coverage reports generated!$(NC)"
	@echo "Java coverage: build/reports/jacoco/test/html/index.html"
	@echo "Frontend coverage: frontend/coverage/index.html"

## install: Install all dependencies
install:
	@echo "$(BLUE)═══ Installing dependencies ═══$(NC)"
	@cd frontend && pnpm install
	@echo "$(GREEN)✓ Dependencies installed!$(NC)"

## update: Update all dependencies
update:
	@echo "$(BLUE)═══ Updating Gradle dependencies ═══$(NC)"
	@./gradlew dependencies --write-locks
	@echo "$(BLUE)═══ Updating npm dependencies ═══$(NC)"
	@cd frontend && pnpm update
	@echo "$(GREEN)✓ Dependencies updated!$(NC)"

## audit: Run security audit
audit:
	@echo "$(BLUE)═══ Auditing npm dependencies ═══$(NC)"
	@cd frontend && pnpm audit
	@echo "$(BLUE)═══ Checking for outdated dependencies ═══$(NC)"
	@./gradlew dependencyUpdates

## ports: List all used ports
ports:
	@echo "$(BLUE)═══ YAPPC Port Allocation ═══$(NC)"
	@echo "  3000 - Frontend (React)"
	@echo "  8000 - Backend API (Node.js)"
	@echo "  8080 - Domain Service (Java)"
	@echo "  8081 - AI Requirements API (Java)"
	@echo "  8082 - Lifecycle API (Java)"
	@echo "  8083 - Canvas AI Service (Java)"
	@echo "  5432 - PostgreSQL"
	@echo "  6379 - Redis"
	@echo ""
	@echo "$(YELLOW)Checking port availability...$(NC)"
	@lsof -i:3000 2>&1 | grep LISTEN || echo "  3000 - $(GREEN)Available$(NC)"
	@lsof -i:8000 2>&1 | grep LISTEN || echo "  8000 - $(GREEN)Available$(NC)"
	@lsof -i:8080 2>&1 | grep LISTEN || echo "  8080 - $(GREEN)Available$(NC)"
	@lsof -i:8081 2>&1 | grep LISTEN || echo "  8081 - $(GREEN)Available$(NC)"
	@lsof -i:8082 2>&1 | grep LISTEN || echo "  8082 - $(GREEN)Available$(NC)"

## docs: Generate documentation
docs:
	@echo "$(BLUE)═══ Generating documentation ═══$(NC)"
	@./gradlew javadoc
	@cd frontend && pnpm run typedoc || echo "$(YELLOW)TypeDoc not configured$(NC)"
	@echo "$(GREEN)✓ Documentation generated!$(NC)"

## release: Create a release (tags and builds)
release:
	@echo "$(RED)Release process not yet automated.$(NC)"
	@echo "Please follow the release guide in docs/RELEASE_GUIDE.md"

## db-migrate: Run database migrations
db-migrate:
	@echo "$(BLUE)═══ Running database migrations ═══$(NC)"
	@./gradlew flywayMigrate
	@echo "$(GREEN)✓ Migrations complete!$(NC)"

## db-reset: Reset database (WARNING: destructive!)
db-reset:
	@echo "$(RED)⚠ WARNING: This will delete all data!$(NC)"
	@read -p "Are you sure? (yes/no): " confirm && [ "$$confirm" = "yes" ]
	@./gradlew flywayClean flywayMigrate
	@echo "$(GREEN)✓ Database reset complete!$(NC)"

## quick-start: Quick start for new developers
quick-start: verify install start-infra
	@echo ""
	@echo "$(GREEN)✓ Quick start complete!$(NC)"
	@echo ""
	@echo "$(BLUE)Next steps:$(NC)"
	@echo "  1. Start backend:  $(YELLOW)make start-backend$(NC)"
	@echo "  2. Start frontend: $(YELLOW)make start-frontend$(NC)"
	@echo "  3. Open browser:   $(YELLOW)http://localhost:3000$(NC)"
	@echo ""
