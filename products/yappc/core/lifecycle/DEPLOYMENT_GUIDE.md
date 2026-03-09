# YAPPC Lifecycle Platform - Deployment Guide

**Version:** 1.0.0  
**Last Updated:** 2025-01-07

---

## Prerequisites

### Required Software
- Java 21 or higher
- Gradle 8.5 or higher
- Docker 24.0 or higher
- Docker Compose 2.20 or higher
- PostgreSQL 15 or higher
- Redis 7 or higher

### Required Services
- Ghatana AI Integration service
- Ghatana Data Cloud service
- Ghatana Observability service

---

## Local Development Setup

### 1. Clone Repository

```bash
cd /home/samujjwal/Developments/ghatana/products/yappc/lifecycle
```

### 2. Build Application

```bash
./gradlew clean build
```

### 3. Run Tests

```bash
./gradlew test
```

### 4. Start Dependencies

```bash
docker-compose up -d postgres redis
```

### 5. Run Application

```bash
./gradlew run
```

Or with Gradle wrapper:

```bash
java -jar build/libs/yappc-lifecycle-1.0.0.jar
```

### 6. Verify Health

```bash
curl http://localhost:8080/health
```

Expected response: `OK`

---

## Docker Deployment

### 1. Build Docker Image

```bash
docker build -t yappc-lifecycle:1.0.0 .
```

### 2. Run with Docker Compose

```bash
docker-compose up -d
```

This starts:
- YAPPC Lifecycle service (port 8080)
- PostgreSQL database (port 5432)
- Redis cache (port 6379)
- Prometheus metrics (port 9090)
- Grafana dashboards (port 3000)

### 3. View Logs

```bash
docker-compose logs -f yappc-lifecycle
```

### 4. Stop Services

```bash
docker-compose down
```

### 5. Clean Up

```bash
docker-compose down -v  # Remove volumes
```

---

## Kubernetes Deployment

### 1. Create Namespace

```bash
kubectl create namespace yappc
```

### 2. Create ConfigMap

```bash
kubectl create configmap yappc-config \
  --from-file=application.yml \
  --namespace=yappc
```

### 3. Create Secrets

```bash
kubectl create secret generic yappc-secrets \
  --from-literal=db-password=yappc_secret \
  --from-literal=jwt-secret=your-jwt-secret \
  --namespace=yappc
```

### 4. Deploy Application

```bash
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
kubectl apply -f k8s/ingress.yaml
```

### 5. Verify Deployment

```bash
kubectl get pods -n yappc
kubectl get svc -n yappc
```

### 6. View Logs

```bash
kubectl logs -f deployment/yappc-lifecycle -n yappc
```

---

## Configuration

### Environment Variables

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| `YAPPC_ENV` | Environment (development/production) | development | No |
| `DB_URL` | PostgreSQL JDBC URL | jdbc:postgresql://localhost:5432/yappc | Yes |
| `DB_USERNAME` | Database username | yappc | Yes |
| `DB_PASSWORD` | Database password | - | Yes |
| `REDIS_HOST` | Redis host | localhost | Yes |
| `REDIS_PORT` | Redis port | 6379 | Yes |
| `JWT_ISSUER` | JWT token issuer | ghatana | Yes |
| `JWT_AUDIENCE` | JWT token audience | yappc | Yes |

### Application Configuration

Edit `src/main/resources/application.yml`:

```yaml
server:
  port: 8080
  
yappc:
  ai:
    provider: openai
    model: gpt-4
    temperature: 0.3
```

---

## API Endpoints

### Health Check
```bash
GET /health
```

### Intent Phase
```bash
POST /api/v1/yappc/intent/capture
POST /api/v1/yappc/intent/analyze
GET  /api/v1/yappc/intent/:id
```

### Shape Phase
```bash
POST /api/v1/yappc/shape/derive
POST /api/v1/yappc/shape/model
GET  /api/v1/yappc/shape/:id
```

### Validation Phase
```bash
POST /api/v1/yappc/validate
POST /api/v1/yappc/validate/with-config
POST /api/v1/yappc/validate/with-policy
```

