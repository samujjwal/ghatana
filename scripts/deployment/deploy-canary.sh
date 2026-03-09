#!/usr/bin/env bash
#
# Canary Deployment Automation Script
# 
# Usage:
#   ./deploy-canary.sh <version> <stage>
#   
# Examples:
#   ./deploy-canary.sh v1.3.0 deploy       # Deploy canary at 0%
#   ./deploy-canary.sh v1.3.0 stage1       # 0.1% - Internal testing
#   ./deploy-canary.sh v1.3.0 stage2       # 1% - Early adopters
#   ./deploy-canary.sh v1.3.0 stage3       # 5% - Controlled rollout
#   ./deploy-canary.sh v1.3.0 stage4       # 25% - Broad rollout
#   ./deploy-canary.sh v1.3.0 stage5       # 50% - Majority rollout
#   ./deploy-canary.sh v1.3.0 promote      # 100% - Full rollout
#   ./deploy-canary.sh v1.3.0 rollback     # Rollback to stable

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
VERSION="${1:-}"
STAGE="${2:-}"
NAMESPACE="ghatana"
APP_NAME="data-cloud"
FEATURE_FLAG_API="http://api.internal/feature-flags"

# Functions
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

validate_args() {
    if [[ -z "$VERSION" ]]; then
        log_error "Version is required"
        echo "Usage: $0 <version> <stage>"
        exit 1
    fi

    if [[ -z "$STAGE" ]]; then
        log_error "Stage is required"
        echo "Usage: $0 <version> <stage>"
        echo "Stages: deploy, stage1, stage2, stage3, stage4, stage5, promote, rollback"
        exit 1
    fi
}

check_prerequisites() {
    log_info "Checking prerequisites..."

    if ! command -v kubectl &> /dev/null; then
        log_error "kubectl is not installed"
        exit 1
    fi

    if ! command -v curl &> /dev/null; then
        log_error "curl is not installed"
        exit 1
    fi

    if ! kubectl get namespace "$NAMESPACE" &> /dev/null; then
        log_error "Namespace $NAMESPACE does not exist"
        exit 1
    fi

    log_info "Prerequisites check passed"
}

deploy_canary() {
    log_info "Deploying canary version $VERSION..."

    # Update deployment image
    kubectl set image deployment/${APP_NAME}-canary \
        app=ghatana/${APP_NAME}:${VERSION}-canary \
        -n "$NAMESPACE"

    # Wait for rollout
    kubectl rollout status deployment/${APP_NAME}-canary -n "$NAMESPACE" --timeout=5m

    # Set canary weight to 0%
    kubectl patch ingress ${APP_NAME}-canary -n "$NAMESPACE" \
        -p '{"metadata":{"annotations":{"nginx.ingress.kubernetes.io/canary-weight":"0"}}}'

    # Create feature flag (disabled)
    curl -X POST "$FEATURE_FLAG_API" \
        -H "Content-Type: application/json" \
        -d "{
            \"key\": \"canary-${VERSION}\",
            \"name\": \"Canary ${VERSION} Rollout\",
            \"state\": \"disabled\",
            \"rollout\": {\"type\": \"none\"},
            \"metadata\": {
                \"deployedAt\": \"$(date -u +"%Y-%m-%dT%H:%M:%SZ")\",
                \"deployedBy\": \"$USER\"
            }
        }" || log_warn "Feature flag may already exist"

    log_info "Canary deployment complete (0% traffic)"
}

stage1_internal() {
    log_info "Stage 1: Internal testing (0.1% traffic)..."

    # Update feature flag for internal team
    curl -X PUT "$FEATURE_FLAG_API/canary-${VERSION}" \
        -H "Content-Type: application/json" \
        -d '{
            "state": "targeted",
            "rollout": {
                "type": "user-list",
                "userIds": ["team-member-1", "team-member-2", "qa-tester-1"]
            }
        }'

    log_info "Stage 1 activated - Internal team only"
    log_warn "Monitor for 5 minutes before proceeding to Stage 2"
}

stage2_early_adopters() {
    log_info "Stage 2: Early adopters (1% traffic)..."

    # Update feature flag for early adopters
    curl -X PUT "$FEATURE_FLAG_API/canary-${VERSION}" \
        -H "Content-Type: application/json" \
        -d '{
            "state": "targeted",
            "rollout": {
                "type": "attribute",
                "attribute": "earlyAdopter",
                "operator": "equals",
                "value": true
            }
        }'

    log_info "Stage 2 activated - Early adopters"
    log_warn "Monitor for 2 hours before proceeding to Stage 3"
}

