# Swagger UI Integration for Ghatana APIs

This directory contains the Swagger UI configuration and documentation for all Ghatana microservices.

## Overview

The Swagger UI integration provides:
- **Centralized API Documentation Hub** — Single point of access for all API specifications
- **Interactive API Explorer** — Try out API endpoints with live requests
- **OpenAPI 3.1.0 Specifications** — Standard-compliant API definitions
- **Multi-API Support** — TutorPutor, Data Cloud, and YAPPC APIs
- **Authentication Support** — Bearer token (JWT) and API Key validation
- **Cross-API Navigation** — Easy switching between different APIs

## Quick Start

### Prerequisites
- Docker 20.10+
- Docker Compose 2.0+
- 4GB RAM minimum (8GB recommended)
- Ports 8080-8082, 8888, 5432, 6379 available

### Start All Services

```bash
# Make the startup script executable
chmod +x start-swagger-ui.sh

# Start all services (API servers + Swagger UI + Redis + PostgreSQL)
./start-swagger-ui.sh start
```

### Access APIs

| Service | URL | Purpose |
|---------|-----|---------|
| **Swagger UI Hub** | http://localhost:8888 | Central API documentation hub |
| **TutorPutor API** | http://localhost:8080 | Content generation API |
| **Data Cloud API** | http://localhost:8081 | Query execution API |
| **YAPPC API** | http://localhost:8082 | Code generation API |

### Swagger UI URLs

Each API has its own Swagger UI:
- TutorPutor: http://localhost:8080/api/v1/swagger-ui.html
- Data Cloud: http://localhost:8081/api/v1/swagger-ui.html
- YAPPC: http://localhost:8082/api/v1/swagger-ui.html

### OpenAPI Specifications

Download raw OpenAPI specifications (JSON/YAML):
- TutorPutor: http://localhost:8080/api/v1/docs
- Data Cloud: http://localhost:8081/api/v1/docs
- YAPPC: http://localhost:8082/api/v1/docs

## Startup Script Usage

```bash
# Start services (default)
./start-swagger-ui.sh start

# Stop all services
./start-swagger-ui.sh stop

# View live logs from all services
./start-swagger-ui.sh logs

# View logs from a specific service
./start-swagger-ui.sh logs tutorputor-api
./start-swagger-ui.sh logs data-cloud-api
./start-swagger-ui.sh logs swagger-ui

# Check service status
./start-swagger-ui.sh status

# Clean up containers and volumes
./start-swagger-ui.sh clean

# Show help
./start-swagger-ui.sh help
```

## Configuration Files

### docker-compose.swagger.yml
Main Docker Compose configuration that orchestrates:
- **swagger-ui** — Swagger UI frontend (port 8888)
- **tutorputor-api** — TutorPutor microservice (port 8080)
- **data-cloud-api** — Data Cloud microservice (port 8081)
- **yappc-api** — YAPPC microservice (port 8082)
- **redis** — Cache service (port 6379)
- **postgres** — Database service (port 5432)

### swagger-ui-config.yaml
Configuration settings for Swagger UI including:
- API endpoints
- Security schemes (OAuth2, API Key)
- UI preferences (deep linking, syntax highlighting)
- Display options

### swagger-ui-index.html
Custom HTML landing page featuring:
- API cards with descriptions
- Quick access links to each API
- Endpoint summaries
- Download specification buttons
- Responsive design for mobile access

## API Documentation

### TutorPutor Content Generation API
- **5 Endpoints** for content generation, library management, learning paths
- **Grade Level Support** — Elementary through Graduate
- **Format Options** — Markdown, HTML, JSON, Plain Text
- **Authentication** — Bearer token (JWT) required
- **Rate Limiting** — 100-1000 req/min based on tier

### Data Cloud Query API
- **7 Endpoints** for datasets, queries, aggregations, results
- **Dual Authentication** — Bearer token OR API Key (X-API-Key header)
- **Asynchronous Operations** — 202 Accepted responses
- **10 Aggregation Types** — COUNT, SUM, AVG, MIN, MAX, STDDEV, PERCENTILE_*
- **Result Pagination** — Limit 1-10000 rows per request

### YAPPC Code Generation API
- **8 Endpoints** for design management, code generation, refactoring
- **Multi-Language Support** — Java, Python, Go, TypeScript
- **Circular Dependency Detection** — Automatic validation
- **Artifact Versioning** — Track changes across generations
- **Export Formats** — JSON, ZIP, TAR.GZ

## Authentication

All APIs support Bearer token authentication (JWT).

### Getting a Token
```bash
# OAuth2 Authorization
curl -X POST http://localhost:8888/auth/oauth/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&client_id=swagger-ui&client_secret=SECRET"

# Using the token in requests
curl -H "Authorization: Bearer YOUR_TOKEN" \
  http://localhost:8080/api/v1/content/generate
```

### API Key (Optional)
Data Cloud API also supports API Key authentication:
```bash
curl -H "X-API-Key: YOUR_API_KEY" \
  http://localhost:8081/api/v1/datasets
```

