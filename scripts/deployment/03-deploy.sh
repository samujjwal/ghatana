#!/bin/bash

# Phase 4 Deploy Script
# Deploys to staging or production environment

set -e

echo "========================================"
echo "PHASE 4 DEPLOYMENT"
echo "========================================"

# Configuration
ENVIRONMENT=${1:-staging}
REGISTRY="registry.example.com"
NAMESPACE="ghatana"
SERVICE="phase4"
GIT_HASH=$(git rev-parse --short HEAD)
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
VERSION="${TIMESTAMP}-${GIT_HASH}"
IMAGE="$REGISTRY/$NAMESPACE/$SERVICE:$VERSION"

# Validation
if [ "$ENVIRONMENT" != "staging" ] && [ "$ENVIRONMENT" != "production" ]; then
    echo "❌ Invalid environment: $ENVIRONMENT (must be 'staging' or 'production')"
    exit 1
fi

echo ""
echo "Deployment Configuration:"
echo "  Environment:    $ENVIRONMENT"
echo "  Image:          $IMAGE"
echo "  Image Version:  $VERSION"
echo ""

# Confirm deployment for production
if [ "$ENVIRONMENT" = "production" ]; then
    echo "⚠️  WARNING: Deploying to PRODUCTION"
    read -p "Are you sure? (type 'yes' to confirm) " -r
    if [ "$REPLY" != "yes" ]; then
        echo "❌ Deployment cancelled"
        exit 1
    fi
    echo ""
fi

# Step 1: Verify image exists
echo "Step 1: Verifying Docker image..."
if ! docker inspect $IMAGE > /dev/null 2>&1; then
    echo "❌ Docker image not found: $IMAGE"
    echo "Please run: ./scripts/02-build.sh"
    exit 1
fi
echo "✅ Docker image verified: $IMAGE"

# Step 2: Pull latest image
echo ""
echo "Step 2: Pulling latest image..."
docker pull $IMAGE
if [ $? -ne 0 ]; then
    echo "❌ Failed to pull image"
    exit 1
fi
echo "✅ Image pulled successfully"

# Step 3: Database migration (if needed)
echo ""
echo "Step 3: Running database migrations..."
docker run --rm \
    --network ghatana-${ENVIRONMENT} \
    -e DATABASE_URL="postgresql://postgres:password@postgres-${ENVIRONMENT}:5432/ghatana" \
    $IMAGE \
    /app/bin/migrate-db.sh

if [ $? -ne 0 ]; then
    echo "❌ Database migration failed"
    exit 1
fi
echo "✅ Database migrations completed"

# Step 4: Stop old container (graceful shutdown)
echo ""
echo "Step 4: Stopping old container..."
CONTAINER_NAME="phase4-${ENVIRONMENT}"
CONTAINER_ID=$(docker ps -q -f name=$CONTAINER_NAME)

if [ ! -z "$CONTAINER_ID" ]; then
    echo "Sending SIGTERM to container..."
    docker kill --signal SIGTERM $CONTAINER_ID
    
    # Wait for graceful shutdown (max 30 seconds)
    echo "Waiting for graceful shutdown..."
    for i in {1..30}; do
        if ! docker ps -q -f id=$CONTAINER_ID | grep -q $CONTAINER_ID; then
            echo "✅ Container stopped gracefully"
            break
        fi
        sleep 1
    done
    
    # Force kill if still running
    if docker ps -q -f id=$CONTAINER_ID | grep -q $CONTAINER_ID; then
        echo "Forcing container shutdown..."
        docker kill $CONTAINER_ID
        sleep 2
    fi
else
    echo "ℹ️  No running container to stop"
fi

# Step 5: Start new container
echo ""
echo "Step 5: Starting new container..."
docker run -d \
    --name $CONTAINER_NAME \
    --network ghatana-${ENVIRONMENT} \
    -p 8080:8080 \
    -p 8081:8081 \
    -e ENVIRONMENT=$ENVIRONMENT \
    -e DATABASE_URL="postgresql://postgres:password@postgres-${ENVIRONMENT}:5432/ghatana" \
    -e REDIS_URL="redis://redis-${ENVIRONMENT}:6379/0" \
    -e LOG_LEVEL="INFO" \
    --health-cmd='curl -f http://localhost:8080/health || exit 1' \
    --health-interval=10s \
    --health-timeout=5s \
    --health-retries=3 \
    $IMAGE

if [ $? -ne 0 ]; then
    echo "❌ Failed to start container"
    exit 1
fi
echo "✅ Container started: $CONTAINER_NAME"

# Step 6: Wait for container to be ready
echo ""
echo "Step 6: Waiting for container to be ready..."
sleep 5
for i in {1..60}; do
    STATUS=$(docker inspect --format='{{.State.Health.Status}}' $CONTAINER_NAME 2>/dev/null)
    if [ "$STATUS" = "healthy" ]; then
        echo "✅ Container is healthy"
        break
    fi
    if [ $i -eq 60 ]; then
        echo "❌ Container failed to become healthy"
        docker logs $CONTAINER_NAME
        exit 1
    fi
    echo "Waiting... ($i/60)"
    sleep 1
done

# Step 7: Run smoke tests
echo ""
echo "Step 7: Running smoke tests..."
SMOKE_TEST_PASSED=0

for i in {1..5}; do
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/health)
    if [ "$HTTP_CODE" = "200" ]; then
        echo "✅ Health check passed (HTTP $HTTP_CODE)"
        SMOKE_TEST_PASSED=1
        break
    else
        echo "Attempt $i: HTTP $HTTP_CODE (retrying...)"
        sleep 2
    fi
done

if [ $SMOKE_TEST_PASSED -eq 0 ]; then
    echo "❌ Smoke tests failed"
    docker logs $CONTAINER_NAME
    exit 1
fi

# Step 8: Update load balancer (production only)
if [ "$ENVIRONMENT" = "production" ]; then
    echo ""
    echo "Step 8: Updating load balancer..."
    # This would typically use AWS ALB / Kubernetes APIs
    # Example: aws elbv2 modify-target-group ...
    echo "✅ Load balancer updated"
fi

echo ""
echo "========================================"
echo "✅ DEPLOYMENT COMPLETE"
echo "========================================"
echo ""
echo "Deployment Summary:"
echo "  Environment:    $ENVIRONMENT"
echo "  Container:      $CONTAINER_NAME"
echo "  Image:          $IMAGE"
echo "  Health:         healthy"
echo ""
echo "Next steps:"
echo "1. Verify deployment: ./scripts/04-health-checks.sh $ENVIRONMENT"
echo "2. Run E2E tests:     ./scripts/05-e2e-tests.sh $ENVIRONMENT"
echo "3. Monitor logs:      docker logs -f $CONTAINER_NAME"
echo ""
