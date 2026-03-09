#!/bin/bash

# Phase 4 Build Script
# Builds Docker image and pushes to registry

set -e

echo "========================================"
echo "PHASE 4 BUILD & PACKAGE"
echo "========================================"

# Configuration
REGISTRY="registry.example.com"
NAMESPACE="ghatana"
SERVICE="phase4"
GIT_HASH=$(git rev-parse --short HEAD)
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
VERSION="${TIMESTAMP}-${GIT_HASH}"

echo ""
echo "Build Configuration:"
echo "  Registry:   $REGISTRY"
echo "  Namespace:  $NAMESPACE"
echo "  Service:    $SERVICE"
echo "  Version:    $VERSION"
echo "  Git Hash:   $GIT_HASH"
echo ""

# Step 1: Clean build
echo "Step 1: Building application..."
./gradlew clean build -x test -x integrationTest
if [ $? -ne 0 ]; then
    echo "❌ Build failed"
    exit 1
fi
echo "✅ Application built successfully"

# Step 2: Build Docker image
echo ""
echo "Step 2: Building Docker image..."
docker build \
    --tag $REGISTRY/$NAMESPACE/$SERVICE:$VERSION \
    --tag $REGISTRY/$NAMESPACE/$SERVICE:latest \
    --build-arg BUILD_VERSION=$VERSION \
    --build-arg BUILD_DATE=$(date -u +'%Y-%m-%dT%H:%M:%SZ') \
    --build-arg GIT_HASH=$GIT_HASH \
    -f Dockerfile \
    .

if [ $? -ne 0 ]; then
    echo "❌ Docker build failed"
    exit 1
fi
echo "✅ Docker image built successfully"

# Step 3: Verify image
echo ""
echo "Step 3: Verifying Docker image..."
docker inspect $REGISTRY/$NAMESPACE/$SERVICE:$VERSION > /dev/null
if [ $? -ne 0 ]; then
    echo "❌ Docker image verification failed"
    exit 1
fi
echo "✅ Docker image verified"

# Step 4: Show image info
echo ""
echo "Image Information:"
docker images | grep $SERVICE

# Step 5: Push to registry (optional)
read -p "Push to registry? (y/n) " -n 1 -r
echo ""
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo ""
    echo "Step 5: Pushing Docker image to registry..."
    
    # Login to registry (assumes credentials are configured)
    docker login $REGISTRY
    
    # Push image
    docker push $REGISTRY/$NAMESPACE/$SERVICE:$VERSION
    docker push $REGISTRY/$NAMESPACE/$SERVICE:latest
    
    if [ $? -ne 0 ]; then
        echo "❌ Docker push failed"
        exit 1
    fi
    echo "✅ Docker image pushed successfully"
    echo ""
    echo "Image: $REGISTRY/$NAMESPACE/$SERVICE:$VERSION"
    echo "Image: $REGISTRY/$NAMESPACE/$SERVICE:latest"
fi

echo ""
echo "========================================"
echo "✅ BUILD COMPLETE"
echo "========================================"
echo ""
echo "Next steps:"
echo "1. Run: ./scripts/02-build.sh          (this script)"
echo "2. Run: ./scripts/03-deploy.sh"
echo "3. Run: ./scripts/04-health-checks.sh"
echo ""
