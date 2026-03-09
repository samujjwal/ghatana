#!/bin/bash

# Production Deployment Script for TutorPutor
# This script handles the complete deployment process for production environment

set -euo pipefail

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
LOG_FILE="$PROJECT_ROOT/logs/deploy-$(date +%Y%m%d-%H%M%S).log"
ENVIRONMENT="production"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging function
log() {
    echo -e "${NC}[$(date '+%Y-%m-%d %H:%M:%S')] $1" | tee -a "$LOG_FILE"
}

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1" | tee -a "$LOG_FILE"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1" | tee -a "$LOG_FILE"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1" | tee -a "$LOG_FILE"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1" | tee -a "$LOG_FILE"
}

# Check prerequisites
check_prerequisites() {
    log_info "Checking prerequisites..."
    
    # Check if required tools are installed
    local tools=("node" "npm" "docker" "docker-compose" "psql" "redis-cli")
    for tool in "${tools[@]}"; do
        if ! command -v "$tool" &> /dev/null; then
            log_error "Required tool '$tool' is not installed"
            exit 1
        fi
    done
    
    # Check environment variables
    if [[ ! -f "$PROJECT_ROOT/.env.production" ]]; then
        log_error "Production environment file not found: .env.production"
        exit 1
    fi
    
    # Check database connection
    if ! psql "$DATABASE_URL" -c "SELECT 1;" &> /dev/null; then
        log_error "Cannot connect to PostgreSQL database"
        exit 1
    fi
    
    # Check Redis connection
    if ! redis-cli ping &> /dev/null; then
        log_error "Cannot connect to Redis"
        exit 1
    fi
    
    log_success "Prerequisites check passed"
}

# Backup current deployment
backup_current_deployment() {
    log_info "Creating backup of current deployment..."
    
    local backup_dir="$PROJECT_ROOT/backups/production-$(date +%Y%m%d-%H%M%S)"
    mkdir -p "$backup_dir"
    
    # Backup database
    log_info "Backing up database..."
    pg_dump "$DATABASE_URL" > "$backup_dir/database.sql"
    
    # Backup Redis
    log_info "Backing up Redis..."
    redis-cli --rdb > "$backup_dir/redis.rdb"
    
    # Backup application files
    log_info "Backing up application files..."
    cp -r "$PROJECT_ROOT/build" "$backup_dir/" 2>/dev/null || true
    
    log_success "Backup completed: $backup_dir"
}

# Build application
build_application() {
    log_info "Building application..."
    
    cd "$PROJECT_ROOT"
    
    # Clean previous build
    log_info "Cleaning previous build..."
    npm run clean
    
    # Install dependencies
    log_info "Installing dependencies..."
    npm ci --production
    
    # Build application
    log_info "Building application..."
    npm run build:production
    
    # Run tests
    log_info "Running tests..."
    npm run test:production
    
    log_success "Application built successfully"
}

# Deploy application
deploy_application() {
    log_info "Deploying application..."
    
    cd "$PROJECT_ROOT"
    
    # Load production environment variables
    source .env.production
    
    # Stop existing services
    log_info "Stopping existing services..."
    docker-compose -f docker-compose.prod.yml down || true
    
    # Build and start services
    log_info "Starting production services..."
    docker-compose -f docker-compose.prod.yml build
    docker-compose -f docker-compose.prod.yml up -d
    
    # Wait for services to be healthy
    log_info "Waiting for services to be healthy..."
    local max_attempts=30
    local attempt=0
    
    while [[ $attempt -lt $max_attempts ]]; do
        if curl -f http://localhost:3000/health &> /dev/null; then
            log_success "Application is healthy"
            break
        fi
        
        attempt=$((attempt + 1))
        log_info "Waiting for application to be healthy... (attempt $attempt/$max_attempts)"
        sleep 10
    done
    
    if [[ $attempt -eq $max_attempts ]]; then
        log_error "Application failed to become healthy within timeout"
        exit 1
    fi
}

# Run database migrations
run_migrations() {
    log_info "Running database migrations..."
    
    cd "$PROJECT_ROOT"
    
    # Load environment variables
    source .env.production
    
    # Run Prisma migrations
    npx prisma migrate deploy
    
    # Run seed data if needed
    if [[ "${SEED_DATA:-false}" == "true" ]]; then
        log_info "Seeding database..."
        npx prisma db seed
    fi
    
    log_success "Database migrations completed"
}

