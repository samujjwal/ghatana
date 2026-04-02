#!/bin/bash

##############################################################################
# Ghatana Swagger UI + API Services Startup Script
# 
# This script launches all Ghatana microservices with Swagger UI
# for comprehensive API documentation and testing.
#
# Usage:
#   ./start-swagger-ui.sh [command]
#
# Commands:
#   start    - Start all services (default)
#   stop     - Stop all services
#   logs     - Show logs from all services
#   clean    - Clean up containers and volumes
#   status   - Show service status
#   help     - Show this help message
##############################################################################

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCKER_COMPOSE_FILE="docker-compose.swagger.yml"
SERVICES=("swagger-ui" "tutorputor-api" "data-cloud-api" "yappc-api" "redis" "postgres")

# Helper functions
print_header() {
    echo -e "${BLUE}"
    echo "===================================================================================="
    echo "$1"
    echo "===================================================================================="
    echo -e "${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_info() {
    echo -e "${BLUE}ℹ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

show_help() {
    grep "^#" "$0" | tail -n +2 | head -n 20 | sed 's/^# //'
}

check_docker() {
    if ! command -v docker &> /dev/null; then
        print_error "Docker is not installed"
        exit 1
    fi
    
    if ! command -v docker-compose &> /dev/null; then
        print_error "Docker Compose is not installed"
        exit 1
    fi
    
    print_success "Docker and Docker Compose are available"
}

start_services() {
    print_header "Starting Ghatana Services with Swagger UI"
    
    check_docker
    
    if [ ! -f "$DOCKER_COMPOSE_FILE" ]; then
        print_error "docker-compose.swagger.yml not found in current directory"
        exit 1
    fi
    
    print_info "Starting services from $DOCKER_COMPOSE_FILE..."
    docker-compose -f "$DOCKER_COMPOSE_FILE" up -d
    
    print_info "Waiting for services to become healthy..."
    sleep 10
    
    # Check service health
    echo ""
    for service in "${SERVICES[@]}"; do
        if docker ps | grep -q "$service"; then
            print_success "$service is running"
        else
            print_warning "$service failed to start (may not have health check)"
        fi
    done
    
    echo ""
    print_header "🎉 All Services Started Successfully!"
    echo ""
    echo -e "${GREEN}API Documentation Hub:${NC}"
    echo "  → http://localhost:8888"
    echo ""
    echo -e "${GREEN}Individual API Swagger UIs:${NC}"
    echo "  → TutorPutor:   http://localhost:8080/api/v1/swagger-ui.html"
    echo "  → Data Cloud:   http://localhost:8081/api/v1/swagger-ui.html"
    echo "  → YAPPC:        http://localhost:8082/api/v1/swagger-ui.html"
    echo ""
    echo -e "${GREEN}OpenAPI Specifications:${NC}"
    echo "  → TutorPutor:   http://localhost:8080/api/v1/docs"
    echo "  → Data Cloud:   http://localhost:8081/api/v1/docs"
    echo "  → YAPPC:        http://localhost:8082/api/v1/docs"
    echo ""
    echo -e "${GREEN}Infrastructure Services:${NC}"
    echo "  → Redis:        localhost:6379"
    echo "  → PostgreSQL:   localhost:5432 (user: ghatana)"
    echo ""
    echo -e "${YELLOW}Note:${NC} It may take 30-60 seconds for all services to be fully ready"
    echo ""
}

stop_services() {
    print_header "Stopping Ghatana Services"
    
    if [ ! -f "$DOCKER_COMPOSE_FILE" ]; then
        print_error "docker-compose.swagger.yml not found"
        exit 1
    fi
    
    print_info "Stopping all services..."
    docker-compose -f "$DOCKER_COMPOSE_FILE" down
    
    print_success "All services stopped"
    echo ""
}

show_logs() {
    if [ ! -f "$DOCKER_COMPOSE_FILE" ]; then
        print_error "docker-compose.swagger.yml not found"
        exit 1
    fi
    
    if [ -z "$1" ]; then
        # Show logs from all services
        docker-compose -f "$DOCKER_COMPOSE_FILE" logs -f
    else
        # Show logs from specific service
        docker-compose -f "$DOCKER_COMPOSE_FILE" logs -f "$1"
    fi
}

show_status() {
    echo ""
    print_header "Service Status"
    
    if [ ! -f "$DOCKER_COMPOSE_FILE" ]; then
        print_error "docker-compose.swagger.yml not found"
        exit 1
    fi
    
    docker-compose -f "$DOCKER_COMPOSE_FILE" ps
    echo ""
}

clean_services() {
    print_header "Cleaning Up Services"
    
    read -p "This will remove all containers and volumes. Continue? (y/N) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        docker-compose -f "$DOCKER_COMPOSE_FILE" down -v
        print_success "Cleanup complete"
    else
        print_warning "Cleanup cancelled"
    fi
    echo ""
}

# Main script logic
COMMAND="${1:-start}"

case "$COMMAND" in
    start)
        start_services
        ;;
    stop)
        stop_services
        ;;
    logs)
        show_logs "$2"
        ;;
    status)
        show_status
        ;;
    clean)
        clean_services
        ;;
    help)
        show_help
        ;;
    *)
        print_error "Unknown command: $COMMAND"
        echo ""
        show_help
        exit 1
        ;;
esac
