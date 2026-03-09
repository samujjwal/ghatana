#!/usr/bin/env bash

################################################################################
# Ghatana Shared Infrastructure Verification Script
#
# Verifies that all shared infrastructure containers are running and healthy
# Usage: ./scripts/verify-shared-infrastructure.sh
################################################################################

set +e  # Don't exit on errors

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}Ghatana Shared Infrastructure Verification${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}\n"

# Check Docker
if ! command -v docker &> /dev/null; then
    echo -e "${RED}✗${NC} Docker is not installed"
    exit 1
fi
echo -e "${GREEN}✓${NC} Docker is available"

# Check Docker daemon
if ! docker ps &>/dev/null; then
    echo -e "${RED}✗${NC} Docker daemon is not running"
    exit 1
fi
echo -e "${GREEN}✓${NC} Docker daemon is running"

echo ""
echo -e "${BLUE}Container Status:${NC}"
echo "─────────────────────────────────────────────────────────────"

# Check each service
SERVICES=("ghatana-postgres" "ghatana-redis" "ghatana-prometheus" "ghatana-grafana" "ghatana-jaeger")

for service in "${SERVICES[@]}"; do
    if docker ps --filter "name=$service" --format "{{.Names}}" | grep -q "$service"; then
        status=$(docker inspect "$service" --format='{{.State.Status}}' 2>/dev/null)
        health=$(docker inspect "$service" --format='{{.State.Health.Status}}' 2>/dev/null)
        
        if [ "$status" = "running" ]; then
            if [ "$health" = "healthy" ]; then
                echo -e "${GREEN}✓${NC} $service (running, healthy)"
            elif [ -z "$health" ]; then
                echo -e "${GREEN}✓${NC} $service (running)"
            else
                echo -e "${YELLOW}⚠${NC} $service (running, $health)"
            fi
        else
            echo -e "${RED}✗${NC} $service ($status)"
        fi
    else
        echo -e "${RED}✗${NC} $service (not found)"
    fi
done

echo ""
echo -e "${BLUE}Port Availability:${NC}"
echo "─────────────────────────────────────────────────────────────"

# Check ports
PORTS=("5432:PostgreSQL" "6379:Redis" "9090:Prometheus" "3000:Grafana" "16686:Jaeger" "6831:Jaeger-Agent")

for port_info in "${PORTS[@]}"; do
    port=$(echo "$port_info" | cut -d: -f1)
    name=$(echo "$port_info" | cut -d: -f2)
    
    if nc -z localhost "$port" 2>/dev/null; then
        echo -e "${GREEN}✓${NC} $name (localhost:$port)"
    else
        echo -e "${RED}✗${NC} $name (localhost:$port) - not accessible"
    fi
done

echo ""
echo -e "${BLUE}Database Connectivity:${NC}"
echo "─────────────────────────────────────────────────────────────"

# Test database connection via docker exec
if docker exec ghatana-postgres psql -U ghatana -d ghatana -c "SELECT 1" &>/dev/null 2>&1; then
    echo -e "${GREEN}✓${NC} PostgreSQL is accessible (via docker)"
else
    echo -e "${RED}✗${NC} PostgreSQL is not accessible"
fi

# Test Redis
if docker exec ghatana-redis redis-cli -a redis123 ping &>/dev/null 2>&1; then
    echo -e "${GREEN}✓${NC} Redis is accessible (via docker)"
else
    echo -e "${RED}✗${NC} Redis is not accessible"
fi

echo ""
echo -e "${BLUE}Volume Status:${NC}"
echo "─────────────────────────────────────────────────────────────"

# List volumes
VOLUMES=("ghatana_postgres_data" "ghatana_redis_data" "ghatana_prometheus_data" "ghatana_grafana_data" "ghatana_jaeger_data")

for volume in "${VOLUMES[@]}"; do
    if docker volume ls --filter "name=$volume" --format "{{.Name}}" | grep -q "$volume"; then
        echo -e "${GREEN}✓${NC} $volume exists"
    else
        echo -e "${YELLOW}⚠${NC} $volume does not exist (may be created on first use)"
    fi
done

echo ""
echo -e "${BLUE}Network Status:${NC}"
echo "─────────────────────────────────────────────────────────────"

if docker network inspect ghatana_ghatana-network &>/dev/null 2>&1; then
    echo -e "${GREEN}✓${NC} ghatana_ghatana-network exists"
    container_count=$(docker network inspect ghatana_ghatana-network --format '{{len .Containers}}')
    echo "  Connected containers: $container_count"
else
    echo -e "${RED}✗${NC} ghatana_ghatana-network does not exist"
fi

echo ""
echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}Verification Complete${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"
