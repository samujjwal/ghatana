# Product Deployment Contract

This document defines the contract for product deployments.

## Deployment Targets

Deployment targets are configured in the deployment target registry:

```json
{
  "targets": {
    "compose-local": {
      "adapter": "docker-compose",
      "configPath": "deploy/local.compose.yaml"
    },
    "kubernetes-dev": {
      "adapter": "kubernetes",
      "namespace": "dev",
      "configPath": "deploy/k8s/dev"
    },
    "kubernetes-prod": {
      "adapter": "kubernetes",
      "namespace": "prod",
      "configPath": "deploy/k8s/prod"
    }
  }
}
```

## Deployment Manifest

Each deployment emits a deployment manifest:

```json
{
  "schemaVersion": "1.0.0",
  "productId": "product-id",
  "version": "1.0.0",
  "environment": "prod",
  "deploymentId": "deployment-uuid",
  "surfaces": [
    {
      "surface": "backend-api",
      "status": "deployed",
      "artifactId": "artifact-id",
      "deploymentTarget": "kubernetes-prod",
      "deployedAt": "2024-01-01T00:00:00Z"
    }
  ]
}
```

## Deployment Gates

Deployments must pass gates before execution:

```json
{
  "gates": {
    "security-preflight": "required",
    "health-checks": "required",
    "approval": "required-for-prod",
    "rollback-plan": "required-for-prod"
  }
}
```

## Rollback Plans

Production deployments require rollback plans:

```json
{
  "rollbackPlan": {
    "strategy": "previous-version",
    "targetVersion": "0.9.0",
    "reason": "Deployment failure",
    "steps": [
      "load-previous-artifact",
      "apply-deployment",
      "verify-health"
    ]
  }
}
```

## Deployment Verification

After deployment, verification steps run:
1. Health check endpoint validation
2. Expected surface availability
3. Smoke test execution
4. Monitoring integration verification
