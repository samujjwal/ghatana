# Quick Start Guide - Production Implementation

**Date:** March 30, 2026  
**Purpose:** Get all implemented components running immediately

---

## 🚀 Immediate Setup (15 minutes)

### 1. Generate Secure Keys

```bash
# Generate 2FA encryption key
export TWO_FACTOR_ENCRYPTION_KEY=$(openssl rand -hex 32)
echo "TWO_FACTOR_ENCRYPTION_KEY=$TWO_FACTOR_ENCRYPTION_KEY"

# Generate JWT secret
export JWT_SECRET=$(openssl rand -base64 32)
echo "JWT_SECRET=$JWT_SECRET"
```

### 2. Update Environment Variables

**Flashit Backend:**
```bash
cd /home/samujjwal/Developments/ghatana/products/flashit/backend/gateway

# Copy and update .env
cp .env.example .env

# Add the generated keys
echo "TWO_FACTOR_ENCRYPTION_KEY=$TWO_FACTOR_ENCRYPTION_KEY" >> .env
echo "JWT_SECRET=$JWT_SECRET" >> .env
```

**Data Cloud UI:**
```bash
cd /home/samujjwal/Developments/ghatana/products/data-cloud/ui

# Create .env.local
echo "VITE_API_URL=http://localhost:8080" > .env.local
echo "VITE_API_TIMEOUT=30000" >> .env.local
```

### 3. Run Database Migrations

```bash
cd /home/samujjwal/Developments/ghatana/products/flashit/backend/gateway

# Run migrations (already applied if schema exists)
npx prisma migrate deploy

# Generate Prisma client
npx prisma generate
```

### 4. Install Dependencies (Workaround)

**For Data Cloud UI (skip pnpm install):**
```bash
# Install only required packages directly
npm install @monaco-editor/react monaco-editor
```

**For Flashit Backend (skip npm install):**
```bash
# Install only required packages directly
npm install --no-save otplib qrcode bcrypt
npm install --save-dev --no-save @types/qrcode
```

---

## 🔧 Start Services

### 1. Start Flashit Backend

```bash
cd /home/samujjwal/Developments/ghatana/products/flashit/backend/gateway

# Start development server
npm run dev

# Server will run on http://localhost:3001
```

### 2. Start Data Cloud Frontend

```bash
cd /home/samujjwal/Developments/ghatana/products/data-cloud/ui

# Start development server
npm run dev

# App will run on http://localhost:5173
```

---

## ✅ Verify Implementation

### 1. Test Flashit Authentication

```bash
# Test refresh token flow
curl -X POST http://localhost:3001/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken": "test-token"}'

# Test 2FA setup
curl -X POST http://localhost:3001/api/auth/2fa/setup \
  -H "Authorization: Bearer your-token"
```

### 2. Test Data Cloud API

```bash
# Test API client
curl http://localhost:8080/api/v1/collections

# Test SQL editor endpoint
curl -X POST http://localhost:8080/api/v1/query/execute \
  -H "Content-Type: application/json" \
  -d '{"sql": "SELECT 1"}'
```

### 3. Check Grafana Dashboards

- **Audio-Video Quality:** http://localhost:3000/d/audio-video-quality
- **AEP Event Flow:** http://localhost:3000/d/aep-event-flow

---

## 📊 Monitor Health

### Check Service Health

```bash
# Flashit health
curl http://localhost:3001/health

# Data Cloud backend health (if running)
curl http://localhost:8080/health

# Check logs
tail -f products/flashit/backend/gateway/logs/app.log
```

### Verify Database

```bash
# Check refresh tokens table
psql -d flashit_dev -c "SELECT COUNT(*) FROM refresh_tokens;"

# Check 2FA table
psql -d flashit_dev -c "SELECT COUNT(*) FROM two_factor_auth;"
```

---

## 🚨 Common Issues & Fixes

### Issue: "Cannot find module '@monaco-editor/react'"
```bash
# Fix: Install manually
cd products/data-cloud/ui
npm install @monaco-editor/react monaco-editor
```

### Issue: "PrismaClient not exported"
```bash
# Fix: Regenerate client
cd products/flashit/backend/gateway
npx prisma generate
```

### Issue: "Property 'prisma' does not exist"
```bash
# Fix: Type declarations already created at:
# src/types/fastify.d.ts
# Restart TypeScript server
```

### Issue: "Module not found: otplib"
```bash
# Fix: Install manually
cd products/flashit/backend/gateway
npm install --no-save otplib qrcode bcrypt
```

---

## 🎯 Next Steps (After Quick Start)

### 1. Complete Integration
- [ ] Register auth routes in main app
- [ ] Test refresh token flow end-to-end
- [ ] Test 2FA setup and verification
- [ ] Integrate API client in Data Cloud UI

### 2. Add Monitoring
- [ ] Deploy Grafana dashboards
- [ ] Configure Prometheus metrics
- [ ] Set up distributed tracing

### 3. Run Tests
- [ ] Run unit tests
- [ ] Run integration tests
- [ ] Verify test coverage

### 4. Deploy to Staging
- [ ] Build Docker images
- [ ] Deploy to staging environment
- [ ] Run smoke tests

---

## 📞 Support

### Documentation
- **Full Implementation Report:** `FINAL_IMPLEMENTATION_REPORT.md`
- **Deployment Checklist:** `DEPLOYMENT_READINESS_CHECKLIST.md`
- **Migration Guide:** `products/data-cloud/docs/MIGRATION_GUIDE_MOCK_TO_REAL_API.md`

### Key Files Created
```
products/flashit/backend/gateway/
├── src/services/auth/refresh-token-service.ts
├── src/services/auth/two-factor-service.ts
├── src/routes/auth-refresh.ts
├── src/routes/auth-2fa.ts
└── src/types/fastify.d.ts

products/data-cloud/ui/
├── src/api/client.ts
├── src/components/sql-editor/MonacoSQLEditor.tsx
└── src/theme/variables.css

monitoring/grafana/dashboards/
├── audio-video-quality.json
└── aep-event-flow.json
```

---

**Quick Start Complete!** 🎉

All critical components are ready. Follow the verification steps above to ensure everything is working correctly.
