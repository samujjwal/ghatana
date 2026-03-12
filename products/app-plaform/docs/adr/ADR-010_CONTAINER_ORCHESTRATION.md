# ADR-010: Container Orchestration Platform
## Project Siddhanta - Architectural Decision Record

**Status**: Accepted  
**Date**: 2026-03-08  
**Decision**: Kubernetes (EKS/AKS/GKE/on-prem) with Istio service mesh  
**Impact**: High

---

# CONTEXT

## Problem Statement

Project Siddhanta's microservices architecture (33+ services) requires a container orchestration platform that supports:

- Automated deployment, scaling, and management of containerized services
- Service discovery and load balancing
- Zero-downtime rolling updates and canary deployments
- Multi-environment deployment (dev, staging, UAT, production)
- Air-gapped on-premise deployment alongside cloud deployment
- Service mesh for mTLS, observability, and traffic management
- Resource management and auto-scaling

## Constraints

1. **Multi-Cloud**: Must run on AWS, Azure, GCP, and on-premise
2. **Air-Gap**: Must support fully offline deployment
3. **Scale**: 33+ microservices with independent scaling requirements
4. **Security**: mTLS, network policies, pod security standards
5. **Observability**: Built-in telemetry and monitoring integration

---

# DECISION

## Architecture Choice

**Kubernetes as the container orchestration platform with Istio service mesh for networking, security, and observability. Managed Kubernetes (EKS/AKS/GKE) for cloud, vanilla Kubernetes for on-premise/air-gapped.**

### **Kubernetes Architecture**

#### **Cluster Strategy**
| Environment | Cluster Type | Provider | Nodes |
|-------------|-------------|----------|-------|
| Development | Shared | EKS / Kind | 3 |
| Staging | Shared | EKS / AKS | 5 |
| UAT | Dedicated | EKS / AKS | 8 |
| Production | Dedicated | EKS / on-prem | 15+ |
| DR | Dedicated | Different AZ/Region | 10+ |

#### **Namespace Strategy**
```
siddhanta-kernel          # K-01 to K-19 services
siddhanta-domain          # D-01 to D-14 services
siddhanta-gateway         # K-11 API Gateway, Istio ingress
siddhanta-observability   # K-06 stack (Prometheus, Grafana, Jaeger)
siddhanta-data            # Databases, caches, message brokers
siddhanta-ci              # CI/CD tooling (ArgoCD, runners)
```

### **Istio Service Mesh**

| Capability | Implementation |
|-----------|---------------|
| **mTLS** | Automatic mTLS between all services (STRICT mode) |
| **Traffic Management** | Canary releases, traffic splitting, fault injection |
| **Observability** | Automatic metrics, distributed tracing, access logs |
| **Security** | Authorization policies, network policies |
| **Resilience** | Timeout, retries, circuit breaking at mesh level |

### **Deployment Strategy**

| Strategy | Use Case |
|----------|----------|
| **Rolling Update** | Standard service deployments |
| **Canary** | High-risk deployments (K-05, K-16, D-01) |
| **Blue-Green** | Database schema migrations |
| **Feature Flags** | Progressive feature rollout via K-02/K-10 |

---

# CONSEQUENCES

## Positive Consequences

- **Portability**: Kubernetes runs on any cloud or on-premise
- **Scalability**: Horizontal Pod Autoscaler (HPA) for demand-based scaling
- **Resilience**: Self-healing (restart failed pods, reschedule, replicate)
- **Security**: mTLS, network policies, pod security standards via Istio
- **Observability**: Istio sidecar provides automatic telemetry
- **Ecosystem**: Helm charts, operators, GitOps (ArgoCD)

## Negative Consequences

- **Complexity**: Kubernetes + Istio learning curve
  - **Mitigation**: Platform team abstracts complexity; K-10 Deployment Abstraction provides simplified interfaces
- **Resource Overhead**: Istio sidecar adds ~50MB memory per pod
  - **Mitigation**: Sidecar resource limits; selective Istio injection
- **Debugging Difficulty**: Distributed system debugging is harder
  - **Mitigation**: K-06 Observability stack with distributed tracing

---

# ALTERNATIVES CONSIDERED

## Option 1: Docker Swarm
- **Rejected**: Limited orchestration features; no service mesh; declining community
## Option 2: AWS ECS/Fargate
- **Rejected**: Cloud lock-in; no air-gap support; limited service mesh
## Option 3: HashiCorp Nomad
- **Rejected**: Smaller ecosystem; limited service mesh options; less community support
## Option 4: Bare Metal Deployment
- **Rejected**: No auto-scaling, self-healing, or orchestration; operational overhead

---

# IMPLEMENTATION NOTES

## Infrastructure as Code
- **Terraform**: Cluster provisioning (EKS, node groups, networking)
- **Helm**: Service deployment charts
- **ArgoCD**: GitOps continuous delivery
- **Kustomize**: Environment-specific overlays

## Resource Management
- Resource requests/limits for all pods
- Vertical Pod Autoscaler (VPA) for right-sizing
- Cluster Autoscaler for node scaling
- Pod Disruption Budgets (PDB) for availability

---

**Decision Makers**: Platform Architecture Team, Infrastructure Team  
**Approval Date**: 2026-03-08
