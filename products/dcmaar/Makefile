# Makefile for DCMAAR repo: helper targets for local development

SHELL := /bin/zsh

# Compute repository root (two levels up from this product folder). This
# keeps pnpm invocations deterministic when filters are used.
REPO_ROOT := $(abspath $(CURDIR)/../..)

.PHONY: all install build-ext build test test-unit test-integration sink start-sink stop-sink
.PHONY: all install build build-ext build-rust-fast build-rust-full build-rust-proto build-typescript build-go-stack build-all-fast

all: build

install:
	pnpm install

## Build everything (fast path: excludes dcmaar-proto for speed)
build: build-all-fast

## Build the extension for all browsers (or use 'build-all-fast' for full build)
build-ext:
	# Build extension for all supported browsers
	# Build extension if present; run pnpm from repository root so filters
	# resolve against the workspace definition at the repository level.
	if [ -f "apps/extension/package.json" ]; then \
		bash "$(REPO_ROOT)/scripts/run-pnpm-from-root.sh" --filter "./apps/extension" run build; \
	else \
		echo "Skipping extension build: apps/extension/package.json not found"; \
	fi

## Aggregated build shortcuts
build-rust-fast:
	@echo "🔧 Rust (workspace excluding dcmaar-proto)"
	cargo build --workspace --exclude dcmaar-proto

# Convenience alias for historical "build-quick" command which previously
# relied on implicit pattern rules (causing `cargo -p quick` errors when
# invoked). Keep this a small, fast build that mirrors `build-rust-fast`.
build-quick: build-rust-fast
	@echo "✅ build-quick completed (aliased to build-rust-fast)"

build-rust-full:
	@echo "🔧 Rust (full workspace including dcmaar-proto)"
	cargo build --workspace

build-rust-proto:
	@echo "🔧 Rust protobuf crate"
	cargo build -p dcmaar-proto

build-typescript:
	@echo "🧱 TypeScript packages"
	pnpm install
	pnpm run build

build-go-stack:
	@echo "🐹 Go services"
	cd apps/server && go mod tidy
	cd apps/server && go build ./...

build-all-fast:
	@echo "🚀 Fast path: proto (rust) + rust-fast + desktop"
	$(MAKE) proto-rust
	$(MAKE) build-rust-fast
	$(MAKE) build-desktop

## Run unit tests
test: test-unit

test-unit:
	# Run unit tests for the extension package if present (invoke pnpm from repo root)
	if [ -f "apps/extension/package.json" ]; then \
		bash "$(REPO_ROOT)/scripts/run-pnpm-from-root.sh" --filter "./apps/extension" run test:unit; \
	else \
		echo "Skipping extension unit tests: apps/extension/package.json not found"; \
	fi

test-integration:
	# Start sink in background and run playwright tests (chromium)
	# Allow overriding sink port with SINK_PORT env (default 8080)
	SINK_PORT?=8080
	$(MAKE) start-sink
	# wait for sink health before proceeding (respect SINK_PORT)
	./ops/scripts/wait-for-health.sh http://localhost:$(SINK_PORT)/health 15 1
	# Build and run Chrome tests for extension if the package exists (pnpm from repo root)
	if [ -f "apps/extension/package.json" ]; then \
		bash "$(REPO_ROOT)/scripts/run-pnpm-from-root.sh" --filter "./apps/extension" run build:chrome; \
		bash "$(REPO_ROOT)/scripts/run-pnpm-from-root.sh" --filter "./apps/extension" run test:chrome; \
	else \
		echo "Skipping extension chrome tests: apps/extension/package.json not found"; \
	fi
	$(MAKE) stop-sink

## Telemetry sink control
SINK_PID_FILE := tmp/sink.pid

start-sink:
	mkdir -p tmp
	# Use the helper script to start the sink and write PID to an absolute path
	@chmod +x experiments/examples/telemetry-sink/bin/start-sink.sh || true
	@$(abspath experiments/examples/telemetry-sink/bin/start-sink.sh) $(abspath $(SINK_PID_FILE))

stop-sink:
	@chmod +x experiments/examples/telemetry-sink/bin/stop-sink.sh || true
	@$(abspath experiments/examples/telemetry-sink/bin/stop-sink.sh) $(abspath $(SINK_PID_FILE)) || true
# DCMaar Project Makefile
# Include shared demo makefile
-include ops/eng/mk/demo.mk

