# Tutorputor Deployment Guide
**Version:** 1.0  
**Date:** March 28, 2026  
**Audit Remediation:** Phase 1 Complete

---

## Prerequisites

### Required Software
- Node.js 18+ and pnpm 8+
- PostgreSQL 14+
- Redis 6+
- Stripe account (test and production keys)

### Environment Setup
```bash
# Install dependencies
cd products/tutorputor
pnpm install

# Install Prisma CLI globally (optional)
npm install -g prisma
```

---

## Environment Variables

### Required Variables

Create `.env` file in `products/tutorputor/`:

```bash
# Database
DATABASE_URL="postgresql://user:password@localhost:5432/tutorputor"

# Payment Processing
STRIPE_SECRET_KEY="sk_test_..." # or sk_live_... for production

# Caching & Sessions
REDIS_URL="redis://localhost:6379"

# Authentication
JWT_SECRET="your-very-long-secret-key-at-least-32-characters-long"

# Application
NODE_ENV="development" # or "production"
PORT="3000"
HOST="0.0.0.0"
```

### Optional Variables

```bash
# External Services
AI_SERVICE_URL="http://localhost:50051"
FEATURE_STORE_URL="http://localhost:8080"
AI_REGISTRY_URL="http://localhost:8081"

# CORS
CORS_ORIGIN="*" # or specific domain for production

# gRPC
GRPC_SERVER_ADDRESS="localhost:50051"
GRPC_USE_TLS="false"

# LTI Integration
LTI_PUBLIC_KEY="-----BEGIN PUBLIC KEY-----\n...\n-----END PUBLIC KEY-----"
```

---

## Database Setup

### 1. Create PostgreSQL Database

```bash
# Connect to PostgreSQL
psql -U postgres

# Create database
CREATE DATABASE tutorputor;

# Create user (if needed)
CREATE USER tutorputor_user WITH PASSWORD 'secure_password';
GRANT ALL PRIVILEGES ON DATABASE tutorputor TO tutorputor_user;

# Exit
\q
```

### 2. Generate Prisma Client

```bash
cd libs/tutorputor-core
npx prisma generate
```

### 3. Run Database Migration

```bash
# Create and apply migration
npx prisma migrate dev --name audit-remediation-phase-1

# Or for production
npx prisma migrate deploy
```

### 4. Seed Database (Optional)

```bash
npx prisma db seed
```

---

## Redis Setup

### Local Development

```bash
# Install Redis (macOS)
brew install redis

# Start Redis
redis-server

# Verify Redis is running
redis-cli ping
# Should return: PONG
```

### Production

Use managed Redis service (AWS ElastiCache, Redis Cloud, etc.)

---

## Stripe Setup

### 1. Get API Keys

1. Sign up at https://stripe.com
2. Get test keys from Dashboard → Developers → API keys
3. For production, activate account and get live keys

### 2. Configure Webhooks

```bash
# Install Stripe CLI for local testing
brew install stripe/stripe-cli/stripe

# Login
stripe login

# Forward webhooks to local server
stripe listen --forward-to localhost:3000/api/webhooks/stripe
```

### 3. Webhook Events to Subscribe

- `customer.subscription.created`
- `customer.subscription.updated`
- `customer.subscription.deleted`
- `invoice.payment_succeeded`
- `invoice.payment_failed`
- `customer.subscription.trial_will_end`

---

## Build and Start

### Development

```bash
# Start in development mode
pnpm dev

# Or with specific port
PORT=3000 pnpm dev
```

### Production

```bash
# Build application
pnpm build

# Start production server
pnpm start

# Or with PM2
pm2 start ecosystem.config.js
```

---

## Validation Checklist

### Pre-Deployment

- [ ] All environment variables set and validated
- [ ] Database connection successful
- [ ] Redis connection successful
- [ ] Stripe keys validated (no placeholders)
- [ ] Prisma client generated
- [ ] Database migrations applied
- [ ] All tests passing

### Post-Deployment

- [ ] Application starts without errors
- [ ] Health check endpoint responds
- [ ] Database queries working
- [ ] Redis caching working
- [ ] Stripe webhooks receiving events
- [ ] Structured logging working
- [ ] Error recovery working

---

## Health Checks

### Application Health

```bash
curl http://localhost:3000/health
```

Expected response:
```json
{
  "status": "healthy",
  "timestamp": "2026-03-28T12:00:00.000Z",
  "checks": {
    "database": "ok",
    "redis": "ok",
    "stripe": "ok"
  }
}
```

### Database Health

```bash
# Check database connection
npx prisma db execute --stdin <<< "SELECT 1"
```

### Redis Health

```bash
redis-cli ping
```

---

## Monitoring

### Structured Logging

Logs are output in JSON format for easy parsing:

```json
{
  "level": "info",
  "timestamp": "2026-03-28T12:00:00.000Z",
  "component": "PaymentService",
  "message": "Subscription created",
  "tenantId": "tenant_123",
  "subscriptionId": "sub_456"
}
```

### Log Aggregation

Configure log aggregation service (e.g., Datadog, New Relic, ELK):

