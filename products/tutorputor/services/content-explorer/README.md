# Content Explorer

Content Explorer service with AEP (Agentic Event Processor) integration built using ActiveJ framework.

## 🏗️ Architecture

- **Framework**: ActiveJ (proper Ghatana architecture)
- **AEP Integration**: Library mode (embedded)
- **Async**: Promise-based with ActiveJ Eventloop
- **Database**: PostgreSQL
- **Cache**: Redis

## 🚀 Quick Start

```bash
# Start the Content Explorer service
./dev-run.sh
```

This will:
1. Start PostgreSQL and Redis containers
2. Build and run the ActiveJ application
3. Test all endpoints
4. Serve on http://localhost:8080

## 📊 Available Endpoints

### Main Service
- **GET /** - Service information and status
- **GET /health** - Health check with component status

### AEP Integration
- **GET /api/explorer/status** - AEP integration and service status
- **GET /api/explorer/config** - Complete configuration details

## 🎯 AEP Integration

### Library Mode (Default)
- AEP components embedded within Content Explorer
- No external dependencies
- Fast startup and development
- Event publishing within same process

### Service Mode (Future)
- External AEP microservices
- HTTP-based communication
- Scalable deployment
- Separate service lifecycle

## 🔧 Configuration

The service uses ActiveJ configuration with AEP integration:

```yaml
explorer:
  aep-integration:
    enabled: true
    mode: library
    event-publishing: true
  scheduler:
    enabled: true
  discovery:
    max-concurrent: 5
    timeout: PT10M
  generation:
    max-concurrent: 5
    timeout: PT5M
    ai-enhancement: true
```

## 🛠️ Development

### Prerequisites
- Java 21
- Docker & Docker Compose

### Running
```bash
# Start development server
./dev-run.sh

# Show help
./dev-run.sh --help
```

### Testing
```bash
# Test health endpoint
curl http://localhost:8080/health

# Test status endpoint
curl http://localhost:8080/api/explorer/status

# Test configuration
curl http://localhost:8080/api/explorer/config
```

## 📁 Project Structure

```
apps/content-explorer/
├── dev-run.sh                    # Main development runner
├── docker-compose.yml            # Docker services
├── activej-app/                  # ActiveJ application source
│   └── src/main/java/...         # Java source code
└── README.md                     # This file
```

## 🏛️ Ghatana Architecture Standards

This service follows Ghatana architectural standards:

- ✅ Uses ActiveJ framework for core domain
- ✅ Promise-based async patterns
- ✅ Core HTTP server from `libs:http-server`
- ✅ Proper dependency flow (`products` -> `libs` -> `contracts`)
- ✅ Type-safe implementations
- ✅ Observability integration

## 🔍 Monitoring

The service provides built-in health checks and status endpoints:

- **Health**: Component status and uptime
- **Status**: AEP integration and service status
- **Configuration**: Current configuration and settings

## 🚦 Service Status

When running, the service provides:

- **AEP Integration**: Library mode active
- **Database**: PostgreSQL connection healthy
- **Cache**: Redis connection healthy
- **Scheduler**: Eventloop-based scheduling ready
- **Services**: Discovery, Generation, Quality agents ready

## 📝 Next Steps

1. **Enhanced AEP Integration**: Add more sophisticated agent implementations
2. **Database Integration**: Connect to actual PostgreSQL schemas
3. **AI Integration**: Connect to `libs:ai-integration` services
4. **Observability**: Add metrics and tracing
5. **Testing**: Add comprehensive EventloopTestBase tests

## 🤝 Contributing

Follow Ghatana development standards:
- Use ActiveJ Promise for async operations
- Extend `EventloopTestBase` for tests
- Add proper JavaDoc with `@doc.*` tags
- No code duplication - check `libs/java/*` first
