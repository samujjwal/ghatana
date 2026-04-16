#!/bin/bash
# YAPPC Deployment Validation Script
# Validates that all services are healthy and accessible

set -e

echo "=========================================="
echo "YAPPC Deployment Validation"
echo "=========================================="
echo ""

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to check service health
check_service() {
    local name=$1
    local url=$2
    local timeout=${3:-5}

    echo -n "Checking $name... "
    
    if curl -sf --max-time "$timeout" "$url" > /dev/null 2>&1; then
        echo -e "${GREEN}OK${NC}"
        return 0
    else
        echo -e "${RED}FAILED${NC}"
        return 1
    fi
}

# Function to check Kubernetes deployment
check_k8s_deployment() {
    local namespace=$1
    local deployment=$2

    echo -n "Checking Kubernetes deployment $deployment... "
    
    if kubectl get deployment "$deployment" -n "$namespace" > /dev/null 2>&1; then
        local ready=$(kubectl get deployment "$deployment" -n "$namespace" -o jsonpath='{.status.readyReplicas}')
        local desired=$(kubectl get deployment "$deployment" -n "$namespace" -o jsonpath='{.spec.replicas}')
        
        if [ "$ready" = "$desired" ]; then
            echo -e "${GREEN}OK${NC} ($ready/$desired ready)"
            return 0
        else
            echo -e "${YELLOW}NOT READY${NC} ($ready/$desired ready)"
            return 1
        fi
    else
        echo -e "${RED}NOT FOUND${NC}"
        return 1
    fi
}

# Check if running in Docker Compose or Kubernetes
if command -v kubectl &> /dev/null && kubectl cluster-info &> /dev/null; then
    echo "Detected Kubernetes deployment"
    echo ""

    # Check Kubernetes deployments
    check_k8s_deployment yappc yappc-backend
    check_k8s_deployment yappc yappc-web
    check_k8s_deployment yappc postgres
    check_k8s_deployment yappc redis

    echo ""
    echo "Kubernetes Services:"
    kubectl get svc -n yappc

else
    echo "Detected Docker Compose deployment"
    echo ""

    # Check Docker Compose services
    check_service "PostgreSQL" "http://localhost:5432" 2 || echo "  (PostgreSQL check may fail - this is expected)"
    check_service "Redis" "http://localhost:6379" 2 || echo "  (Redis check may fail - this is expected)"
    check_service "YAPPC Backend" "http://localhost:8082/health"
    check_service "AI Requirements" "http://localhost:8081/health"
    check_service "Lifecycle API" "http://localhost:8082/health"
    check_service "YAPPC Web" "http://localhost:5173"

    echo ""
    echo "Docker Containers:"
    docker compose -f deployment/docker/docker-compose-simple.yml ps 2>/dev/null || echo "Docker Compose not running from deployment/docker/"
fi

echo ""
echo "=========================================="
echo "Validation Complete"
echo "=========================================="
