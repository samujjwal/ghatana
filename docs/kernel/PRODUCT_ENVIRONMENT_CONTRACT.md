# Product Environment Contract

This document defines the contract for product environments.

## Environment Types

Kernel defines standard environment types:

```json
{
  "environmentTypes": {
    "local": "Local development environment",
    "dev": "Development environment",
    "staging": "Staging environment",
    "prod": "Production environment"
  }
}
```

## Environment Configuration

Products declare environment-specific configuration:

```yaml
environment: local

surfaces:
  backend-api:
    port: 8080
    env:
      DATABASE_URL: jdbc:postgresql://localhost:5432/db
      LOG_LEVEL: debug
```

## Environment Variables

Environment variables can be:
- **Static**: Defined in lifecycle configuration files
- **Dynamic**: Provided at deployment time
- **Secrets**: Loaded from secret management systems

## Environment Files

- `lifecycle.local.yaml`: Local development configuration
- `lifecycle.dev.yaml`: Development environment configuration
- `lifecycle.staging.yaml`: Staging environment configuration
- `lifecycle.prod.yaml`: Production environment configuration

## Health Checks

Each environment requires health check configuration:

```json
{
  "surfaces": {
    "backend-api": {
      "type": "http",
      "path": "/health",
      "port": 8080,
      "interval": "30s",
      "timeout": "5s"
    }
  }
}
```

## Environment Promotion

Products are promoted between environments through the promotion workflow:
1. Validate release artifact
2. Apply deployment target configuration
3. Deploy to target environment
4. Verify health checks
5. Emit deployment manifest
