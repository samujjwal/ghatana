# Demo Orchestration Makefile
# Include this file in your main Makefile for demo-related targets

# Default demo configuration
DEMO_NAMESPACE ?= dcmaar-demo
DOCKER_COMPOSE := docker-compose -p $(DEMO_NAMESPACE) -f docker-compose.demo.yml
SCRIPTS_DIR := $(shell pwd)/scripts/demo
REPORTS_DIR := $(shell pwd)/reports/demo
DATA_DIR := $(shell pwd)/data/demo

# Demo targets
.PHONY: help-demo demo demo-up demo-down demo-logs demo-clean demo-smoke demo-validate demo-report demo-preflight demo-seed demo-reset

# No-op target so top-level `make help` can depend on it
help-demo:
	@true

##@ Demo

demo: demo-preflight demo-up demo-validate demo-seed ## Start the demo environment with preflight checks, validation, and data seeding

# Run preflight checks
demo-preflight: check-prerequisites
	@echo "🛫 Running preflight checks..."
	@mkdir -p "$(REPORTS_DIR)"
	@chmod +x "$(SCRIPTS_DIR)/preflight.sh"
	@"$(SCRIPTS_DIR)/preflight.sh" 2>&1 | tee "$(REPORTS_DIR)/preflight-$(shell date +%Y%m%d-%H%M%S).log"
	@echo "✅ Preflight checks completed"

# Start the demo environment
demo-up: check-prerequisites
	@echo "🚀 Starting demo environment..."
	@mkdir -p "$(REPORTS_DIR)/logs"
	@$(DOCKER_COMPOSE) up -d
	@echo "✅ Demo environment started. Use 'make demo-logs' to view logs."

# Seed demo data
demo-seed: check-prerequisites
	@echo "🌱 Seeding demo data..."
	@chmod +x "$(SCRIPTS_DIR)/seed-data.sh"
	@if [ -f "$(SCRIPTS_DIR)/seed-data.sh" ]; then \
		if [ "$(DOCKER_COMPOSE) ps -q server" ]; then \
			echo "📊 Seeding data via API..."; \
			$(DOCKER_COMPOSE) exec -T server /app/bin/seed-demo-data; \
		else \
			echo "📊 Seeding data directly..."; \
			DATA_DIR="$(DATA_DIR)" "$(SCRIPTS_DIR)/seed-data.sh"; \
		fi; \
	else \
		echo "⚠️  Seed script not found at $(SCRIPTS_DIR)/seed-data.sh"; \
		exit 1; \
	fi
	@echo "✅ Demo data seeded successfully"

# Reset demo data
demo-reset: check-prerequisites
	@echo "🔄 Resetting demo data..."
	@if [ -f "$(SCRIPTS_DIR)/seed-data.sh" ]; then \
		if [ "$(DOCKER_COMPOSE) ps -q server" ]; then \
			echo "♻️  Resetting data via API..."; \
			$(DOCKER_COMPOSE) exec -T server /app/bin/reset-demo-data; \
		else \
			echo "♻️  Resetting data directly..."; \
			DATA_DIR="$(DATA_DIR)" RESET_DATA=true "$(SCRIPTS_DIR)/seed-data.sh"; \
		fi; \
	else \
		echo "⚠️  Seed script not found at $(SCRIPTS_DIR)/seed-data.sh"; \
		exit 1; \
	fi
	@echo "✅ Demo data reset successfully"

# Stop the demo environment
demo-down:
	@echo "🛑 Stopping demo environment..."
	@$(DOCKER_COMPOSE) down
	@echo "✅ Demo environment stopped."

# View demo logs
demo-logs:
	@$(DOCKER_COMPOSE) logs -f

# Clean up demo resources
demo-clean: demo-down
	@echo "🧹 Cleaning up demo resources..."
	@$(DOCKER_COMPOSE) down -v --remove-orphans --rmi all
	@rm -rf "$(REPORTS_DIR)" "$(DATA_DIR)"
	@echo "✅ Demo resources cleaned up."

# Run smoke tests against the demo environment
demo-smoke: check-prerequisites
	@echo "🔍 Running smoke tests..."
	@mkdir -p "$(REPORTS_DIR)/tests"
	@$(DOCKER_COMPOSE) exec -T server /app/bin/smoke-test || \
		(echo "❌ Smoke tests failed" && exit 1)
	@echo "✅ Smoke tests passed."

# Validate the demo environment is working
demo-validate: check-prerequisites
	@echo "🔍 Validating demo environment..."
	@# Check if all containers are running
	@if [ "$$($(DOCKER_COMPOSE) ps -q | wc -l)" -eq 0 ]; then \
		echo "❌ No demo containers are running. Run 'make demo-up' first."; \
		exit 1; \
	fi
	@# Check if all services are healthy
	@$(DOCKER_COMPOSE) ps | grep -v "Exit 0" | grep -v "Up" | grep -v "Status" | grep -v "\-\-\-\-" && \
		echo "❌ Some services are not running" && exit 1 || true
	@# Check API server health
	@if ! curl -s http://localhost:8080/health | grep -q '"status":"UP"'; then \
		echo "❌ API server is not healthy"; \
		exit 1; \
	fi
	@echo "✅ Demo environment is healthy"

# Generate demo report
demo-report:
	@echo "📊 Generating demo report..."
	@mkdir -p "$(REPORTS_DIR)"
	@echo "# DCMaar Demo Report" > "$(REPORTS_DIR)/report-$(shell date +%Y%m%d-%H%M%S).md"
	@echo "## System Information" >> "$(REPORTS_DIR)/report-$(shell date +%Y%m%d-%H%M%S).md"
	@echo "- Date: $(shell date)" >> "$(REPORTS_DIR)/report-$(shell date +%Y%m%d-%H%M%S).md"
	@echo "- Hostname: $(shell hostname)" >> "$(REPORTS_DIR)/report-$(shell date +%Y%m%d-%H%M%S).md"
	@echo "- OS: $(shell uname -a)" >> "$(REPORTS_DIR)/report-$(shell date +%Y%m%d-%H%M%S).md"
	@echo "## Docker Information" >> "$(REPORTS_DIR)/report-$(shell date +%Y%m%d-%H%M%S).md"
	@docker --version | sed 's/^/- /' >> "$(REPORTS_DIR)/report-$(shell date +%Y%m%d-%H%M%S).md"
	@docker-compose --version | sed 's/^/- /' >> "$(REPORTS_DIR)/report-$(shell date +%Y%m%d-%H%M%S).md"
	@echo "## Running Containers" >> "$(REPORTS_DIR)/report-$(shell date +%Y%m%d-%H%M%S).md"
	@$(DOCKER_COMPOSE) ps >> "$(REPORTS_DIR)/report-$(shell date +%Y%m%d-%H%M%S).md"
	@echo "✅ Demo report generated at $(REPORTS_DIR)/report-$(shell date +%Y%m%d-%H%M%S).md"

# Check prerequisites
check-prerequisites:
	@if ! command -v docker &> /dev/null; then \
		echo "❌ Docker is not installed. Please install Docker first."; \
		exit 1; \
	fi
	@if ! command -v docker-compose &> /dev/null; then \
		echo "❌ Docker Compose is not installed. Please install Docker Compose."; \
		exit 1; \
	fi
	@if ! docker info &> /dev/null; then \
		echo "❌ Docker daemon is not running. Please start Docker."; \
		exit 1; \
	fi