stage3_controlled() {
    log_info "Stage 3: Controlled rollout (5% traffic)..."

    # Update feature flag for 5% traffic
    curl -X PUT "$FEATURE_FLAG_API/canary-${VERSION}" \
        -H "Content-Type: application/json" \
        -d '{
            "state": "gradual",
            "rollout": {
                "type": "percentage",
                "percentage": 5
            }
        }'

    # Update ingress canary weight
    kubectl patch ingress ${APP_NAME}-canary -n "$NAMESPACE" \
        -p '{"metadata":{"annotations":{"nginx.ingress.kubernetes.io/canary-weight":"5"}}}'

    log_info "Stage 3 activated - 5% traffic"
    log_warn "Monitor for 12 hours before proceeding to Stage 4"
}

stage4_broad() {
    log_info "Stage 4: Broad rollout (25% traffic)..."

    # Update feature flag for 25% traffic
    curl -X PUT "$FEATURE_FLAG_API/canary-${VERSION}" \
        -H "Content-Type: application/json" \
        -d '{
            "state": "gradual",
            "rollout": {
                "type": "percentage",
                "percentage": 25
            }
        }'

    # Update ingress canary weight
    kubectl patch ingress ${APP_NAME}-canary -n "$NAMESPACE" \
        -p '{"metadata":{"annotations":{"nginx.ingress.kubernetes.io/canary-weight":"25"}}}'

    log_info "Stage 4 activated - 25% traffic"
    log_warn "Monitor for 24 hours before proceeding to Stage 5"
}

stage5_majority() {
    log_info "Stage 5: Majority rollout (50% traffic)..."

    # Update feature flag for 50% traffic
    curl -X PUT "$FEATURE_FLAG_API/canary-${VERSION}" \
        -H "Content-Type: application/json" \
        -d '{
            "state": "gradual",
            "rollout": {
                "type": "percentage",
                "percentage": 50
            }
        }'

    # Update ingress canary weight
    kubectl patch ingress ${APP_NAME}-canary -n "$NAMESPACE" \
        -p '{"metadata":{"annotations":{"nginx.ingress.kubernetes.io/canary-weight":"50"}}}'

    log_info "Stage 5 activated - 50% traffic"
    log_warn "Monitor for 24 hours before promoting to 100%"
}

promote_full() {
    log_info "Promoting canary to production (100% traffic)..."

    # Update production deployment to canary version
    kubectl set image deployment/${APP_NAME}-production \
        app=ghatana/${APP_NAME}:${VERSION} \
        -n "$NAMESPACE"

    # Wait for production rollout
    kubectl rollout status deployment/${APP_NAME}-production -n "$NAMESPACE" --timeout=10m

    # Enable feature flag for all users
    curl -X PUT "$FEATURE_FLAG_API/canary-${VERSION}" \
        -H "Content-Type: application/json" \
        -d '{
            "state": "enabled",
            "rollout": {
                "type": "all"
            }
        }'

    # Scale down canary deployment
    kubectl scale deployment/${APP_NAME}-canary --replicas=0 -n "$NAMESPACE"

    # Remove canary weight from ingress
    kubectl patch ingress ${APP_NAME}-canary -n "$NAMESPACE" \
        -p '{"metadata":{"annotations":{"nginx.ingress.kubernetes.io/canary-weight":"0"}}}'

    log_info "Promotion complete - 100% traffic on $VERSION"
    log_warn "Monitor for 48 hours, then schedule cleanup"
}

rollback() {
    log_error "Rolling back canary deployment..."

    # Disable feature flag
    curl -X PUT "$FEATURE_FLAG_API/canary-${VERSION}" \
        -H "Content-Type: application/json" \
        -d '{
            "state": "disabled",
            "rollout": {
                "type": "none"
            }
        }'

    # Set canary weight to 0%
    kubectl patch ingress ${APP_NAME}-canary -n "$NAMESPACE" \
        -p '{"metadata":{"annotations":{"nginx.ingress.kubernetes.io/canary-weight":"0"}}}'

    # Scale down canary deployment
    kubectl scale deployment/${APP_NAME}-canary --replicas=0 -n "$NAMESPACE"

    # Notify team
    echo "Rollback complete - All traffic on stable version"
    log_warn "Create incident postmortem and notify team"
}

# Main execution
main() {
    validate_args
    check_prerequisites

    case "$STAGE" in
        deploy)
            deploy_canary
            ;;
        stage1)
            stage1_internal
            ;;
        stage2)
            stage2_early_adopters
            ;;
        stage3)
            stage3_controlled
            ;;
        stage4)
            stage4_broad
            ;;
        stage5)
            stage5_majority
            ;;
        promote)
            promote_full
            ;;
        rollback)
            rollback
            ;;
        *)
            log_error "Unknown stage: $STAGE"
            echo "Valid stages: deploy, stage1, stage2, stage3, stage4, stage5, promote, rollback"
            exit 1
            ;;
    esac

    log_info "✅ Done!"
}

main
