# YAPPC Platform - Quick Start Guide

This guide provides simple deployment options for YAPPC. Choose the option that best fits your needs.

## Option 1: Docker Compose (Recommended for Development)

### Prerequisites
- Docker 20.10+
- Docker Compose 2.0+

### Quick Start

```bash
# Navigate to deployment directory
cd products/yappc/deployment/docker

# Copy environment template
cp .env.example .env

# Start all services
docker compose -f docker-compose-simple.yml up

# Access services
# YAPPC Backend: http://localhost:8082
# YAPPC Web: http://localhost:5173
# AI Requirements: http://localhost:8081
# Lifecycle API: http://localhost:8082
# PostgreSQL: localhost:5432
# Redis: localhost:6379
```

### Stop Services

```bash
docker compose -f docker-compose-simple.yml down
```

### View Logs

```bash
docker compose -f docker-compose-simple.yml logs -f
```

### Custom Configuration

Edit `.env` file to customize:
- Database credentials
- Redis password
- AI provider settings
- JVM options
- Log levels

## Option 2: Kubernetes with Kind (Recommended for Testing)

### Prerequisites
- Kind 0.20+
- kubectl 1.27+

### Quick Start

```bash
# Create Kind cluster
kind create cluster --name yappc

# Apply deployment
kubectl apply -f deployment/kind-simple/k8s-deployment.yml

# Wait for pods to be ready
kubectl wait --for=condition=ready pod -l app=yappc-backend -n yappc --timeout=300s
kubectl wait --for=condition=ready pod -l app=yappc-web -n yappc --timeout=300s

# Port forward to access services
kubectl port-forward svc/yappc-backend 8082:8082 -n yappc
kubectl port-forward svc/yappc-web 5173:5173 -n yappc

# Access services
# YAPPC Backend: http://localhost:8082
# YAPPC Web: http://localhost:5173
```

### Stop Services

```bash
# Delete deployment
kubectl delete -f deployment/kind-simple/k8s-deployment.yml

# Delete Kind cluster
kind delete cluster --name yappc
```

### View Logs

```bash
kubectl logs -f deployment/yappc-backend -n yappc
kubectl logs -f deployment/yappc-web -n yappc
```

## Option 3: Advanced Deployment (Production)

For production deployments, use the existing docker-compose.yml with profiles:

```bash
cd products/yappc/deployment/docker

# Start with specific profile
docker compose --profile backend up
docker compose --profile web up
docker compose --profile full up
```

See [DEPLOYMENT.md](./DEPLOYMENT.md) for detailed production deployment options.

## Validation

### Health Check

```bash
# Docker Compose
curl http://localhost:8082/health
curl http://localhost:8081/health
curl http://localhost:8082/health

# Kubernetes
kubectl exec -n yappc deployment/yappc-backend -- curl http://localhost:8082/health
```

### Run Validation Script

```bash
cd products/yappc/deployment
./validate-deployment.sh
```

## Troubleshooting

### Services Won't Start

1. Check if ports are already in use:
   ```bash
   lsof -i :8082
   lsof -i :5173
   ```

2. Check Docker logs:
   ```bash
   docker compose -f docker-compose-simple.yml logs
   ```

3. Restart services:
   ```bash
   docker compose -f docker-compose-simple.yml restart
   ```

### Database Connection Issues

1. Verify PostgreSQL is healthy:
   ```bash
   docker compose -f docker-compose-simple.yml exec postgres pg_isready -U yappc
   ```

2. Check database credentials in `.env`

### Memory Issues

Increase JVM heap size in `.env`:
```
JVM_OPTS=-Xmx2g -Xms1g
```

## Next Steps

- See [DEPLOYMENT.md](./DEPLOYMENT.md) for advanced deployment options
- See [../docs/](../docs/) for architecture documentation
- See [../README.md](../README.md) for project overview