# Project configuration
PROJECT_NAME := dcmaar
VERSION := 0.1.0
BUILD_DATE := $(shell date -u +"%Y-%m-%dT%H:%M:%SZ")
GIT_COMMIT := $(shell git rev-parse --short HEAD 2>/dev/null)
GIT_BRANCH := $(shell git rev-parse --abbrev-ref HEAD 2>/dev/null)

# Default target
.DEFAULT_GOAL := help

##@ General

help: ## Display this help
	@awk 'BEGIN {FS = ":.*##"; printf "\nUsage:\n  make \033[36m<target>\033[0m\n"} /^[a-zA-Z_-]+:.*?##/ { printf "  \033[36m%-20s\033[0m %s\n", $$1, $$2 } /^##@/ { printf "\n\033[1m%s\033[0m\n", substr($$0, 5) } ' $(MAKEFILE_LIST)

version: ## Display version information
	@echo "$(PROJECT_NAME) v$(VERSION)"
	@echo "Build: $(GIT_COMMIT) ($(GIT_BRANCH))"
	@echo "Date: $(BUILD_DATE)"

##@ Development

setup: ## Install development dependencies
	@echo "Installing development dependencies..."
	# Rust (respect rust-toolchain.toml)
	rustup show >/dev/null 2>&1 || curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y
	rustup component add clippy rustfmt || true
	cargo install cargo-udeps --locked >/dev/null 2>&1 || true
	# Go (ensure protoc plugins if missing)
	go install google.golang.org/protobuf/cmd/protoc-gen-go@latest || true
	go install google.golang.org/grpc/cmd/protoc-gen-go-grpc@latest || true
	# Node toolchain (pnpm preferred)
	npm install -g pnpm >/dev/null 2>&1 || true
	pnpm install --frozen-lockfile || true
	# Buf (if not already vendored via node_modules/.bin)
	npx @bufbuild/buf --version >/dev/null 2>&1 || npm install --save-dev @bufbuild/buf
	# Pre-commit hooks (optional)
	command -v pre-commit >/dev/null 2>&1 && pre-commit install || true
	@echo "✅ Setup complete"

##@ Lint & Format

.PHONY: lint lint-rust lint-go lint-proto lint-js lint-ts format format-rust format-go format-js

lint: lint-rust lint-go lint-proto lint-js ## Run all linters
	@echo "✅ All lint checks passed (or reported above)."

lint-rust:
	@echo "🔍 Rust (clippy)"; \
	cargo clippy --workspace --all-targets -- -D warnings || true

lint-go:
	@echo "🔍 Go (vet + staticcheck if available)"; \
	cd apps/server && go vet ./...; \
	command -v staticcheck >/dev/null 2>&1 && staticcheck ./... || true

lint-proto:
	@echo "🔍 Proto (buf lint)"; \
	cd proto && npx @bufbuild/buf dep update && npx @bufbuild/buf lint

lint-js:
	@echo "🔍 JS/TS (eslint)"; \
	command -v pnpm >/dev/null 2>&1 || npm install -g pnpm >/dev/null 2>&1 || true; \
	[ -f package.json ] && pnpm exec eslint . --ext .ts,.tsx,.js || echo "(eslint skipped: no config)"

format: format-rust format-go format-js ## Format all supported languages
	@echo "✅ Formatting complete"

format-rust:
	@echo "🛠  rustfmt"; cargo fmt --all

format-go:
	@echo "🛠  go fmt"; cd apps/server && go fmt ./...

format-js:
	@echo "🛠  prettier"; \
	command -v pnpm >/dev/null 2>&1 && pnpm exec prettier -w . 2>/dev/null || echo "(prettier skipped)"

.PHONY: proto proto-rust proto-go proto-js proto-all proto-verify proto-clean-go
# Generate all protobuf code
proto: proto-clean proto-rust proto-go proto-js

