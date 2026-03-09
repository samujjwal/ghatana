#!/bin/bash

# Ghatana Shared Infrastructure Management
# This script manages shared infrastructure services (postgres, redis, kafka, minio, grafana, prometheus, etc)
# that are used by all Ghatana products.

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
COMPOSE_FILE="$ROOT_DIR/docker-compose.yml"

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging functions
log_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

log_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

log_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

log_error() {
    echo -e "${RED}❌ $1${NC}"
}

# Check if Docker is running
check_docker() {
    if ! docker info > /dev/null 2>&1; then
        log_error "Docker is not running. Please start Docker and try again."
        exit 1
    fi
}

# Start infrastructure services
start_infrastructure() {
    log_info "Starting Ghatana shared infrastructure services..."
    
    check_docker
    
    # Start only infrastructure services (exclude product-specific services)
    docker compose -f "$COMPOSE_FILE" up -d \
        postgres \
        redis \
        zookeeper \
        kafka \
        kafka-ui \
        minio \
        rabbitmq \
        elasticsearch \
        prometheus \
        grafana \
        jaeger
    
    log_success "Infrastructure services started"
    log_info "Waiting for services to be healthy..."
    
    # Wait for critical services
    wait_for_service "postgres" "PostgreSQL"
    wait_for_service "redis" "Redis"
    wait_for_service "minio" "MinIO"
    
    log_success "All critical infrastructure services are ready!"
    echo ""
    show_service_urls
}

# Stop infrastructure services
stop_infrastructure() {
    log_info "Stopping Ghatana shared infrastructure services..."
    
    docker compose -f "$COMPOSE_FILE" stop \
        postgres \
        redis \
        zookeeper \
        kafka \
        kafka-ui \
        minio \
        rabbitmq \
        elasticsearch \
        prometheus \
        grafana \
        jaeger
    
    log_success "Infrastructure services stopped"
}

# Restart infrastructure services
restart_infrastructure() {
    log_info "Restarting Ghatana shared infrastructure services..."
    stop_infrastructure
    start_infrastructure
}

# Status of infrastructure services
status_infrastructure() {
    log_info "Checking status of Ghatana shared infrastructure services..."
    echo ""
    
    docker compose -f "$COMPOSE_FILE" ps \
        postgres \
        redis \
        zookeeper \
        kafka \
        kafka-ui \
        minio \
        rabbitmq \
        elasticsearch \
        prometheus \
        grafana \
        jaeger
}

# Wait for a service to be healthy
wait_for_service() {
    local service_name=$1
    local display_name=$2
    local max_attempts=60
    local attempt=0
    
    log_info "Waiting for $display_name to be ready..."
    
    while [ $attempt -lt $max_attempts ]; do
        health_status=$(docker inspect --format='{{.State.Health.Status}}' "ghatana-$service_name" 2>/dev/null || echo "no-health")
        
        if [ "$health_status" = "healthy" ]; then
            log_success "$display_name is ready!"
            return 0
        elif [ "$health_status" = "no-health" ]; then
            # Check if container is just running (no healthcheck defined)
            running_status=$(docker inspect --format='{{.State.Running}}' "ghatana-$service_name" 2>/dev/null || echo "false")
            if [ "$running_status" = "true" ]; then
                log_success "$display_name is running!"
                return 0
            fi
        fi
        
        sleep 1
        attempt=$((attempt + 1))
        printf "."
    done
    
    echo ""
    log_warning "$display_name failed to start within ${max_attempts} seconds"
    return 1
}

# Show service URLs
show_service_urls() {
    log_info "Service URLs:"
    echo ""
    echo "  📊 Grafana:        http://localhost:3000 (admin/admin)"
    echo "  📈 Prometheus:     http://localhost:9090"
    echo "  🔍 Jaeger:         http://localhost:16686"
    echo "  📦 MinIO Console:  http://localhost:9003 (minioadmin/minioadmin123)"
    echo "  🐰 RabbitMQ:       http://localhost:15673 (guest/guest)"
    echo "  📨 Kafka UI:       http://localhost:8084"
    echo "  🔎 Elasticsearch:  http://localhost:9200"
    echo ""
    echo "  💾 PostgreSQL:     localhost:5432 (ghatana/ghatana123)"
    echo "  🔴 Redis:          localhost:6379 (password: redis123)"
    echo "  📨 Kafka:          localhost:9092"
    echo ""
}

# Logs for a specific service
logs_infrastructure() {
    local service=$1
    
    if [ -z "$service" ]; then
        log_error "Please specify a service name"
        echo "Available services: postgres, redis, kafka, minio, grafana, prometheus, rabbitmq, elasticsearch, jaeger"
        exit 1
    fi
    
    docker compose -f "$COMPOSE_FILE" logs -f "$service"
}

# Clean up infrastructure (remove volumes)
clean_infrastructure() {
    log_warning "This will remove all data volumes. Are you sure? (yes/no)"
    read -r confirmation
    
    if [ "$confirmation" != "yes" ]; then
        log_info "Cleanup cancelled"
        exit 0
    fi
    
    log_info "Stopping and removing infrastructure services and volumes..."
    
    docker compose -f "$COMPOSE_FILE" down -v \
        postgres \
        redis \
        zookeeper \
        kafka \
        kafka-ui \
        minio \
        rabbitmq \
        elasticsearch \
        prometheus \
        grafana \
        jaeger
    
    log_success "Infrastructure cleaned up"
}

# Show usage
usage() {
    echo "Ghatana Shared Infrastructure Management"
    echo ""
    echo "Usage: $0 {start|stop|restart|status|logs|clean|urls}"
    echo ""
    echo "Commands:"
    echo "  start      Start all shared infrastructure services"
    echo "  stop       Stop all shared infrastructure services"
    echo "  restart    Restart all shared infrastructure services"
    echo "  status     Show status of all infrastructure services"
    echo "  logs       Show logs for a specific service (e.g., ./infrastructure.sh logs redis)"
    echo "  clean      Stop and remove all infrastructure services and volumes"
    echo "  urls       Show service URLs and credentials"
    echo ""
}

# Main script logic
case "${1:-}" in
    start)
        start_infrastructure
        ;;
    stop)
        stop_infrastructure
        ;;
    restart)
        restart_infrastructure
        ;;
    status)
        status_infrastructure
        ;;
    logs)
        logs_infrastructure "${2:-}"
        ;;
    clean)
        clean_infrastructure
        ;;
    urls)
        show_service_urls
        ;;
    *)
        usage
        exit 1
        ;;
esac
