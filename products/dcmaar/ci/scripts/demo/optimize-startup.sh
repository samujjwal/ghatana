#!/usr/bin/env bash
# DCMaar Container Optimization Script
# Analyzes and optimizes container startup times for the demo environment

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")/.."
REPORTS_DIR="${REPORTS_DIR:-$ROOT_DIR/reports/optimization}"
DOCKER_COMPOSE_FILE="${DOCKER_COMPOSE_FILE:-docker-compose.demo.yml}"

# Check prerequisites
check_prerequisites() {
    log "INFO" "🔍 Checking prerequisites..."
    
    local commands=("docker" "docker-compose" "jq" "bc")
    for cmd in "${commands[@]}"; do
        if ! command -v "$cmd" &> /dev/null; then
            log "ERROR" "❌ Required command '$cmd' is not installed."
            return 1
        fi
    done
    
    if ! docker info &> /dev/null; then
        log "ERROR" "❌ Docker daemon is not running."
        return 1
    fi
    
    if [ ! -f "$DOCKER_COMPOSE_FILE" ]; then
        log "ERROR" "❌ Docker Compose file not found: $DOCKER_COMPOSE_FILE"
        return 1
    fi
    
    log "INFO" "✅ All prerequisites are met"
}

# Analyze container startup times
analyze_startup_times() {
    log "INFO" "⏱️  Analyzing container startup times..."
    
    mkdir -p "$REPORTS_DIR"
    local report_file="$REPORTS_DIR/startup-analysis-$(date +%Y%m%d-%H%M%S).md"
    
    # Get list of services from docker-compose
    local services
    services=$(docker-compose -f "$DOCKER_COMPOSE_FILE" config --services)
    
    # Create markdown report
    echo "# Container Startup Time Analysis" > "$report_file"
    echo "Generated: $(date)" >> "$report_file"
    echo "" >> "$report_file"
    echo "## Startup Metrics" >> "$report_file"
    echo "" >> "$report_file"
    echo "| Service | Image | Start Time (s) | CPU % | Memory (MB) | Status |" >> "$report_file"
    echo "|---------|-------|----------------|-------|-------------|--------|" >> "$report_file"
    
    local total_start_time=0
    local service_count=0
    
    # Check if containers are running
    if ! docker-compose -f "$DOCKER_COMPOSE_FILE" ps | grep -q "Up"; then
        log "WARN" "⚠️  No running containers found. Starting services first..."
        docker-compose -f "$DOCKER_COMPOSE_FILE" up -d
        sleep 10  # Give containers time to start
    fi
    
    # Analyze each service
    for service in $services; do
        log "INFO" "📊 Analyzing $service..."
        
        # Get container ID
        local container_id
        container_id=$(docker-compose -f "$DOCKER_COMPOSE_FILE" ps -q "$service")
        
        if [ -z "$container_id" ]; then
            log "WARN" "❌ Container for service $service not found"
            echo "| $service | - | - | - | - | ❌ Not running |" >> "$report_file"
            continue
        fi
        
        # Get container info
        local container_info
        container_info=$(docker inspect "$container_id")
        
        # Get image name
        local image
        image=$(echo "$container_info" | jq -r '.[0].Config.Image')
        
        # Get startup time
        local started_at
        local finished_at
        local start_time=0
        
        started_at=$(echo "$container_info" | jq -r '.[0].State.StartedAt')
        finished_at=$(echo "$container_info" | jq -r '.[0].State.FinishedAt')
        
        if [ "$started_at" != "0001-01-01T00:00:00Z" ] && [ "$finished_at" != "0001-01-01T00:00:00Z" ]; then
            local start_ts finish_ts
            start_ts=$(date -d "$started_at" +%s.%N)
            finish_ts=$(date -d "$finished_at" +%s.%N)
            start_time=$(echo "$finish_ts - $start_ts" | bc)
            total_start_time=$(echo "$total_start_time + $start_time" | bc)
            ((service_count++))
        fi
        
        # Get resource usage
        local cpu_usage=0
        local mem_usage=0
        
        if docker stats --no-stream "$container_id" &>/dev/null; then
            local stats
            stats=$(docker stats --no-stream --format '{{.CPUPerc}} {{.MemUsage}}' "$container_id" 2>/dev/null || true)
            
            if [ -n "$stats" ]; then
                # Extract CPU percentage (remove %)
                cpu_usage=$(echo "$stats" | awk '{print $1}' | tr -d '%')
                
                # Extract memory usage (convert to MB)
                local mem_str
                mem_str=$(echo "$stats" | awk '{print $2}')
                if [[ "$mem_str" == *"MiB"* ]]; then
                    mem_usage=$(echo "$mem_str" | sed 's/MiB//')
                elif [[ "$mem_str" == *"GiB"* ]]; then
                    local gb_mem
                    gb_mem=$(echo "$mem_str" | sed 's/GiB//')
                    mem_usage=$(echo "$gb_mem * 1024" | bc | awk '{printf "%.2f", $1}')
                fi
            fi
        fi
        
        # Get container status
        local status
        status=$(echo "$container_info" | jq -r '.[0].State.Status')
        local status_emoji="✅"
        
        if [ "$status" != "running" ]; then
            status_emoji="❌"
        fi
        
        # Add to report
        echo "| $service | $image | $start_time | $cpu_usage% | $mem_usage | $status_emoji $status |" >> "$report_file"
    done
    
    # Calculate average startup time
    local avg_start_time=0
    if [ $service_count -gt 0 ]; then
        avg_start_time=$(echo "scale=2; $total_start_time / $service_count" | bc)
    fi
    
    # Add summary to report
    echo "" >> "$report_file"
    echo "## Summary" >> "$report_file"
    echo "" >> "$report_file"
    echo "- **Total Services Analyzed**: $service_count" >> "$report_file"
    echo "- **Total Startup Time**: ${total_start_time}s" >> "$report_file"
    echo "- **Average Startup Time**: ${avg_start_time}s" >> "$report_file"
    
    # Add optimization recommendations
    echo "" >> "$report_file"
    echo "## Optimization Recommendations" >> "$report_file"
    echo "" >> "$report_file"
    generate_optimization_recommendations "$report_file"
    
    log "INFO" "✅ Analysis complete. Report saved to $report_file"
    
    # Display summary
    echo ""
    echo "📊 Startup Analysis Summary:"
    echo "   Total Services: $service_count"
    echo "   Total Startup Time: ${total_start_time}s"
    echo "   Average Startup Time: ${avg_start_time}s"
    echo ""
    echo "View full report: $report_file"
}

