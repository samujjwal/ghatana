# Kubernetes Deployment for AEP and Data-Cloud

This directory contains Kubernetes manifests for deploying AEP and Data-Cloud in a Kubernetes cluster.

## Prerequisites

- Kubernetes cluster (v1.24+)
- kubectl configured
- Docker images built and pushed to registry

## Quick Start

### 1. Build Docker Images

```bash
# Build AEP image
cd products/aep
docker build -t ghatana/aep:1.0.0-SNAPSHOT .

# Build Data-Cloud image
cd products/data-cloud
docker build -t ghatana/datacloud:1.0.0-SNAPSHOT .

# Push to registry (if using remote cluster)
docker push ghatana/aep:1.0.0-SNAPSHOT
docker push ghatana/datacloud:1.0.0-SNAPSHOT
```

### 2. Deploy to Kubernetes

```bash
# Create namespace and service accounts
kubectl apply -f k8s/namespace.yaml

# Deploy AEP
kubectl apply -f k8s/aep-configmap.yaml
kubectl apply -f k8s/aep-deployment.yaml
kubectl apply -f k8s/aep-service.yaml

# Deploy Data-Cloud
kubectl apply -f k8s/datacloud-configmap.yaml
kubectl apply -f k8s/datacloud-deployment.yaml
kubectl apply -f k8s/datacloud-service.yaml
```

### 3. Verify Deployment

```bash
# Check pod status
kubectl get pods -n ghatana

# Check services
kubectl get svc -n ghatana

# View logs
kubectl logs -n ghatana -l app=aep
kubectl logs -n ghatana -l app=datacloud

# Port forward for local access
kubectl port-forward -n ghatana svc/aep 8080:8080
kubectl port-forward -n ghatana svc/datacloud 8090:8090
```

## Architecture

```
┌─────────────────────────────────────────┐
│         Kubernetes Cluster              │
│                                         │
│  ┌──────────────┐    ┌──────────────┐  │
│  │     AEP      │    │  Data-Cloud  │  │
│  │              │    │              │  │
│  │  3 replicas  │◄──►│  3 replicas  │  │
│  │              │    │              │  │
│  │  Port: 8080  │    │  Port: 8090  │  │
│  │  Metrics:    │    │  Metrics:    │  │
│  │    9090      │    │    9091      │  │
│  └──────────────┘    └──────────────┘  │
│         │                    │          │
│         └────────┬───────────┘          │
│                  │                      │
│         ┌────────▼────────┐             │
│         │  Persistent     │             │
│         │  Storage        │             │
│         └─────────────────┘             │
└─────────────────────────────────────────┘
```

## Configuration

### AEP Configuration

Edit `aep-configmap.yaml` to configure:

- Worker threads
- Metrics enablement
- Pipeline limits
- Logging settings

### Data-Cloud Configuration

Edit `datacloud-configmap.yaml` to configure:

- Storage backend
- Connection pool size
- Caching settings
- Query timeouts

### Secrets

Create secrets for sensitive data:

```bash
kubectl create secret generic datacloud-secrets \
  -n ghatana \
  --from-literal=DB_PASSWORD=your-password \
  --from-literal=API_KEY=your-api-key
```

## Scaling

### Horizontal Scaling

```bash
# Scale AEP
kubectl scale deployment aep -n ghatana --replicas=5

# Scale Data-Cloud
kubectl scale deployment datacloud -n ghatana --replicas=5
```

### Vertical Scaling

Edit the deployment YAML files to adjust resource requests/limits.

## Monitoring

### Metrics

Both services expose Prometheus metrics:

- AEP: `http://aep:9090/metrics`
- Data-Cloud: `http://datacloud:9091/metrics`

### Health Checks

- AEP: `http://aep:8080/health`
- Data-Cloud: `http://datacloud:8090/health`

## Troubleshooting

### Pod not starting

```bash
# Describe pod
kubectl describe pod -n ghatana <pod-name>

# Check events
kubectl get events -n ghatana --sort-by='.lastTimestamp'
```

### Connection issues

```bash
# Test connectivity
kubectl run -it --rm debug --image=busybox --restart=Never -n ghatana -- sh
# Inside pod:
wget -O- http://aep:8080/health
wget -O- http://datacloud:8090/health
```

### Resource issues

```bash
# Check resource usage
kubectl top pods -n ghatana
kubectl top nodes
```

## Cleanup

```bash
# Delete all resources
kubectl delete -f k8s/

# Or delete namespace (removes everything)
kubectl delete namespace ghatana
```

## Production Considerations

1. **High Availability**
   - Use at least 3 replicas
   - Configure pod anti-affinity
   - Use multiple availability zones

2. **Resource Limits**
   - Set appropriate CPU/memory limits
   - Monitor actual usage and adjust

3. **Persistent Storage**
   - Use appropriate storage class
   - Configure backup/restore
   - Consider using StatefulSets for data persistence

4. **Security**
   - Use network policies
   - Enable RBAC
   - Scan images for vulnerabilities
   - Use secrets for sensitive data

5. **Monitoring**
   - Set up Prometheus/Grafana
   - Configure alerting
   - Enable distributed tracing

6. **Logging**
   - Centralize logs (ELK, Loki, etc.)
   - Set appropriate log levels
   - Implement log rotation

## RBAC Configuration

The `rbac.yaml` manifest provides comprehensive Role-Based Access Control:

### Cluster Roles

- **aep-cluster-reader**: Read-only access to cluster-wide resources (nodes, namespaces, pods)
- **datacloud-cluster-reader**: Read-only access to cluster-wide resources

### Namespace Roles

- **aep-namespace-admin**: Full access to AEP resources within the ghatana namespace
- **datacloud-namespace-admin**: Full access to Data-Cloud resources within the ghatana namespace

### Network Policies

- **aep-network-policy**: Restricts AEP ingress/egress to only necessary connections
- **datacloud-network-policy**: Restricts Data-Cloud ingress/egress to only necessary connections

### Applying RBAC

```bash
# Apply RBAC configuration
kubectl apply -f rbac.yaml

# Verify roles
kubectl get clusterroles | grep -E "aep|datacloud"
kubectl get roles -n ghatana

# Verify bindings
kubectl get clusterrolebindings | grep -E "aep|datacloud"
kubectl get rolebindings -n ghatana
```

## TLS Configuration

Both AEP and Data-Cloud support TLS/HTTPS. Configure via environment variables:

### AEP TLS

```yaml
env:
  - name: AEP_TLS_ENABLED
    value: "true"
  - name: AEP_KEYSTORE_PATH
    value: "/etc/ssl/keystore.p12"
  - name: AEP_KEYSTORE_PASSWORD
    valueFrom:
      secretKeyRef:
        name: aep-tls-secret
        key: keystore-password
```

### Data-Cloud TLS

```yaml
env:
  - name: DATACLOUD_TLS_ENABLED
    value: "true"
  - name: DATACLOUD_KEYSTORE_PATH
    value: "/etc/ssl/keystore.p12"
  - name: DATACLOUD_KEYSTORE_PASSWORD
    valueFrom:
      secretKeyRef:
        name: datacloud-tls-secret
        key: keystore-password
```

## CORS Configuration

Configure CORS for browser-based clients:

```yaml
env:
  - name: AEP_CORS_ENABLED
    value: "true"
  - name: AEP_CORS_ORIGINS
    value: "https://app.example.com,https://admin.example.com"
  - name: AEP_CORS_CREDENTIALS
    value: "true"
```

## Next Steps

- Set up Ingress for external access
- Configure HorizontalPodAutoscaler
- Set up monitoring and alerting
- Configure backup and disaster recovery