```bash
# Example: Forward logs to Datadog
export DD_API_KEY="your_api_key"
export DD_SITE="datadoghq.com"
```

### Metrics

Key metrics to monitor:

- Request rate and latency
- Error rate
- Database query performance
- Redis hit/miss ratio
- Worker job success/failure rate
- Payment processing success rate

---

## Troubleshooting

### Database Connection Issues

```bash
# Check DATABASE_URL format
echo $DATABASE_URL

# Test connection
psql $DATABASE_URL -c "SELECT 1"

# Check Prisma schema
npx prisma validate
```

### Stripe Validation Errors

```bash
# Verify key format
echo $STRIPE_SECRET_KEY | grep -E "^sk_(test|live)_[a-zA-Z0-9]{24,}$"

# Test Stripe connection
stripe customers list --limit 1
```

### Redis Connection Issues

```bash
# Check Redis URL
echo $REDIS_URL

# Test connection
redis-cli -u $REDIS_URL ping
```

### Environment Validation Errors

The application validates all environment variables at startup. Check logs for detailed error messages:

```bash
# Example error output
❌ Environment Variable Validation Failed

The following environment variables are missing or invalid:

  • STRIPE_SECRET_KEY
    Reason: Must be a valid Stripe API key (sk_test_* or sk_live_*)
    Example: STRIPE_SECRET_KEY=[YOUR_STRIPE_TEST_API_KEY]

  • JWT_SECRET
    Reason: Must be at least 32 characters for security
    Example: JWT_SECRET=your-very-long-secret-key-at-least-32-characters
```

---

## Rollback Procedures

### Database Rollback

```bash
# Rollback last migration
npx prisma migrate resolve --rolled-back <migration_name>

# Or restore from backup
psql $DATABASE_URL < backup.sql
```

### Application Rollback

```bash
# With PM2
pm2 stop tutorputor
git checkout <previous_version>
pnpm install
pnpm build
pm2 start tutorputor

# With Docker
docker-compose down
docker-compose up -d --build <previous_image>
```

---

## Security Checklist

### Production Security

- [ ] Use strong JWT_SECRET (32+ characters)
- [ ] Use production Stripe keys (sk_live_*)
- [ ] Enable HTTPS/TLS
- [ ] Set CORS_ORIGIN to specific domain
- [ ] Use managed PostgreSQL with SSL
- [ ] Use managed Redis with authentication
- [ ] Enable rate limiting
- [ ] Configure firewall rules
- [ ] Set up monitoring and alerts
- [ ] Regular security audits

### Secrets Management

```bash
# Use secrets manager (AWS Secrets Manager, Vault, etc.)
# Never commit .env files to git

# Example with AWS Secrets Manager
aws secretsmanager get-secret-value \
  --secret-id tutorputor/production \
  --query SecretString \
  --output text > .env
```

---

## Performance Optimization

### Database

```sql
-- Add indexes for common queries
CREATE INDEX idx_enrollments_user_id ON "Enrollment"("userId");
CREATE INDEX idx_enrollments_module_id ON "Enrollment"("moduleId");
CREATE INDEX idx_learning_events_timestamp ON "LearningEvent"("timestamp");
```

### Redis Caching

```typescript
// Cache frequently accessed data
const cacheKey = `user:${userId}:dashboard`;
const cached = await redis.get(cacheKey);

if (cached) {
  return JSON.parse(cached);
}

const data = await fetchDashboard(userId);
await redis.setex(cacheKey, 300, JSON.stringify(data)); // 5 min TTL
return data;
```

### Connection Pooling

```bash
# PostgreSQL connection pool
DATABASE_URL="postgresql://user:password@localhost:5432/tutorputor?connection_limit=20"
```

---

## Backup and Recovery

### Database Backup

```bash
# Daily backup
pg_dump $DATABASE_URL > backup_$(date +%Y%m%d).sql

# Automated backup with cron
0 2 * * * pg_dump $DATABASE_URL | gzip > /backups/tutorputor_$(date +\%Y\%m\%d).sql.gz
```

### Redis Backup

```bash
# Save Redis snapshot
redis-cli SAVE

# Copy RDB file
cp /var/lib/redis/dump.rdb /backups/redis_$(date +%Y%m%d).rdb
```

---

## Scaling Considerations

### Horizontal Scaling

- Use load balancer (nginx, AWS ALB)
- Stateless application design
- Shared Redis for sessions
- Database read replicas

### Vertical Scaling

- Increase database resources
- Increase Redis memory
- Optimize queries and indexes

---

## Support and Maintenance

### Log Locations

- Application logs: `stdout` (JSON format)
- Database logs: PostgreSQL logs directory
- Redis logs: Redis logs directory

### Maintenance Windows

- Database migrations: Low-traffic periods
- Application updates: Rolling deployment
- Backup verification: Weekly

---

## Next Steps

1. Complete remaining audit findings
2. Add comprehensive integration tests
3. Set up CI/CD pipeline
4. Configure monitoring and alerts
5. Perform load testing
6. Security penetration testing
7. Documentation updates

---

*Last Updated: March 28, 2026*  
*Version: 1.0*  
*Contact: DevOps Team*