# Generate optimization recommendations
generate_optimization_recommendations() {
    local report_file="$1"
    
    cat >> "$report_file" << 'EOF'
### 1. Optimize Docker Images
- Use multi-stage builds to reduce image size
- Remove unnecessary files and dependencies
- Use `.dockerignore` to exclude unnecessary files
- Use smaller base images (e.g., `alpine` variants)

### 2. Optimize Container Startup
- Set proper `HEALTHCHECK` in Dockerfile
- Use `depends_on` with healthchecks in docker-compose
- Set appropriate `restart` policies
- Use `init: true` for better signal handling

### 3. Resource Management
- Set appropriate CPU and memory limits
- Use `deploy.resources` in docker-compose v3+
- Monitor and adjust based on usage patterns

### 4. Network Optimization
- Use custom bridge networks
- Configure DNS settings if needed
- Consider using `network_mode: host` for performance-critical services

### 5. Volume Optimization
- Use named volumes for persistent data
- Consider `cached` or `delegated` modes for bind mounts on macOS/Windows
- Avoid using host volumes in production

### 6. Application-Level Optimization
- Implement lazy loading where possible
- Optimize application startup code
- Use connection pooling for databases
- Cache expensive operations

### 7. Monitoring and Logging
- Implement distributed tracing
- Set up proper logging with log rotation
- Monitor container metrics

### 8. Build Cache Optimization
- Order Dockerfile commands from least to most frequently changing
- Leverage build cache with `--cache-from`
- Use BuildKit for faster builds

### 9. Dependencies
- Keep dependencies up to date
- Remove unused dependencies
- Use dependency locking

### 10. CI/CD Pipeline
- Implement parallel builds where possible
- Cache build dependencies
- Use buildx for multi-architecture builds
EOF
}

