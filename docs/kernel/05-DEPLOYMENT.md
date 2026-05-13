# Deployment

## Overview

Kernel deployment manages how products are deployed to different environments with proper validation and rollback capabilities.

## Deployment Targets

Deployment targets are configured in `config/deployment/deployment-targets.json`:

```json
{
  "version": "1.0.0",
  "targets": {
    "local": {
      "type": "compose",
      "adapter": "compose-local",
      "configPath": "deploy/local.compose.yaml"
    },
    "dev": {
      "type": "kubernetes",
      "adapter": "kubernetes",
      "namespace": "dev",
      "configPath": "deploy/k8s/dev"
    },
    "staging": {
      "type": "helm",
      "adapter": "helm",
      "namespace": "staging",
      "chartPath": "deploy/helm/product"
    },
    "prod": {
      "type": "helm",
      "adapter": "helm",
      "namespace": "prod",
      "chartPath": "deploy/helm/product"
    }
  }
}
```

## Deployment Adapters

### ComposeLocalAdapter
- **Target**: local
- **Purpose**: Local development with Docker Compose
- **Operations**: docker compose up, docker compose down, docker compose ps
- **Health Checks**: HTTP endpoints defined in deploy/health-checks.json

### KubernetesDeploymentAdapter
- **Target**: dev, staging, prod
- **Purpose**: Kubernetes deployments
- **Operations**: kubectl apply, kubectl rollout status, kubectl rollout undo
- **Health Checks**: Kubernetes readiness/liveness probes

### HelmDeploymentAdapter
- **Target**: staging, prod
- **Purpose**: Helm chart deployments
- **Operations**: helm install/upgrade, helm test, helm rollback
- **Health Checks**: Helm test hooks

### TerraformDeploymentAdapter
- **Target**: staging, prod
- **Purpose**: Infrastructure as Code deployments
- **Operations**: terraform apply, terraform plan, terraform destroy
- **Health Checks**: Terraform outputs validation

## Deployment Manifest

Deployment manifests describe how a product should be deployed:

```json
{
  "schemaVersion": "1.0.0",
  "productId": "digital-marketing",
  "environment": "staging",
  "target": "kubernetes",
  "surfaces": {
    "backend-api": {
      "replicas": 2,
      "resources": {
        "cpu": "500m",
        "memory": "1Gi"
      },
      "healthChecks": {
        "path": "/health",
        "port": 8080
      }
    },
    "web": {
      "replicas": 2,
      "resources": {
        "cpu": "250m",
        "memory": "512Mi"
      },
      "healthChecks": {
        "path": "/",
        "port": 80
      }
    }
  }
}
```

## Deployment Gates

- **Deployment Gates**: Must pass before deployment
- **Health Check Gates**: Health checks must pass after deployment
- **Rollback Gates**: Rollback plan must exist before non-local deployment

## Rollback

Rollback is always planned before promotion to production. Rollback strategies:
- previous-artifact: Rollback to previous artifact version
- last-known-good: Rollback to last known good version
- manual: Manual rollback with specified version