## Performance Notes

### Service Startup Times
- Redis: ~2 seconds
- PostgreSQL: ~5 seconds
- API Services: ~10-15 seconds each
- Swagger UI: ~3 seconds
- **Total startup time: ~30-40 seconds**

### Resource Requirements
- **Docker Containers:** 6 total
  - Swagger UI: 200MB memory
  - Each API: 400-500MB memory
  - Redis: 100MB memory
  - PostgreSQL: 200MB memory
- **Total:** ~2GB memory usage

### Load Test SLAs
From production load testing (k6):
- **TutorPutor API:** P95 < 500ms, Success > 99%
- **Data Cloud API:** P95 < 2s, Success > 98%
- **YAPPC API:** P95 < 1s, Success > 99%
- **Cache Hit Ratio:** > 80% with Redis

## Troubleshooting

### Services fail to start
```bash
# Check if ports are already in use
lsof -i :8888
lsof -i :8080
lsof -i :8081
lsof -i :8082
lsof -i :6379
lsof -i :5432

# Free up ports and retry
./start-swagger-ui.sh clean
./start-swagger-ui.sh start
```

### Swagger UI shows "Failed to fetch"
```bash
# This usually means the API services haven't started yet
# Check logs:
./start-swagger-ui.sh logs

# Wait 30-60 seconds and refresh the browser
```

### Can't connect to database
```bash
# Verify PostgreSQL is running
./start-swagger-ui.sh status

# Check PostgreSQL logs
./start-swagger-ui.sh logs postgres

# Try restarting just PostgreSQL
docker-compose -f docker-compose.swagger.yml restart postgres
```

### Redis connection errors
```bash
# Test Redis connectivity
redis-cli -h localhost ping
# Should respond with "PONG"

# Restart Redis if needed
docker-compose -f docker-compose.swagger.yml restart redis
```

## Integration with CI/CD

### GitHub Actions Example
```yaml
- name: Start Swagger UI Services
  run: |
    chmod +x start-swagger-ui.sh
    ./start-swagger-ui.sh start
    sleep 40  # Wait for services to be ready

- name: Run API Tests
  run: |
    npm test  # or your test command
    
- name: Stop Services
  if: always()
  run: ./start-swagger-ui.sh stop
```

### Kubernetes Deployment
The docker-compose configuration can be converted to Kubernetes manifests:
```bash
# Using Kompose to convert
kompose convert -f docker-compose.swagger.yml
```

## Advanced Configuration

### Custom API Specifications
To add a new API to Swagger UI:

1. Add the OpenAPI spec to the appropriate service directory
2. Update `docker-compose.swagger.yml` to expose the spec
3. Add the API card to `swagger-ui-index.html`
4. Update the Swagger UI URLs list in the Compose file

### TLS/HTTPS Configuration
For production deployment, add SSL certificates:
```yaml
swagger-ui:
  environment:
    - PLUGIN: https
    - TLS_CERT_PATH=/etc/nginx/ssl/cert.pem
    - TLS_KEY_PATH=/etc/nginx/ssl/key.pem
  volumes:
    - ./certs:/etc/nginx/ssl:ro
```

### Rate Limiting
Each API has built-in rate limiting:
- TutorPutor: 100-1000 req/min
- Data Cloud: 1000 QPS per user
- YAPPC: 100 code generation jobs/hour

## Monitoring

### Health Checks
All services expose health endpoints:
```bash
# API health
curl http://localhost:8080/health
curl http://localhost:8081/health
curl http://localhost:8082/health

# Swagger UI (port 8888)
curl http://localhost:8888
```

### Metrics and Logs
Real-time monitoring:
```bash
# Stream all service logs
./start-swagger-ui.sh logs

# Stream specific service logs
./start-swagger-ui.sh logs tutorputor-api
```

### Database Monitoring
```bash
# Connect to PostgreSQL
psql -h localhost -U ghatana -d ghatana

# View tables
\dt

# Check connection count
SELECT count(*) FROM pg_stat_activity;
```

## Production Deployment

For production use:
1. Use managed services (RDS for PostgreSQL, ElastiCache for Redis)
2. Deploy APIs on Kubernetes or container orchestration platform
3. Use AWS API Gateway or similar for Swagger UI
4. Enable TLS/HTTPS for all endpoints
5. Implement proper authentication (OAuth2, JWT)
6. Set up load balancing and auto-scaling
7. Configure monitoring and alerting
8. Use secrets management (HashiCorp Vault, AWS Secrets Manager)

## Support and Documentation

- **API Docs:** http://localhost:8888
- **GitHub:** https://github.com/ghatana
- **Documentation:** https://docs.ghatana.io
- **Issues:** https://github.com/ghatana/ghatana/issues

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2026-04-02 | Initial Swagger UI integration with 3 APIs (TutorPutor, Data Cloud, YAPPC) |

---

**Last Updated:** 2026-04-02  
**Maintainer:** Ghatana Engineering Team