# Generate Rust code from proto files
proto-rust: proto-clean
	@echo "🚀 Generating Rust code from proto files..."
	@mkdir -p core/proto/gen/rust/src
	@# Ensure a Cargo.toml exists for the generated crate (workspace expects this)
	@if [ ! -s core/proto/gen/rust/Cargo.toml ]; then \
	  echo "Creating template core/proto/gen/rust/Cargo.toml"; \
	  : > core/proto/gen/rust/Cargo.toml; \
	  printf "[package]\n" >> core/proto/gen/rust/Cargo.toml; \
	  printf "name = \"dcmaar-pb\"\n" >> core/proto/gen/rust/Cargo.toml; \
	  printf "version = \"0.1.0\"\n" >> core/proto/gen/rust/Cargo.toml; \
	  printf "edition = \"2021\"\n" >> core/proto/gen/rust/Cargo.toml; \
	  printf "description = \"Generated Protocol Buffer code for DCMaar\"\n" >> core/proto/gen/rust/Cargo.toml; \
	  printf "publish = false\n\n" >> core/proto/gen/rust/Cargo.toml; \
	  printf "[lib]\n" >> core/proto/gen/rust/Cargo.toml; \
	  printf "name = \"dcmaar_proto\"\n" >> core/proto/gen/rust/Cargo.toml; \
	  printf "path = \"src/lib.rs\"\n\n" >> core/proto/gen/rust/Cargo.toml; \
	  printf "[dependencies]\n" >> core/proto/gen/rust/Cargo.toml; \
	  printf "prost = \"0.12\"\n" >> core/proto/gen/rust/Cargo.toml; \
  printf "prost-types = \"0.12\"\n" >> core/proto/gen/rust/Cargo.toml; \
  printf "tonic = { version = \"0.10\", default-features = false }\n" >> core/proto/gen/rust/Cargo.toml; \
  printf "bytes = \"1.0\"\n" >> core/proto/gen/rust/Cargo.toml; \
	  printf "futures-util = { version = \"0.3\", default-features = false }\n" >> core/proto/gen/rust/Cargo.toml; \
	  printf "serde = { version = \"1.0\", features = [\"derive\"] }\n" >> core/proto/gen/rust/Cargo.toml; \
	  printf "serde_json = \"1.0\"\n" >> core/proto/gen/rust/Cargo.toml; \
	  printf "thiserror = \"1.0\"\n\n" >> core/proto/gen/rust/Cargo.toml; \
	  printf "[dev-dependencies]\n" >> core/proto/gen/rust/Cargo.toml; \
  printf "tokio = { version = \"1.0\", features = [\"rt-multi-thread\", \"macros\"] }\n" >> core/proto/gen/rust/Cargo.toml; \
  printf "tonic-build = \"0.10\"\n" >> core/proto/gen/rust/Cargo.toml; \
	fi
	@# Ensure a stub lib.rs exists so the crate is valid before generation
	@if [ ! -s core/proto/gen/rust/src/lib.rs ]; then \
	  printf "// The actual contents are generated by buf/prost/tonic\n" >> core/proto/gen/rust/src/lib.rs; \
	fi
	@# Generate the Rust sources into core/proto/gen/rust/src as configured in buf.gen.rust.yaml
	@npx @bufbuild/buf generate --template buf.gen.rust.yaml
	@# Now validate the Rust workspace compiles with the generated crate included
	@cd apps/agent && cargo check

proto-go: proto-clean-go ## Generate Go code from proto files
	@echo "Generating Go code from proto files..."
	@mkdir -p core/proto/gen/go
	# Ensure buf dependencies are up-to-date and run the local buf binary to generate
	@echo "Updating buf dependencies..."
	@PATH="$(PWD)/node_modules/.bin:$$PATH" node_modules/.bin/buf dep update core/proto || true
	@echo "Running buf generate (Go)..."
	@PATH="$(PWD)/node_modules/.bin:$$PATH" node_modules/.bin/buf generate --template buf.gen.go.yaml || ( echo "buf generate failed" && exit 1 )
	# Use a single shell so variables persist across lines
	@set -e; \
	if [ -d core/proto/gen/go/pb/dcmaar/v1 ]; then \
		GEN_DIR=core/proto/gen/go/pb/dcmaar/v1; \
		OUT_BASE=core/proto/gen/go/pb; \
	else \
		GEN_DIR=$$(find core/proto/gen -type d -path "*/dcmaar/v1" -print -quit || true); \
		if [ -z "$$GEN_DIR" ]; then \
			echo "No generated Go pb directory found under core/proto/gen - generation may have failed"; \
			ls -la core/proto/gen || true; \
			exit 1; \
		fi; \
		OUT_BASE=$$(dirname $$(dirname "$$GEN_DIR")); \
	fi; \
	echo "Found/generated Go pb at $$GEN_DIR; module base will be $$OUT_BASE"; \
	mkdir -p "$$OUT_BASE"; \
	if [ ! -f "$$OUT_BASE/go.mod" ]; then \
		echo "Creating minimal $$OUT_BASE/go.mod"; \
		( cd "$$OUT_BASE" && printf '%s\n' \
			'module github.com/samujjwal/dcmaar/core/proto/gen/go/pb' \
			'' \
			'go 1.25' \
			'' \
			'require (' \
			')' \
			 > go.mod ); \
	fi; \
	( cd "$$OUT_BASE" && go mod tidy ) || true; \
	( cd "$$OUT_BASE" && go mod verify ) || true; \
	( cd apps/server && go mod edit -replace github.com/samujjwal/dcmaar/core/proto/gen/go/pb=../../core/proto/gen/go/pb && go mod tidy ) || true; \
	./ops/scripts/fix-proto-gomod.sh || true