# Configure monitoring
setup_monitoring() {
    log_info "Setting up monitoring..."
    
    cd "$PROJECT_ROOT"
    
    # Create monitoring directories
    mkdir -p logs/monitoring
    mkdir -p metrics
    
    # Set up log rotation
    log_info "Setting up log rotation..."
    sudo tee /etc/logrotate.d/tutorputor > /dev/null <<EOF
$PROJECT_ROOT/logs/*.log {
    daily
    missingok
    rotate 30
    compress
    delaycompress
    notifempty
    create 644
    postrotate
        systemctl reload rsyslog
    endscript
}
EOF
    
    # Start monitoring services
    log_info "Starting monitoring services..."
    docker-compose -f docker-compose.monitoring.yml up -d
    
    log_success "Monitoring setup completed"
}

# Run health checks
run_health_checks() {
    log_info "Running health checks..."
    
    local health_checks=(
        "Application Health:curl -f http://localhost:3000/health"
        "Database Health:psql $DATABASE_URL -c 'SELECT 1;'"
        "Redis Health:redis-cli ping"
        "WebSocket Health:curl -i -N -H 'Connection: Upgrade' -H 'Upgrade: websocket' ws://localhost:3000/ws"
    )
    
    for check in "${health_checks[@]}"; do
        local name="${check%%:*}"
        local command="${check#*:}"
        
        log_info "Checking $name..."
        if eval "$command" &> /dev/null; then
            log_success "$name: OK"
        else
            log_error "$name: FAILED"
            exit 1
        fi
    done
    
    log_success "All health checks passed"
}

# Deploy static assets to CDN
deploy_static_assets() {
    log_info "Deploying static assets to CDN..."
    
    cd "$PROJECT_ROOT"
    
    # Check if CDN configuration exists
    if [[ ! -f "$PROJECT_ROOT/.env.cdn" ]]; then
        log_warning "CDN configuration not found, skipping static asset deployment"
        return
    fi
    
    # Load CDN configuration
    source .env.cdn
    
    # Deploy to CDN (example with AWS S3)
    if [[ "${CDN_PROVIDER:-}" == "s3" ]]; then
        log_info "Deploying to AWS S3 CDN..."
        
        # Sync static files to S3
        aws s3 sync "$PROJECT_ROOT/build/static" "s3://$CDN_BUCKET/static/" --delete
        
        # Invalidate CDN cache
        aws cloudfront create-invalidation --distribution "$CDN_DISTRIBUTION_ID" --paths "/static/*"
        
        log_success "Static assets deployed to CDN"
    fi
}

# Send deployment notification
send_notification() {
    log_info "Sending deployment notification..."
    
    # Check if notification configuration exists
    if [[ ! -f "$PROJECT_ROOT/.env.notifications" ]]; then
        log_warning "Notification configuration not found, skipping notification"
        return
    fi
    
    # Load notification configuration
    source .env.notifications
    
    # Send Slack notification
    if [[ -n "${SLACK_WEBHOOK_URL:-}" ]]; then
        local message="✅ TutorPutor Production Deployment Successful\n\n*Environment:* $ENVIRONMENT\n*Version:* $(git rev-parse --short HEAD)\n*Time:* $(date)\n*Deployer:* ${USER:-unknown}"
        
        curl -X POST -H 'Content-type: application/json' \
            --data "{\"text\":\"$message\"}" \
            "$SLACK_WEBHOOK_URL" &> /dev/null || true
        
        log_success "Slack notification sent"
    fi
    
    # Send email notification
    if [[ -n "${NOTIFICATION_EMAIL:-}" && -n "${SMTP_HOST:-}" ]]; then
        local subject="✅ TutorPutor Production Deployment Successful"
        local body="TutorPutor has been successfully deployed to production.\n\nEnvironment: $ENVIRONMENT\nVersion: $(git rev-parse --short HEAD)\nTime: $(date)\nDeployer: ${USER:-unknown}"
        
        echo -e "Subject: $subject\n\n$body" | \
        mail -s "TutorPutor Deploy" -S "$SMTP_HOST" -a "$NOTIFICATION_EMAIL" &> /dev/null || true
        
        log_success "Email notification sent"
    fi
}

# Cleanup old deployments
cleanup_old_deployments() {
    log_info "Cleaning up old deployments..."
    
    local backup_dir="$PROJECT_ROOT/backups"
    local max_backups=10
    
    # Keep only the last N backups
    cd "$backup_dir"
    ls -t | tail -n +$((max_backups + 1)) | xargs rm -rf
    
    log_success "Old deployments cleaned up"
}

# Main deployment function
main() {
    log_info "Starting TutorPutor production deployment..."
    
    # Create logs directory
    mkdir -p "$PROJECT_ROOT/logs"
    
    # Run deployment steps
    check_prerequisites
    backup_current_deployment
    build_application
    run_migrations
    deploy_application
    run_health_checks
    setup_monitoring
    deploy_static_assets
    send_notification
    cleanup_old_deployments
    
    log_success "🎉 Production deployment completed successfully!"
    log_info "Application is available at: ${BASE_URL:-https://tutorputor.example.com}"
    log_info "Logs are available at: $LOG_FILE"
}

# Handle script arguments
case "${1:-}" in
    "deploy")
        main
        ;;
    "build")
        build_application
        ;;
    "migrate")
        run_migrations
        ;;
    "health")
        run_health_checks
        ;;
    "backup")
        backup_current_deployment
        ;;
    "monitoring")
        setup_monitoring
        ;;
    "cleanup")
        cleanup_old_deployments
        ;;
    "help"|"-h"|"--help")
        echo "Usage: $0 [command]"
        echo ""
        echo "Commands:"
        echo "  deploy      - Full production deployment"
        echo "  build       - Build application only"
        echo "  migrate     - Run database migrations only"
        echo "  health      - Run health checks only"
        echo "  backup      - Create backup only"
        echo "  monitoring   - Setup monitoring only"
        echo "  cleanup     - Clean up old deployments"
        echo "  help        - Show this help message"
        echo ""
        exit 0
        ;;
    *)
        log_error "Unknown command: ${1:-}"
        echo "Use '$0 help' for available commands"
        exit 1
        ;;
esac
