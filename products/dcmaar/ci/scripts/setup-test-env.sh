#!/bin/bash
set -e

# Create test network if it doesn't exist
docker network create test-network || true

# Start ClickHouse
docker run -d --name test-clickhouse \
  --network test-network \
  -p 8123:8123 \
  -p 9000:9000 \
  -e CLICKHOUSE_DB=testdb \
  -e CLICKHOUSE_USER=test \
  -e CLICKHOUSE_PASSWORD=test \
  --health-cmd="clickhouse-client --query 'SELECT 1'" \
  --health-interval=5s \
  clickhouse/clickhouse-server:23.3

# Start Redis
docker run -d --name test-redis \
  --network test-network \
  -p 6379:6379 \
  --health-cmd="redis-cli ping" \
  --health-interval=5s \
  redis:7-alpine

echo "Waiting for services to be ready..."

# Wait for ClickHouse
until docker inspect --format="{{.State.Health.Status}}" test-clickhouse | grep -q "healthy"; do
  sleep 1
done

# Wait for Redis
until docker inspect --format="{{.State.Health.Status}}" test-redis | grep -q "healthy"; do
  sleep 1
done

echo "Test environment is ready!"
echo "ClickHouse: localhost:8123"
echo "Redis: localhost:6379"
