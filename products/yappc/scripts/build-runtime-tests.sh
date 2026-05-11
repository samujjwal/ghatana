#!/bin/bash
# Build and runtime tests for YAPPC
# Task 6.6: Add build/runtime tests for Makefile, Docker build, container health/readiness

set -e

echo "=== YAPPC Build and Runtime Tests ==="

# Test 1: Makefile quick-start command
echo "Test 1: Makefile quick-start command"
make quick-start || {
    echo "FAIL: make quick-start failed"
    exit 1
}
echo "PASS: make quick-start succeeded"

# Test 2: Makefile contract-check command
echo "Test 2: Makefile contract-check command"
make contract-check || {
    echo "FAIL: make contract-check failed"
    exit 1
}
echo "PASS: make contract-check succeeded"

# Test 3: Makefile production-check command
echo "Test 3: Makefile production-check command"
make production-check || {
    echo "FAIL: make production-check failed"
    exit 1
}
echo "PASS: make production-check succeeded"

# Test 4: Docker build
echo "Test 4: Docker build"
docker build -t yappc:test -f products/yappc/Dockerfile products/yappc/ || {
    echo "FAIL: Docker build failed"
    exit 1
}
echo "PASS: Docker build succeeded"

# Test 5: Container health endpoint
echo "Test 5: Container health endpoint"
docker run -d --name yappc-test-health -p 8082:8082 yappc:test
sleep 5

HEALTH_RESPONSE=$(curl -s http://localhost:8082/api/v1/health || echo "failed")
if [[ "$HEALTH_RESPONSE" == "failed" ]]; then
    echo "FAIL: Container health endpoint not responding"
    docker rm -f yappc-test-health
    exit 1
fi
echo "PASS: Container health endpoint responding"

# Test 6: Container ready endpoint
echo "Test 6: Container ready endpoint"
READY_RESPONSE=$(curl -s http://localhost:8082/api/v1/ready || echo "failed")
if [[ "$READY_RESPONSE" == "failed" ]]; then
    echo "FAIL: Container ready endpoint not responding"
    docker rm -f yappc-test-health
    exit 1
fi
echo "PASS: Container ready endpoint responding"

# Test 7: Non-root user in container
echo "Test 7: Non-root user check"
USER_ID=$(docker exec yappc-test-health id -u)
if [[ "$USER_ID" == "0" ]]; then
    echo "FAIL: Container running as root (uid 0)"
    docker rm -f yappc-test-health
    exit 1
fi
echo "PASS: Container running as non-root user (uid $USER_ID)"

# Test 8: Non-root write check
echo "Test 8: Non-root write check"
docker exec yappc-test-health touch /tmp/test-write || {
    echo "FAIL: Non-root user cannot write to /tmp"
    docker rm -f yappc-test-health
    exit 1
}
docker exec yappc-test-health rm /tmp/test-write
echo "PASS: Non-root user can write to allowed directories"

# Test 9: Cleanup
echo "Test 9: Cleanup"
docker rm -f yappc-test-health
docker rmi yappc:test
echo "PASS: Cleanup succeeded"

echo "=== All build/runtime tests passed ==="
