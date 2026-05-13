# Security, Privacy, Observability

## Overview

Kernel enforces security, privacy, and observability requirements through gates and policies defined in lifecycle governance.

## Security Gates

### SAST
- **Required**: For all production deployments
- **Check**: Static application security testing
- **Tools**: CodeQL, Semgrep, or equivalent

### Dependency Scan
- **Required**: For all production deployments
- **Check**: Vulnerable dependencies
- **Tools**: Trivy, Snyk, or equivalent

### Container Scan
- **Required**: For all containerized deployments
- **Check**: Container image vulnerabilities
- **Tools**: Trivy, Clair, or equivalent

## Privacy Gates

### Data Classification
- **Required**: For all products with PII
- **Check**: Data classification labels
- **Validation**: PII properly classified and handled

### PII Audit
- **Required**: For all products with PII
- **Check**: PII handling audit
- **Validation**: PII access logs and consent tracking

## Observability Gates

### Metrics Collection
- **Required**: For all production deployments
- **Metrics**: Request rate, error rate, latency, resource usage

### Logging
- **Required**: For all production deployments
- **Fields**: correlation-id, tenant-id, timestamp, level, message

### Tracing
- **Required**: For all production deployments
- **Spans**: Request spans across service boundaries

### Alerting
- **Required**: For all production deployments
- **Alerts**: Error rate threshold, latency threshold, availability threshold

### SLO Validation
- **Required**: For all production deployments
- **SLOs**: Availability, latency, error rate targets

## Governance Policies

### Promotion Policies

Promotion policies are defined in `config/lifecycle/promotion-policies.json`:

```json
{
  "version": "1.0.0",
  "policies": {
    "dev-to-staging": {
      "requirements": {
        "tests": true,
        "security": "basic",
        "codeQuality": true
      }
    },
    "staging-to-prod": {
      "requirements": {
        "tests": true,
        "security": "full",
        "codeQuality": true,
        "coverage": 80,
        "performance": true
      }
    }
  }
}
```

### Approval Gates

Approval gates are defined in `config/lifecycle/approval-gates.json`:

```json
{
  "version": "1.0.0",
  "gates": {
    "code-review": {
      "required": true,
      "approvers": ["tech-lead"],
      "phases": ["build", "package"]
    },
    "security-review": {
      "required": true,
      "approvers": ["security-lead"],
      "environments": ["prod"]
    }
  }
}
```

### Rollback Gates

Rollback gates are defined in `config/lifecycle/rollback-gates.json`:

```json
{
  "version": "1.0.0",
  "gates": {
    "automatic-rollback": {
      "healthCheckFailures": 3,
      "errorRateThreshold": 0.5
    },
    "manual-approval-rollback": {
      "approvers": ["tech-lead", "sre-lead"]
    }
  }
}
```

### Observability Gates

Observability gates are defined in `config/lifecycle/observability-gates.json`:

```json
{
  "version": "1.0.0",
  "gates": {
    "metrics-collection": {
      "requiredMetrics": ["request-rate", "error-rate", "latency"]
    },
    "logging": {
      "requiredFields": ["correlation-id", "tenant-id", "timestamp"]
    },
    "tracing": {
      "requiredSpans": ["http-request", "database-query"]
    }
  }
}
```

## Data Classification

Data classification must influence deployment gates:
- **public**: No special requirements
- **internal**: Internal access controls
- **confidential**: Encryption at rest and in transit
- **restricted**: Additional audit and approval requirements

## Health Checks

No product can deploy without health checks defined in `deploy/health-checks.json`:

```json
{
  "surfaces": {
    "backend-api": {
      "type": "http",
      "path": "/health",
      "port": 8080,
      "interval": 30,
      "timeout": 10
    },
    "web": {
      "type": "http",
      "path": "/",
      "port": 80,
      "interval": 30,
      "timeout": 10
    }
  }
}
```