# Optimize docker-compose file
optimize_docker_compose() {
    log "INFO" "⚡ Optimizing docker-compose configuration..."
    
    local optimized_file="${DOCKER_COMPOSE_FILE%.*}-optimized.yml"
    
    # Create optimized version with common optimizations
    cat > "$optimized_file" << 'EOF'
# Optimized Docker Compose Configuration
# Generated: $(date)
# Original: $DOCKER_COMPOSE_FILE

version: '3.8'

x-common-optimizations: &common-optimizations
  init: true
  stop_grace_period: 10s
  restart: unless-stopped
  healthcheck:
    test: ["CMD", "curl", "-f", "http://localhost:8080/health"]
    interval: 30s
    timeout: 10s
    retries: 3
    start_period: 20s
  deploy:
    resources:
      limits:
        cpus: '1'
        memory: 1G
      reservations:
        cpus: '0.5'
        memory: 512M

services:
EOF

    # Process each service
    local services
    services=$(docker-compose -f "$DOCKER_COMPOSE_FILE" config --services)
    
    for service in $services; do
        log "INFO" "🔧 Optimizing service: $service"
        
        # Get original service config
        local service_config
        service_config=$(docker-compose -f "$DOCKER_COMPOSE_FILE" config | yq eval ".services.$service" -)
        
        # Add optimized config
        echo "  $service:" >> "$optimized_file"
        echo "    <<: *common-optimizations" >> "$optimized_file"
        
        # Add service-specific optimizations
        if [[ "$service" == *"db"* || "$service" == *"postgres"* || "$service" == *"mysql"* ]]; then
            echo "    # Database optimizations" >> "$optimized_file"
            echo "    shm_size: '1gb'" >> "$optimized_file"
            echo "    environment:" >> "$optimized_file"
            echo "      - POSTGRES_POOL_SIZE=20" >> "$optimized_file"
            echo "      - POSTGRES_MAX_CONNECTIONS=100" >> "$optimized_file"
        elif [[ "$service" == *"api"* || "$service" == *"app"* ]]; then
            echo "    # Application optimizations" >> "$optimized_file"
            echo "    environment:" >> "$optimized_file"
            echo "      - NODE_ENV=production" >> "$optimized_file"
            echo "      - UV_THREADPOOL_SIZE=16" >> "$optimized_file"
        fi
        
        # Add original config (will be overridden by our optimizations)
        echo "$service_config" | yq eval 'del(.restart, .healthcheck, .deploy)' - >> "$optimized_file"
        echo "" >> "$optimized_file"
    done
    
    log "INFO" "✅ Optimized docker-compose file created: $optimized_file"
    log "INFO" "Review the file and apply with: docker-compose -f $optimized_file up -d"
}

# Logging function
log() {
    local level=$1
    local message=$2
    local timestamp
    timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    echo -e "[${timestamp}] [${level}] ${message}"
}

# Main function
main() {
    local command=${1:-help}
    
    case $command in
        analyze)
            check_prerequisites
            analyze_startup_times
            ;;
        optimize)
            check_prerequisites
            optimize_docker_compose
            ;;
        all)
            check_prerequisites
            analyze_startup_times
            optimize_docker_compose
            ;;
        help|*)
            echo "Usage: $0 {analyze|optimize|all|help}"
            echo "  analyze   - Analyze container startup times and generate report"
            echo "  optimize  - Generate optimized docker-compose configuration"
            echo "  all       - Run both analysis and optimization"
            echo "  help      - Show this help message"
            exit 1
            ;;
    esac
}

# Check if yq is installed
if ! command -v yq &> /dev/null; then
    echo "Error: yq is required but not installed. Please install it first."
    echo "On macOS: brew install yq"
    echo "On Linux: sudo apt-get install yq"
    exit 1
fi

# Run the main function
main "$@"

exit 0