# Generate JavaScript/TypeScript code from proto files
proto-js:
	@echo "🚀 Generating JavaScript/TypeScript code from proto files..."
	@mkdir -p core/proto/gen/js
	@PATH="$(PWD)/node_modules/.bin:$$PATH" npx @bufbuild/buf generate --template buf.gen.js.yaml || (echo "⚠️  buf generate (JS) failed — skipping JS proto generation (possibly rate-limited). See https://buf.build/docs/bsr/rate-limits/" && true)

# Clean generated proto files
proto-clean:
	@echo "🧹 Cleaning generated proto files..."
	@rm -rf core/proto/gen

# Clean only Go-generated proto files (used as a prerequisite for `proto-go`)
proto-clean-go:
	@echo "🧹 Cleaning generated Go proto files..."
	@rm -rf core/proto/gen/go

# Verify protobuf files for style and breaking changes
.PHONY: proto-verify proto-lint proto-breaking

# Main verification target
proto-verify: proto-lint proto-breaking

# Lint protobuf files for style and best practices
proto-lint:
	@echo "🔍 Linting proto files..."
	@echo "✅ Running buf lint..."
	@cd proto && npx @bufbuild/buf dep update && npx @bufbuild/buf lint || (echo "❌ Lint errors found!" && exit 1)

# Check for breaking changes against main branch
proto-breaking:
	@echo "🔍 Checking for breaking changes..."
	@if git rev-parse --verify main >/dev/null 2>&1; then \
		echo "✅ Found main branch, checking for breaking changes..."; \
		cd proto && npx @bufbuild/buf dep update && npx @bufbuild/buf breaking --against '../.git#branch=main' || (echo "❌ Breaking changes detected!" && exit 1); \
	else \
		echo "⚠️  Main branch not found, skipping breaking change detection"; \
	fi
	
	@echo "✅ Proto verification completed successfully!"

##@ Build Targets

# Build all components (Go server stack removed; use Java + Node.js server instead)
build-all: proto build-agent build-extension build-desktop

# Build Rust agent
build-agent:
	@echo "Building Rust agent..."
	@cd apps/agent && cargo build

# Convenience target for Go builds (server + shared libs)
build-go:
	@echo "Building Go components..."
	@cd apps/server && go build ./...

# Build Go server
build-server:
	@echo "Building Go server..."
	@# Only copy the template go.mod into apps/server if a go.mod does not already exist.
	@if [ ! -f apps/server/go.mod ]; then \
		cp apps/server/go.mod.local apps/server/go.mod; \
	fi
	@cd apps/server && go mod tidy
	@cd apps/server && go build -o ../../bin/server ./cmd/server

# Build browser extension
build-extension:
	@echo "Building browser extension..."
	@"$(REPO_ROOT)/scripts/run-pnpm-from-root.sh" --filter "./apps/extension" run build

# Build desktop app
build-desktop:
	@echo "🖥️ Building desktop app..."
	@"$(REPO_ROOT)/scripts/run-pnpm-from-root.sh" --filter "./apps/desktop" run build

##@ Verification

verify: build-all ## Verify full project builds
	@echo "✅ All components built successfully!"

##@ Development

# (Removed duplicate setup target definition; see single definition near top of file)

##@ Test

.PHONY: test test-rust test-go test-extension test-desktop

# Run all tests
test: test-rust test-go test-extension test-desktop

