# YAPPC Production Deployment Guide
**Version:** 1.0.0  
**Last Updated:** 2026-02-03  
**Status:** Production Ready

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Environment Setup](#environment-setup)
3. [Build Process](#build-process)
4. [Deployment Steps](#deployment-steps)
5. [Post-Deployment Verification](#post-deployment-verification)
6. [Monitoring & Alerts](#monitoring--alerts)
7. [Rollback Procedures](#rollback-procedures)
8. [Troubleshooting](#troubleshooting)

---

## Prerequisites

### System Requirements

**Backend:**
- Java 21+
- PostgreSQL 15+
- Redis 7+
- Minimum 4GB RAM
- 20GB disk space

**Frontend:**
- Node.js 20+
- pnpm 8+
- Modern browser support (Chrome 90+, Firefox 88+, Safari 14+, Edge 90+)

### Required Services

- PostgreSQL database
- Redis cache
- WebSocket server
- Object storage (S3-compatible)
- CDN (optional but recommended)

### Access Requirements

- Production server SSH access
- Database credentials
- OAuth provider credentials (Google, GitHub, Microsoft)
- Monitoring service credentials (Sentry, LogRocket, etc.)

---

## Environment Setup

### 1. Clone Repository

```bash
git clone https://github.com/your-org/ghatana.git
cd ghatana/products/yappc
```

### 2. Configure Environment Variables

**Backend (.env.production):**
```bash
# Copy example file
cp .env.production.example .env.production

# Edit with production values
vim .env.production
```

**Frontend (.env.production):**
```bash
cd frontend
cp .env.production.example .env.production
vim .env.production
```

### 3. Database Setup

```bash
# Create production database
createdb yappc_production

# Run migrations
./gradlew flywayMigrate -Denv=production

# Verify schema
psql yappc_production -c "\dt"
```

### 4. Install Dependencies

**Backend:**
```bash
./gradlew build
```

**Frontend:**
```bash
cd frontend
pnpm install
```

---

## Build Process

### Backend Build

```bash
# Clean build
./gradlew clean build

# Skip tests (if already run in CI)
./gradlew clean build -x test

# Build specific module
./gradlew :products:yappc:build
```

### Frontend Build

```bash
cd frontend

# Production build
pnpm build

# Analyze bundle size
pnpm build --analyze

# Build with source maps (debugging)
VITE_SOURCE_MAPS=true pnpm build
```

### Build Artifacts

**Backend:**
- `build/libs/yappc-1.0.0.jar`

**Frontend:**
- `dist/` - Static assets
- `dist/index.html` - Entry point
- `dist/assets/` - JS/CSS bundles

---

## Deployment Steps

### Option A: Docker Deployment (Recommended)

#### 1. Build Docker Images

```bash
# Backend
docker build -t yappc-backend:1.0.0 -f Dockerfile.backend .

# Frontend
docker build -t yappc-frontend:1.0.0 -f Dockerfile.frontend ./frontend
```

#### 2. Push to Registry

```bash
docker tag yappc-backend:1.0.0 registry.example.com/yappc-backend:1.0.0
docker push registry.example.com/yappc-backend:1.0.0

docker tag yappc-frontend:1.0.0 registry.example.com/yappc-frontend:1.0.0
docker push registry.example.com/yappc-frontend:1.0.0
```

#### 3. Deploy with Docker Compose

```bash
# Production docker-compose
docker-compose -f docker-compose.prod.yml up -d

# Check status
docker-compose -f docker-compose.prod.yml ps

# View logs
docker-compose -f docker-compose.prod.yml logs -f
```

### Option B: Kubernetes Deployment

#### 1. Apply Configurations

```bash
# Create namespace
kubectl create namespace yappc-production

# Apply secrets
kubectl apply -f k8s/secrets.yaml -n yappc-production

# Apply configmaps
kubectl apply -f k8s/configmaps.yaml -n yappc-production

# Deploy backend
kubectl apply -f k8s/backend-deployment.yaml -n yappc-production

# Deploy frontend
kubectl apply -f k8s/frontend-deployment.yaml -n yappc-production

# Apply services
kubectl apply -f k8s/services.yaml -n yappc-production

# Apply ingress
kubectl apply -f k8s/ingress.yaml -n yappc-production
```

#### 2. Verify Deployment

```bash
# Check pods
kubectl get pods -n yappc-production

# Check services
kubectl get svc -n yappc-production

# Check ingress
kubectl get ingress -n yappc-production

# View logs
kubectl logs -f deployment/yappc-backend -n yappc-production
```

### Option C: Traditional Server Deployment

#### 1. Deploy Backend

```bash
# Copy JAR to server
scp build/libs/yappc-1.0.0.jar user@server:/opt/yappc/

# SSH to server
ssh user@server

# Start service
sudo systemctl start yappc-backend

# Enable on boot
sudo systemctl enable yappc-backend

# Check status
sudo systemctl status yappc-backend
```

#### 2. Deploy Frontend

```bash
# Copy build to server
rsync -avz frontend/dist/ user@server:/var/www/yappc/

# Configure nginx
sudo cp nginx.conf /etc/nginx/sites-available/yappc
sudo ln -s /etc/nginx/sites-available/yappc /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

---

## Post-Deployment Verification

### Health Checks

```bash
# Backend health
curl https://api.yappc.com/health

# Frontend
curl https://app.yappc.com

# WebSocket
wscat -c wss://api.yappc.com/ws
```

### Smoke Tests

1. **Authentication:**
   - Login with Google OAuth
   - Login with GitHub OAuth
   - Login with Microsoft OAuth

2. **Core Features:**
   - Create new project
   - Open canvas
   - Send chat message
   - Receive notification

3. **Real-Time:**
   - Canvas collaboration
   - Chat messaging
   - Presence indicators

### Performance Checks

```bash
# Lighthouse audit
lighthouse https://app.yappc.com --output=html

# WebPageTest
# Visit https://www.webpagetest.org/

# Check bundle size
du -sh frontend/dist/assets/*.js
```

---

## Monitoring & Alerts

### Application Monitoring

**Sentry (Error Tracking):**
- Frontend errors: https://sentry.io/yappc-frontend
- Backend errors: https://sentry.io/yappc-backend

**LogRocket (Session Replay):**
- Dashboard: https://app.logrocket.com/yappc

**Google Analytics:**
- Dashboard: https://analytics.google.com

### Infrastructure Monitoring

**Prometheus Metrics:**
```bash
# Access metrics
curl https://api.yappc.com/metrics
```

**Grafana Dashboards:**
- Application: https://grafana.yappc.com/d/app
- Infrastructure: https://grafana.yappc.com/d/infra

### Alert Configuration

**Critical Alerts:**
- API error rate > 1%
- Response time > 2s (p95)
- WebSocket disconnections > 10%
- Database connection pool exhausted

**Warning Alerts:**
- Memory usage > 80%
- CPU usage > 70%
- Disk usage > 80%
- Cache hit rate < 80%

---

## Rollback Procedures

### Quick Rollback (Docker)

```bash
# Rollback to previous version
docker-compose -f docker-compose.prod.yml down
docker-compose -f docker-compose.prod.yml up -d yappc-backend:0.9.0 yappc-frontend:0.9.0
```

### Kubernetes Rollback

```bash
# Rollback deployment
kubectl rollout undo deployment/yappc-backend -n yappc-production
kubectl rollout undo deployment/yappc-frontend -n yappc-production

# Check rollout status
kubectl rollout status deployment/yappc-backend -n yappc-production
```

### Database Rollback

```bash
# Rollback migrations
./gradlew flywayUndo -Denv=production

# Restore from backup
pg_restore -d yappc_production backup.dump
```

---

## Troubleshooting

### Common Issues

#### 1. Backend Won't Start

**Symptoms:** Service fails to start, connection refused

**Diagnosis:**
```bash
# Check logs
journalctl -u yappc-backend -n 100

# Check port
netstat -tlnp | grep 8080

# Check Java process
ps aux | grep java
```

**Solutions:**
- Verify database connection
- Check environment variables
- Ensure port is not in use
- Verify Java version

#### 2. Frontend 404 Errors

**Symptoms:** Routes return 404, blank page

**Diagnosis:**
```bash
# Check nginx config
nginx -t

# Check file permissions
ls -la /var/www/yappc/

# Check nginx logs
tail -f /var/log/nginx/error.log
```

**Solutions:**
- Verify nginx configuration
- Check file permissions
- Ensure SPA fallback configured
- Clear browser cache

#### 3. WebSocket Connection Fails

**Symptoms:** Real-time features not working

**Diagnosis:**
```bash
# Test WebSocket
wscat -c wss://api.yappc.com/ws

# Check firewall
sudo ufw status

# Check nginx WebSocket config
grep -A 10 "location /ws" /etc/nginx/sites-available/yappc
```

**Solutions:**
- Verify WebSocket proxy configuration
- Check firewall rules
- Ensure upgrade headers present
- Verify SSL certificate

#### 4. High Memory Usage

**Symptoms:** OOM errors, slow performance

**Diagnosis:**
```bash
# Check memory
free -h

# Check Java heap
jstat -gc <pid>

# Check processes
top -o %MEM
```

**Solutions:**
- Increase heap size
- Check for memory leaks
- Restart service
- Scale horizontally

---

## Security Checklist

- [ ] SSL/TLS certificates configured
- [ ] HTTPS enforced
- [ ] Secure cookies enabled
- [ ] CSP headers configured
- [ ] CORS properly configured
- [ ] Rate limiting enabled
- [ ] SQL injection prevention
- [ ] XSS protection enabled
- [ ] Secrets not in code
- [ ] Database credentials rotated
- [ ] OAuth secrets secured
- [ ] Firewall rules configured
- [ ] Regular security updates
- [ ] Backup encryption enabled
- [ ] Access logs enabled

---

## Performance Checklist

- [ ] CDN configured
- [ ] Gzip compression enabled
- [ ] Brotli compression enabled
- [ ] HTTP/2 enabled
- [ ] Static assets cached
- [ ] Database indexes optimized
- [ ] Connection pooling configured
- [ ] Redis caching enabled
- [ ] Bundle size optimized
- [ ] Lazy loading implemented
- [ ] Code splitting configured
- [ ] Images optimized
- [ ] Fonts optimized
- [ ] Critical CSS inlined
- [ ] Service worker configured

---

## Support & Escalation

### Support Contacts

- **DevOps Team:** devops@yappc.com
- **Backend Team:** backend@yappc.com
- **Frontend Team:** frontend@yappc.com
- **On-Call:** oncall@yappc.com

### Escalation Path

1. **Level 1:** Team lead
2. **Level 2:** Engineering manager
3. **Level 3:** VP Engineering
4. **Level 4:** CTO

### Emergency Procedures

**Critical Outage:**
1. Page on-call engineer
2. Create incident in PagerDuty
3. Join war room (Slack #incidents)
4. Follow incident response playbook
5. Communicate status updates every 15 minutes

---

## Appendix

### Useful Commands

```bash
# Check application version
curl https://api.yappc.com/version

# Database backup
pg_dump yappc_production > backup-$(date +%Y%m%d).sql

# Database restore
psql yappc_production < backup-20260203.sql

# Clear Redis cache
redis-cli FLUSHALL

# Restart services
sudo systemctl restart yappc-backend
sudo systemctl restart nginx

# View real-time logs
tail -f /var/log/yappc/application.log

# Check disk space
df -h

# Check network connections
netstat -an | grep ESTABLISHED
```

### Configuration Files

- Backend: `/opt/yappc/application.properties`
- Nginx: `/etc/nginx/sites-available/yappc`
- Systemd: `/etc/systemd/system/yappc-backend.service`
- Environment: `/opt/yappc/.env.production`

---

**Document Version:** 1.0.0  
**Last Updated:** 2026-02-03  
**Maintained By:** DevOps Team  
**Review Schedule:** Quarterly
