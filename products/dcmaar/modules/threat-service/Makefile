# Go parameters
GOCMD=go
GOBUILD=$(GOCMD) build
GOCLEAN=$(GOCMD) clean
GOTEST=$(GOCMD) test
GOGET=$(GOCMD) get
GOMOD=$(GOCMD) mod
BINARY_NAME=query-service
DOCKER_COMPOSE=docker-compose -f docker-compose.yml -f docker-compose.override.yml

# Default target
help:
	@echo "Available targets:"
	@echo "  build           - Build the application"
	@echo "  run             - Run the application locally"
	@echo "  test            - Run tests"
	@echo "  test-cover      - Run tests with coverage"
	@echo "  lint            - Run linters"
	@echo "  fmt             - Format code"
	@echo "  tidy            - Tidy Go modules"
	@echo "  docker-build    - Build Docker image"
	@echo "  docker-up       - Start all services"
	@echo "  docker-down     - Stop all services"
	@echo "  docker-logs     - Show logs"
	@echo "  docker-bash     - Open bash in the service container"
	@echo "  benchmark       - Run benchmark tests"

# Build the application
build:
	@echo "Building server..."
	$(GOBUILD) -o bin/$(BINARY_NAME) ./cmd/server

# Run the application
run: build
	./bin/$(BINARY_NAME) --config config/config.docker.yaml

# Run tests
test:
	$(GOTEST) -v -race -coverprofile=coverage.out ./...

# Run tests with coverage
test-cover: test
	$(GOCMD) tool cover -html=coverage.out

# Run linters
lint:
	golangci-lint run --timeout 5m

# Format code
fmt:
	gofmt -s -w .
	goimports -w .

# Tidy Go modules
tidy:
	$(GOMOD) tidy

# Build Docker image
docker-build:
	$(DOCKER_COMPOSE) build

# Start all services
docker-up:
	$(DOCKER_COMPOSE) up -d

# Stop all services
docker-down:
	$(DOCKER_COMPOSE) down --remove-orphans

# Show logs
docker-logs:
	$(DOCKER_COMPOSE) logs -f query-service

# Open bash in the service container
docker-bash:
	$(DOCKER_COMPOSE) exec query-service /bin/bash

# Generate protobuf and gRPC code
gen-proto:
	protoc --go_out=. --go-grpc_out=. proto/**/*.proto

# Generate database migrations
gen-migration:
	@if [ -z "$(name)" ]; then \
		echo "Usage: make gen-migration name=your_migration_name"; \
		exit 1; \
	fi
	migrate create -ext sql -dir migrations -seq $(name)

# Run database migrations
migrate-up:
	migrate -path migrations -database "clickhouse://clickhouse:9000/default" up

# Rollback the last database migration
migrate-down:
	migrate -path migrations -database "clickhouse://clickhouse:9000/default" down 1

# Show database migration status
migrate-status:
	migrate -path migrations -database "clickhouse://clickhouse:9000/default" version

# Generate Swagger documentation
swagger:
	swag init -g cmd/server/main.go --output docs/swagger

# Generate mocks
gen-mocks:
	mockgen -source=internal/service/query/service.go -destination=internal/service/query/mocks/service_mock.go -package=mocks

# Clean build files
clean:
	$(GOCLEAN)
	rm -f bin/$(BINARY_NAME)
	rm -f coverage.out

# Install development dependencies
dev-deps:
	# Install golangci-lint
	curl -sSfL https://raw.githubusercontent.com/golangci/golangci-lint/master/install.sh | sh -s -- -b $(shell go env GOPATH)/bin v1.53.3
	
	# Install swag
	$(GOGET) -u github.com/swaggo/swag/cmd/swag@latest
	
	# Install migrate
	$(GOGET) -tags 'clickhouse' -u github.com/golang-migrate/migrate/v4/cmd/migrate@latest
	
	# Install mockgen
	$(GOGET) -u github.com/golang/mock/mockgen@latest

# Run benchmark tests
benchmark: export CLICKHOUSE_DSN=clickhouse://test:test@localhost:9000/test?debug=true
benchmark: export REDIS_ADDR=localhost:6379
benchmark:
	@echo "Running benchmark tests..."
	@go test -bench=. -tags=integration -v ./internal/testhelper/... -benchmem -count=3

# Default target
.DEFAULT_GOAL := help