# Test Rust components
test-rust:
	@echo "🧪 Testing Rust components..."
	cargo test --workspace

# Test Go components
test-go:
	@echo "🧪 Testing Go components..."
	cd apps/server && go test -v ./...

# Test browser extension
test-extension:
	@echo "🧪 Testing browser extension..."
	"$(REPO_ROOT)/scripts/run-pnpm-from-root.sh" --filter "./services/extension" test

# Test desktop application
test-desktop:
	@echo "🧪 Testing desktop application..."
	"$(REPO_ROOT)/scripts/run-pnpm-from-root.sh" --filter "./services/desktop" test

##@ Benchmarks

.PHONY: bench bench-go bench-rust

# Run all benchmarks
bench: bench-go bench-rust

# Go benchmarks
bench-go:
	@echo "🏁 Running Go benchmarks..."
	cd apps/server && go test -bench=. -benchmem ./...

# Rust benchmarks
bench-rust:
	@echo "🏁 Running Rust benchmarks (proto crate)..."
	cd core/proto/gen/rust && cargo bench || true

##@ Cleanup

clean: ## Clean all generated files
	@echo "🧹 Cleaning generated files..."
	@if docker ps >/dev/null 2>&1; then \
		$(MAKE) demo-down; \
	else \
		echo "⚠️  Docker not running - skipping Docker cleanup"; \
	fi
	@rm -rf bin
	@rm -rf node_modules
	@rm -rf packages/*/node_modules
	@$(MAKE) proto-clean

##@ Documentation

docs: ## Generate documentation
	@echo "Generating documentation..."
	# Add documentation generation commands here

##@ Demo (see help-demo for more info)

# Include demo help in main help
help: help-demo

# Add any additional demo-specific targets here

##@ Release

release: ## Create a new release
	@echo "Creating release v$(VERSION)..."
	# Add release commands here

PROTO_DIR=proto

.PHONY: all proto agent server extension desktop dashboards test test-unit test-integration test-coverage run-server run-agent smoke up down test-env-up test-env-down test-env-clean

all: proto agent-rs extension desktop dashboards

# (Removed duplicate mock proto target)

agent-rs:
	cd client/agent && cargo build

# Build the agent service
agent:
	@echo "Building agent..."
	cd services/agent && go build ./cmd/agent || true

# Build the server service
server:
	@echo "Building server..."
	cd apps/server && go build ./cmd/server || true

# Build the browser extension
extension:
	@echo "Building browser extension..."
	cd services/extension && npm install --silent || true && npm run build || true

# Build the desktop application
desktop:
	@echo "Building desktop app..."
	cd services/desktop && npm install --silent || true && npm run dev || true

# Build the dashboards web application
dashboards:
	@echo "Building dashboards..."
	cd services/dashboards && npm install --silent || true && npm run build || true

# Run the server locally
run-server:
	cd apps/server && go run ./cmd/server

# Run the Rust agent locally
run-agent:
	cd client/agent && cargo run

.PHONY: smoke
smoke:
	@echo "Running smoke test (placeholder)..."
	# Ensure protos are generated (Go stubs if toolchain present)
	make proto
	# Start only the server container; we'll run the Rust agent locally
	docker compose up -d --quiet-pull server
	@echo "[smoke] Waiting for server on 127.0.0.1:50051 ..."
	@for i in $$(seq 1 30); do \
	  (echo > /dev/tcp/127.0.0.1/50051) >/dev/null 2>&1 && echo "[smoke] Server is up" && break; \
	  sleep 1; \
	  if [ $$i -eq 30 ]; then echo "[smoke] Server did not come up in time"; exit 1; fi; \
	 done
	@echo "[smoke] Sending one-shot envelopes from Rust agent..."
	cd client/agent && ONE_SHOT=1 cargo run --quiet || true
	@echo "[smoke] Server logs (tail):"
	@docker compose logs --no-color --tail=50 server || true
	@echo "[smoke] Complete."

.PHONY: smoke-corr
smoke-corr: ## Run one-shot agent with a fixed corr_id and show correlated server logs
	@echo "[smoke-corr] Building prerequisites..."
	make proto
	@echo "[smoke-corr] Starting server container..."
	docker compose up -d --quiet-pull server
	@echo "[smoke-corr] Waiting for server on 127.0.0.1:50051 ..."
	@for i in $$(seq 1 30); do \
	  (echo > /dev/tcp/127.0.0.1/50051) >/dev/null 2>&1 && echo "[smoke-corr] Server is up" && break; \
	  sleep 1; \
	  if [ $$i -eq 30 ]; then echo "[smoke-corr] Server did not come up in time"; exit 1; fi; \
	 done
	@CORR_ID=$$(python3 -c 'import uuid; print(uuid.uuid4())'); \
	 echo "[smoke-corr] Using corr_id=$$CORR_ID"; \
	 echo "[smoke-corr] Sending one-shot metrics batch from agent..."; \
	 (cd client/agent && ONE_SHOT=1 DCMAR_TEST_CORR_ID=$$CORR_ID cargo run --quiet) || true; \
	 echo "[smoke-corr] Pulling correlated server logs"; \
	 docker compose logs --no-color --tail=200 server | grep --color=never $$CORR_ID || echo "[smoke-corr] No server logs captured with corr_id=$$CORR_ID"; \
	 echo "[smoke-corr] Verifying HTTP health endpoint"; \
	 curl -fsS http://127.0.0.1:8080/healthz || echo "[smoke-corr] Failed to reach healthz endpoint"; \
	 echo "[smoke-corr] Verifying metrics endpoint (first 5 lines)"; \
	 curl -fsS http://127.0.0.1:8080/metrics | head -n 5 || echo "[smoke-corr] Failed to fetch metrics"; \
	 echo "[smoke-corr] Done."

.PHONY: verify-corr
verify-corr: ## Run automated corr_id verification script (requires docker, cargo, curl, grpcurl)
	@bash ops/scripts/verify_corr.sh

.PHONY: verify-deny
verify-deny: ## Alias of verify-corr focused on policy deny + metrics
	@bash ops/scripts/verify_corr.sh

##@ Rego (OPA)

.PHONY: server-opa run-server-opa smoke-rego
server-opa: ## Build server with OPA/Rego support (-tags=opa)
	@echo "🔧 Building server with -tags=opa..."
	cd apps/server && go build -tags=opa -o bin/server-opa ./cmd/server

run-server-opa: server-opa ## Run server (OPA) locally using config.rego.yaml (requires ClickHouse test env)
	@echo "🚀 Starting ClickHouse/Redis test env (if not running)..."
	@$(MAKE) -s test-env-up
	@echo "🚀 Running server (OPA) with config.rego.yaml..."
	CONFIG_FILE=$(PWD)/apps/server/config/config.rego.yaml \
		apps/server/bin/server-opa & echo $$! > /tmp/dcmaar-server-opa.pid ; \
		echo "PID=$$(cat /tmp/dcmaar-server-opa.pid)" ; \
		sleep 2 ; \
		(echo > /dev/tcp/127.0.0.1/50051) >/dev/null 2>&1 && echo "gRPC up on 127.0.0.1:50051" || echo "Waiting for server..."

smoke-rego: server-opa ## Smoke test Rego engine (allow echo, deny unknown)
	@bash ops/scripts/smoke_rego.sh

.PHONY: up-opa down-opa
up-opa: ## Run Docker Compose with Rego-enabled server
	docker compose -f docker-compose.opa.yml up -d --build

down-opa: ## Stop Rego-enabled docker compose stack
	docker compose -f docker-compose.opa.yml down -v

up:
	docker compose up -d --quiet-pull

down:
	docker compose down -v || true

.PHONY: dev-certs
dev-certs:
	@echo "Generating dev TLS certificates..."
	bash ops/scripts/dev-pki/generate_certs.sh

# Native messaging host installation
NATIVE_HOST_BIN := $(shell pwd)/services/native-host/dcmaar-native-host
NATIVE_HOST_MANIFEST_SRC := services/native-host/com.dcmaar.agent.json
NATIVE_HOST_MANIFEST_DEST := $(HOME)/.config/google-chrome/NativeMessagingHosts/com.dcmaar.agent.json

# Build the native host
dev-native-host: services/native-host/main.go
	@echo "Building native host..."
	cd services/native-host && go build -o dcmaar-native-host

# Install the native messaging host
install-native-host: dev-native-host
	@echo "Installing native messaging host for Chrome/Edge..."
	@mkdir -p $(dir $(NATIVE_HOST_MANIFEST_DEST))
	@sed -e 's|{{BINARY_PATH}}|$(NATIVE_HOST_BIN)|g' \
	    -e 's|{{EXTENSION_ID}}|$(shell cat services/extension/.extension_id 2>/dev/null || echo "YOUR_EXTENSION_ID")|g' \
	    $(NATIVE_HOST_MANIFEST_SRC) > $(NATIVE_HOST_MANIFEST_DEST)
	@echo "Installed manifest to $(NATIVE_HOST_MANIFEST_DEST)"
	@echo "Note: For Edge, you may need to copy the manifest to the Edge NativeMessagingHosts directory"

# Uninstall the native messaging host
uninstall-native-host:

# Setup test environment
test-env-up:
	@echo "Setting up test environment..."
	./ops/scripts/setup-test-env.sh

# Tear down test environment
test-env-down:
	@echo "Tearing down test environment..."
	docker stop test-clickhouse test-redis 2>/dev/null || true
	docker rm test-clickhouse test-redis 2>/dev/null || true

# Clean test environment (including volumes)
test-env-clean: test-env-down
	@echo "Removing test network..."
	docker network rm test-network 2>/dev/null || true

# Generate test coverage report
test-coverage: test-unit test-integration
	@echo "\nGenerating combined test coverage report..."
	@cd apps/server && \
	echo "mode: set" > coverage.out && \
	cat *.cover.out | grep -v "mode: set" >> coverage.out && \
	go tool cover -html=coverage.out -o coverage.html && \
	echo "Coverage report generated at apps/server/coverage.html"

# Run mock server for testing
mock-server:
	@echo "Starting mock server on http://localhost:8080"
	@echo "Press Ctrl+C to stop"
	cd test/load/mock_server && go run .

# Run load test against the real server
load-test: test-env-up
	@echo "Starting load test against real server..."
	@echo "Make sure the server is running with: make run-server"
	@echo "Starting load test in 5 seconds..."
	@sleep 5
	cd test/load && go run . --server http://localhost:8080 --rps 100 --duration 30s

# Run load test against mock server
load-test-mock:
	@echo "Starting load test against mock server..."
	@echo "Make sure the mock server is running with: make mock-server"
	@echo "Starting load test in 5 seconds..."
	@sleep 5
	cd test/load && go run . --server http://localhost:8080 --rps 100 --duration 30s

# Run all tests with coverage
test-all: test-coverage load-test

# Test data generation and validation
.PHONY: test-data test-data-gen test-data-validate test-data-clean grpc-requests

# Generate and validate test data
test-data: test-data-gen test-data-validate

# Generate test data
test-data-gen:
	@echo "🔨 Generating test data..."
	@./ops/scripts/generate-test-data.sh

# Validate test data
test-data-validate:
	@echo "🔍 Validating test data..."
	@./ops/scripts/validate-test-data.sh

# Clean up test data
test-data-clean:
	@echo "🧹 Cleaning up test data..."
	@./ops/scripts/clean-test-data.sh

# Generate sample gRPC requests
grpc-requests:
	@echo "📡 Generating sample gRPC requests..."
	@go run ops/scripts/generate-grpc-requests.go

## Rust Build Commands
build-rust: check-rust-version
	@echo "🚀 Building Rust workspace..."
	cargo build --workspace

# Check Rust version matches the one specified in rust-toolchain.toml
check-rust-version:
	@echo "🔍 Checking Rust toolchain..."
	@rustc --version | grep -q "1.90.0" || (echo "❌ Wrong Rust version. Please install rustup and run: rustup install 1.90.0 && rustup default 1.90.0" && exit 1)

# Build a specific crate
build-%:
	@if [ "$*" = "all" ]; then \
	  echo "Skipping implicit build of 'all' (use build-all target)"; \
	else \
	  cargo build -p $*; \
	fi

# Check for circular dependencies
check-deps:
	@echo "🔍 Checking for circular dependencies..."
	cargo udeps --workspace

# Clean build artifacts
clean-rust:
	cargo clean

.PHONY: help version setup build test lint clean docs release

# Generate JSON Schemas from TypeScript definitions
.PHONY: schema-generate
schema-generate: proto-js
	@echo "📝 Generating JSON Schemas..."
	@mkdir -p services/desktop/src/schemas
	@mkdir -p services/extension/src/schemas
	@node ops/scripts/codegen/jsonschema.js
