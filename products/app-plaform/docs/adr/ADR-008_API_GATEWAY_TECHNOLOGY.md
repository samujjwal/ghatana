# ADR-008: API Gateway Technology Choice
## Project Siddhanta - Architectural Decision Record

**Status**: Accepted  
**Date**: 2026-03-08  
**Decision**: Custom API Gateway service (K-11) built on Envoy/Istio ingress  
**Impact**: High

---

# CONTEXT

## Problem Statement

Project Siddhanta requires a centralized API entry point that provides:

- Single secure entry for all external and internal API traffic
- Authentication validation (JWT/mTLS via K-01)
- Rate limiting per tenant, per endpoint
- Dynamic route registration as new microservices are deployed
- Jurisdiction-aware routing for multi-market support
- OpenAPI schema validation on incoming requests
- Telemetry injection (trace IDs, correlation IDs)
- Web Application Firewall and DDoS protection

## Constraints

1. **Latency**: Gateway must add <2ms P99 overhead
2. **Throughput**: Support 50,000+ TPS sustained
3. **Availability**: 99.999% uptime (single point of entry)
4. **Air-Gap**: Must work without cloud-specific dependencies
5. **Extensibility**: Dynamic route management without restarts

---

# DECISION

## Architecture Choice

**Build a custom API Gateway service (K-11) leveraging Istio Ingress Gateway (Envoy-based) as the data plane, with a control plane for dynamic route management, jurisdiction-aware routing, and platform-specific authentication integration.**

### **Architecture Design**

#### **Data Plane (Envoy/Istio)**
- High-performance proxy for request routing
- TLS termination and mTLS enforcement
- Built-in observability (metrics, access logs, tracing)
- Connection pooling and load balancing

#### **Control Plane (K-11 Service)**
- Dynamic route registration API
- Rate limiting configuration per tenant/endpoint
- Schema validation against OpenAPI specifications
- Jurisdiction-aware routing rules
- Authentication delegation to K-01 IAM
- Telemetry injection (trace_id, correlation_id, tenant_id)

### **Key Features**

| Feature | Implementation |
|---------|---------------|
| **Auth Validation** | JWT verification via K-01; mTLS via Istio |
| **Rate Limiting** | Token bucket per tenant/endpoint; configurable via K-02 |
| **Routing** | Dynamic routes with health-check-based failover |
| **Schema Validation** | OpenAPI 3.0 request validation |
| **Jurisdiction Routing** | Header/path-based routing to jurisdiction-specific services |
| **Telemetry** | Automatic trace_id, correlation_id, tenant_id injection |
| **WAF** | OWASP ruleset, custom rules, DDoS mitigation |
| **Request Limiting** | Max request size, timeout enforcement |

---

# CONSEQUENCES

## Positive Consequences

- **Unified Entry Point**: Consistent security and observability for all traffic
- **Dynamic Routing**: New services registered without gateway restart
- **Jurisdiction Awareness**: Route to correct service instances per market
- **Observability**: Complete traffic visibility with Istio telemetry
- **Performance**: Envoy's C++ data plane provides <1ms routing latency

## Negative Consequences

- **Single Point of Failure**: Gateway outage blocks all traffic
  - **Mitigation**: Multi-replica deployment, health checks, automatic failover
- **Complexity**: Custom control plane adds development/maintenance cost
  - **Mitigation**: Leverage Istio CRDs for routing, minimize custom logic

---

# ALTERNATIVES CONSIDERED

## Option 1: Kong Gateway
- **Rejected**: Lua-based plugin system less performant; license concerns for enterprise features
## Option 2: AWS API Gateway
- **Rejected**: Cloud lock-in; no air-gap support; limited customization
## Option 3: Nginx Ingress Only
- **Rejected**: No dynamic route management; limited telemetry; no schema validation

---

**Decision Makers**: Platform Architecture Team  
**Approval Date**: 2026-03-08
