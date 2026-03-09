#!/usr/bin/env bash
# DCMaar Distributed Tracing Setup
# Configures OpenTelemetry and Jaeger for the demo environment

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")/.."
CONFIG_DIR="${CONFIG_DIR:-$ROOT_DIR/configs}"
TRACING_NAMESPACE="dcmaar-tracing"

# Check prerequisites
check_prerequisites() {
    log "INFO" "🔍 Checking prerequisites..."
    
    local commands=("docker" "docker-compose" "kubectl" "helm")
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
    
    log "INFO" "✅ All prerequisites are met"
}

# Setup Jaeger with OpenTelemetry Collector
setup_jaeger() {
    log "INFO" "🚀 Setting up Jaeger with OpenTelemetry Collector..."
    
    # Create directory for Jaeger configuration
    local jaeger_dir="$CONFIG_DIR/jaeger"
    mkdir -p "$jaeger_dir"
    
    # Create docker-compose file for Jaeger
    cat > "$jaeger_dir/docker-compose.yml" << 'EOF'
version: '3.8'
services:
  # Jaeger
  jaeger:
    image: jaegertracing/all-in-one:latest
    container_name: jaeger
    ports:
      - "16686:16686"  # UI
      - "14268:14268"  # HTTP collector
      - "14250:14250"  # Model.proto
    environment:
      - COLLECTOR_OTLP_ENABLED=true
    networks:
      - tracing

  # OpenTelemetry Collector
  otel-collector:
    image: otel/opentelemetry-collector:latest
    container_name: otel-collector
    command: ["--config=/etc/otel-collector-config.yaml"]
    volumes:
      - ./otel-collector-config.yaml:/etc/otel-collector-config.yaml
    ports:
      - "4317:4317"   # OTLP gRPC
      - "4318:4318"   # OTLP HTTP
      - "8888:8888"   # Metrics
    depends_on:
      - jaeger
    networks:
      - tracing

networks:
  tracing:
    driver: bridge
EOF

    # Create OpenTelemetry Collector config
    cat > "$jaeger_dir/otel-collector-config.yaml" << 'EOF'
receivers:
  otlp:
    protocols:
      grpc:
      http:

processors:
  batch:
    timeout: 1s
    send_batch_size: 1024

  memory_limiter:
    check_interval: 1s
    limit_mib: 1800
    spike_limit_mib: 512

extensions:
  health_check:
  pprof:
    endpoint: 0.0.0.0:1777
  zpages:
    endpoint: 0.0.0.0:55679

exporters:
  jaeger:
    endpoint: "jaeger:14250"
    tls:
      insecure: true
  logging:
    loglevel: debug

service:
  extensions: [health_check, pprof, zpages]
  pipelines:
    traces:
      receivers: [otlp]
      processors: [memory_limiter, batch]
      exporters: [jaeger, logging]
    metrics:
      receivers: [otlp]
      processors: [memory_limiter, batch]
      exporters: [logging]
    logs:
      receivers: [otlp]
      processors: [memory_limiter, batch]
      exporters: [logging]
EOF

    log "INFO" "✅ Jaeger and OpenTelemetry Collector configuration created"
}

# Start tracing services
start_tracing() {
    log "INFO" "🚀 Starting tracing services..."
    
    local jaeger_dir="$CONFIG_DIR/jaeger"
    
    if [ ! -f "$jaeger_dir/docker-compose.yml" ]; then
        log "ERROR" "❌ Jaeger configuration not found. Run setup first."
        return 1
    fi
    
    (cd "$jaeger_dir" && docker-compose up -d)
    
    log "INFO" "✅ Tracing services started"
    log "INFO" "🌐 Jaeger UI: http://localhost:16686"
    log "INFO" "📊 OpenTelemetry Collector metrics: http://localhost:8888/metrics"
}

# Stop tracing services
stop_tracing() {
    log "INFO" "🛑 Stopping tracing services..."
    
    local jaeger_dir="$CONFIG_DIR/jaeger"
    
    if [ -f "$jaeger_dir/docker-compose.yml" ]; then
        (cd "$jaeger_dir" && docker-compose down)
        log "INFO" "✅ Tracing services stopped"
    else
        log "WARN" "❌ Jaeger configuration not found. Nothing to stop."
    fi
}

# Clean up tracing resources
clean_tracing() {
    log "INFO" "🧹 Cleaning up tracing resources..."
    
    stop_tracing
    
    local jaeger_dir="$CONFIG_DIR/jaeger"
    if [ -d "$jaeger_dir" ]; then
        rm -rf "$jaeger_dir"
        log "INFO" "✅ Removed tracing configuration"
    fi
    
    log "INFO" "✅ Tracing resources cleaned up"
}

# Check if tracing is running
check_tracing_status() {
    log "INFO" "🔍 Checking tracing services status..."
    
    local jaeger_running
    local otel_running
    
    jaeger_running=$(docker ps -f "name=jaeger" --format '{{.Names}}' | wc -l)
    otel_running=$(docker ps -f "name=otel-collector" --format '{{.Names}}' | wc -l)
    
    if [ "$jaeger_running" -gt 0 ] && [ "$otel_running" -gt 0 ]; then
        log "INFO" "✅ All tracing services are running"
        return 0
    else
        log "WARN" "⚠️  Some tracing services are not running"
        [ "$jaeger_running" -eq 0 ] && log "WARN" "   - Jaeger is not running"
        [ "$otel_running" -eq 0 ] && log "WARN" "   - OpenTelemetry Collector is not running"
        return 1
    fi
}

# Configure application for tracing
configure_application() {
    log "INFO" "⚙️  Configuring application for tracing..."
    
    local app_config="$CONFIG_DIR/demo/application.yaml"
    
    if [ ! -f "$app_config" ]; then
        log "WARN" "❌ Application config not found at $app_config"
        return 1
    fi
    
    # Backup original config
    cp "$app_config" "${app_config}.bak"
    
    # Add/update tracing configuration
    if ! grep -q "opentelemetry:" "$app_config"; then
        cat >> "$app_config" << 'EOF'

# OpenTelemetry Configuration
opentelemetry:
  enabled: true
  serviceName: "dcmaar-demo"
  exporter:
    otlp:
      endpoint: "http://localhost:4317"
      protocol: grpc
  resource:
    attributes:
      - key: "service.namespace"
        value: "demo"
      - key: "deployment.environment"
        value: "demo"
  sampler:
    type: parentbased_always_on
    argument: 1.0
EOF
        log "INFO" "✅ Added OpenTelemetry configuration to $app_config"
    else
        log "INFO" "ℹ️  OpenTelemetry configuration already exists in $app_config"
    fi
    
    log "INFO" "🔁 Restarting application to apply changes..."
    docker-compose -f docker-compose.demo.yml restart
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
        setup)
            check_prerequisites
            setup_jaeger
            ;;
        start)
            start_tracing
            ;;
        stop)
            stop_tracing
            ;;
        status)
            check_tracing_status
            ;;
        configure-app)
            configure_application
            ;;
        clean)
            clean_tracing
            ;;
        help|*)
            echo "Usage: $0 {setup|start|stop|status|configure-app|clean|help}"
            echo "  setup          - Set up tracing infrastructure"
            echo "  start          - Start tracing services"
            echo "  stop           - Stop tracing services"
            echo "  status         - Check status of tracing services"
            echo "  configure-app  - Configure application for tracing"
            echo "  clean          - Remove all tracing resources"
            echo "  help           - Show this help message"
            exit 1
            ;;
    esac
}

# Run the main function
main "$@"

exit 0