### Generation Phase
```bash
POST /api/v1/yappc/generate
POST /api/v1/yappc/generate/diff
GET  /api/v1/yappc/generate/artifacts/:id
```

### API Info
```bash
GET /api/v1/yappc/info
```

---

## Monitoring

### Prometheus Metrics

Access Prometheus at: `http://localhost:9090`

Key metrics:
- `yappc.intent.capture` - Intent capture duration
- `yappc.shape.derive` - Shape derivation duration
- `yappc.validate.execute` - Validation duration
- `yappc.generate.execute` - Generation duration

### Grafana Dashboards

Access Grafana at: `http://localhost:3000`

Default credentials:
- Username: `admin`
- Password: `admin`

Dashboards:
- YAPPC Overview
- Phase Performance
- Error Rates
- Resource Usage

### Application Logs

Logs are output in JSON format:

```json
{
  "timestamp": "2025-01-07T17:00:00Z",
  "level": "INFO",
  "logger": "com.ghatana.yappc.services.intent.IntentServiceImpl",
  "message": "Intent captured successfully",
  "context": {
    "intentId": "intent-123",
    "tenantId": "tenant-123"
  }
}
```

---

## Security

### Authentication

All API endpoints require JWT authentication:

```bash
curl -H "Authorization: Bearer <jwt-token>" \
  http://localhost:8080/api/v1/yappc/intent/capture
```

### Authorization

RBAC policies are enforced:
- `yappc:intent:read` - Read intent specifications
- `yappc:intent:write` - Create/update intent specifications
- `yappc:shape:read` - Read shape specifications
- `yappc:shape:write` - Create/update shape specifications

### Audit Logging

All operations are logged to the audit trail:
- User identity
- Action performed
- Timestamp
- Input/output references

---

## Troubleshooting

### Application Won't Start

1. Check Java version:
   ```bash
   java -version
   ```

2. Check port availability:
   ```bash
   lsof -i :8080
   ```

3. Check logs:
   ```bash
   tail -f logs/yappc.log
   ```

### Database Connection Issues

1. Verify PostgreSQL is running:
   ```bash
   docker-compose ps postgres
   ```

2. Test connection:
   ```bash
   psql -h localhost -U yappc -d yappc
   ```

3. Check credentials in `application.yml`

### Redis Connection Issues

1. Verify Redis is running:
   ```bash
   docker-compose ps redis
   ```

2. Test connection:
   ```bash
   redis-cli ping
   ```

### AI Service Unavailable

1. Check AI service configuration
2. Verify API keys are set
3. Check network connectivity
4. Review AI service logs

---

## Performance Tuning

### JVM Options

For production, use:

```bash
java -Xms2g -Xmx4g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+HeapDumpOnOutOfMemoryError \
  -jar yappc-lifecycle-1.0.0.jar
```

### Database Connection Pool

Adjust in `application.yml`:

```yaml
database:
  pool:
    min_size: 10
    max_size: 50
```

### Redis Configuration

For high-throughput scenarios:

```yaml
redis:
  pool:
    max_total: 50
    max_idle: 20
```

---

## Backup and Recovery

### Database Backup

```bash
docker-compose exec postgres pg_dump -U yappc yappc > backup.sql
```

### Database Restore

```bash
docker-compose exec -T postgres psql -U yappc yappc < backup.sql
```

### Artifact Backup

Artifacts are stored in data-cloud. Follow data-cloud backup procedures.

---

## Scaling

### Horizontal Scaling

Deploy multiple instances behind a load balancer:

```bash
kubectl scale deployment yappc-lifecycle --replicas=3 -n yappc
```

### Vertical Scaling

Increase resource limits:

```yaml
resources:
  limits:
    cpu: "4"
    memory: "8Gi"
  requests:
    cpu: "2"
    memory: "4Gi"
```

---

## Support

For issues or questions:
- Check logs: `/var/log/yappc/`
- Review metrics: Grafana dashboards
- Contact: ghatana-platform-team

---

**Document Version:** 1.0.0  
**Last Updated:** 2025-01-07
