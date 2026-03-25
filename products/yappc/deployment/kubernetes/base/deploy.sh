#!/bin/bash
# YAPPC Kubernetes Deployment Script
# Automates deployment of YAPPC platform to Kubernetes cluster

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo "════════════════════════════════════════════════════════════════════════"
echo "YAPPC Kubernetes Deployment"
echo "════════════════════════════════════════════════════════════════════════"
echo ""

# Check prerequisites
check_prerequisites() {
    echo "🔍 Checking prerequisites..."
    
    # Check kubectl
    if ! command -v kubectl &> /dev/null; then
        echo -e "${RED}✗ kubectl not found${NC}"
        echo "Install: https://kubernetes.io/docs/tasks/tools/"
        exit 1
    fi
    echo -e "${GREEN}✓ kubectl found${NC}"
    
    # Check helm
    if ! command -v helm &> /dev/null; then
        echo -e "${RED}✗ helm not found${NC}"
        echo "Install: https://helm.sh/docs/intro/install/"
        exit 1
    fi
    echo -e "${GREEN}✓ helm found${NC}"
    
    # Check cluster connection
    if ! kubectl cluster-info &> /dev/null; then
        echo -e "${RED}✗ Cannot connect to Kubernetes cluster${NC}"
        echo "Configure kubectl context first"
        exit 1
    fi
    echo -e "${GREEN}✓ Connected to Kubernetes cluster${NC}"
    
    echo ""
}

# Install prerequisites (CRDs, operators)
install_prerequisites() {
    echo "📦 Installing prerequisites..."
    
    # Install NVIDIA Device Plugin
    echo "Installing NVIDIA Device Plugin..."
    kubectl apply -f k8s/gpu-device-plugin.yaml
    
    # Install cert-manager (for TLS)
    echo "Installing cert-manager..."
    kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.13.0/cert-manager.yaml
    
    # Install metrics-server (for HPA)
    echo "Installing metrics-server..."
    kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
    
    # Install Prometheus Operator (optional)
    read -p "Install Prometheus Operator for monitoring? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
        helm repo update
        helm install kube-prometheus prometheus-community/kube-prometheus-stack \
            --namespace monitoring --create-namespace
        echo -e "${GREEN}✓ Prometheus Operator installed${NC}"
    fi
    
    echo ""
}

# Deploy using Helm
deploy_helm() {
    echo "🚀 Deploying YAPPC with Helm..."
    
    # Create namespace
    kubectl create namespace yappc --dry-run=client -o yaml | kubectl apply -f -
    
    # Deploy with Helm
    helm upgrade --install yappc helm/yappc \
        --namespace yappc \
        --create-namespace \
        --wait \
        --timeout 10m \
        --values helm/yappc/values.yaml
    
    echo -e "${GREEN}✓ YAPPC deployed successfully${NC}"
    echo ""
}

# Deploy using kubectl (alternative)
deploy_kubectl() {
    echo "🚀 Deploying YAPPC with kubectl..."
    
    # Apply manifests in order
    kubectl apply -f k8s/namespace.yaml
    kubectl apply -f k8s/configmap.yaml
    kubectl apply -f k8s/secrets.yaml
    kubectl apply -f k8s/persistent-volume.yaml
    kubectl apply -f k8s/ollama-deployment.yaml
    kubectl apply -f k8s/yappc-deployment.yaml
    kubectl apply -f k8s/ingress.yaml
    kubectl apply -f k8s/hpa.yaml
    
    # Optional: Monitoring
    read -p "Deploy monitoring (Prometheus/Grafana)? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        kubectl apply -f k8s/prometheus-monitoring.yaml
        kubectl apply -f k8s/grafana-dashboard.yaml
    fi
    
    # Optional: Logging
    read -p "Deploy logging (Fluent Bit)? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        kubectl apply -f k8s/fluent-bit.yaml
    fi
    
    echo -e "${GREEN}✓ YAPPC deployed successfully${NC}"
    echo ""
}

# Verify deployment
verify_deployment() {
    echo "🔍 Verifying deployment..."
    
    # Wait for rollout
    echo "Waiting for Ollama deployment..."
    kubectl rollout status deployment/ollama -n yappc --timeout=5m
    
    echo "Waiting for YAPPC deployment..."
    kubectl rollout status deployment/yappc-agents -n yappc --timeout=5m
    
    # Check pod status
    echo ""
    echo "Pod Status:"
    kubectl get pods -n yappc -o wide
    
    # Check services
    echo ""
    echo "Services:"
    kubectl get svc -n yappc
    
    # Check ingress
    echo ""
    echo "Ingress:"
    kubectl get ingress -n yappc
    
    echo ""
    echo -e "${GREEN}✓ Deployment verified${NC}"
    echo ""
}

# Get access URLs
get_urls() {
    echo "📍 Access URLs:"
    echo ""
    
    # Get Ingress URL
    INGRESS_IP=$(kubectl get ingress yappc-ingress -n yappc -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "pending")
    if [ "$INGRESS_IP" != "pending" ]; then
        echo -e "  YAPPC API: ${BLUE}https://yappc.ghatana.ai${NC}"
    else
        echo -e "  ${YELLOW}Ingress IP pending (waiting for load balancer)${NC}"
    fi
    
    # Port-forward instructions
    echo ""
    echo "For local access (port-forward):"
    echo -e "  ${BLUE}kubectl port-forward svc/yappc-service 8080:8080 -n yappc${NC}"
    echo -e "  Access: http://localhost:8080"
    echo ""
    
    # Monitoring URLs
    if kubectl get svc -n monitoring &> /dev/null; then
        echo "Monitoring:"
        echo -e "  Prometheus: ${BLUE}kubectl port-forward svc/kube-prometheus-prometheus 9090:9090 -n monitoring${NC}"
        echo -e "  Grafana: ${BLUE}kubectl port-forward svc/kube-prometheus-grafana 3000:80 -n monitoring${NC}"
        echo ""
    fi
}

# Main deployment flow
main() {
    check_prerequisites
    
    echo "Choose deployment method:"
    echo "  1) Helm (recommended)"
    echo "  2) kubectl (raw manifests)"
    read -p "Enter choice (1 or 2): " -n 1 -r
    echo ""
    
    case $REPLY in
        1)
            install_prerequisites
            deploy_helm
            ;;
        2)
            install_prerequisites
            deploy_kubectl
            ;;
        *)
            echo -e "${RED}Invalid choice${NC}"
            exit 1
            ;;
    esac
    
    verify_deployment
    get_urls
    
    echo "════════════════════════════════════════════════════════════════════════"
    echo -e "${GREEN}✅ YAPPC Deployment Complete!${NC}"
    echo "════════════════════════════════════════════════════════════════════════"
    echo ""
    echo "Next steps:"
    echo "  1. Configure DNS to point to Ingress IP"
    echo "  2. Wait for TLS certificate issuance (~2 min)"
    echo "  3. Access YAPPC at https://yappc.ghatana.ai"
    echo "  4. Monitor logs: kubectl logs -f -n yappc -l app=yappc-agents"
    echo "  5. Scale if needed: kubectl scale deployment yappc-agents --replicas=5 -n yappc"
    echo ""
}

# Run main
main "$@"
