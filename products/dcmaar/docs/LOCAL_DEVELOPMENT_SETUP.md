# Guardian Local Development Setup

> Configuration guide for running Guardian backend, parent dashboard, and browser extension locally for E2E development and testing.

---

## 1. Prerequisites

- **Node.js**: v18+ (LTS recommended)
- **PostgreSQL**: v14+
- **pnpm** or **npm**: Package manager
- **Chrome/Firefox**: For browser extension testing

### 1.1 Install workspace dependencies (recommended)

From the Guardian product root:

```bash
cd products/dcmaar/apps/guardian
pnpm install
```

### 1.2 Build Guardian components (optional)

From the Guardian product root:

```bash
# Quick build (skip tests)
make build-quick

# Full build (with tests)
make build-prod

# Build a specific component
make build-quick COMPONENT=backend
make build-quick COMPONENT=parent-mobile
make build-quick COMPONENT=agent-desktop

# View all options / component names
make help
```

Or run the underlying script directly:

```bash
bash scripts/build.sh --component=all --skip-tests
bash scripts/build.sh --help
```

---

## 2. Environment Configuration

### 2.1 Backend (`apps/backend`)

Create `.env` from `.env.example`:

```bash
cd apps/backend
cp .env.example .env
```

Key environment variables:

```env
# Database
DATABASE_URL=postgresql://postgres:password@localhost:5432/guardian_dev

# JWT
JWT_SECRET=your-local-dev-secret-key-min-32-chars
JWT_EXPIRES_IN=7d

# Server
PORT=3001
NODE_ENV=development

# CORS (allow parent dashboard and extension)
CORS_ORIGINS=http://localhost:5173,chrome-extension://*,moz-extension://*
```

### 2.2 Parent Dashboard (`apps/parent-dashboard`)

Create `.env` from `.env.example`:

```bash
cd apps/parent-dashboard
cp .env.example .env
```

Key environment variables:

```env
# API Base URL (backend)
VITE_API_BASE_URL=http://localhost:3001/api

# WebSocket URL
VITE_WS_URL=ws://localhost:3001
```

### 2.3 Browser Extension (`apps/browser-extension`)

The extension reads configuration from `src/config/index.ts`. For local development:

```typescript
// src/config/index.ts
export const config = {
  apiBaseUrl: 'http://localhost:3001',
  // Device ID and Child ID are obtained during pairing
  // For testing, you can hardcode after running seed:
  // deviceId: '<extension-device-id-from-seed>',
  // childId: '<child-id-from-seed>',
};
```

---

## 3. Database Setup

### 3.1 Create Database

```bash
createdb guardian_dev
```

### 3.2 Run Migrations

```bash
cd apps/backend
psql -d guardian_dev -f src/db/schema.sql
psql -d guardian_dev -f src/db/migrations/002_audit_logs.sql
psql -d guardian_dev -f src/db/migrations/003_add_is_active_to_children.sql
psql -d guardian_dev -f src/db/migrations/004_desktop_telemetry.sql
```

### 3.3 Seed Test Data

```bash
cd apps/backend
npx ts-node src/db/seed.ts --comprehensive
```

This creates:
- **Parent account**: `parent@example.com` (password hash: `$2b$10$seeded`)
- **Children**: Sample Child (12), Teen Child (16), Young Child (7)
- **Devices**: Mobile, Browser Extension, Desktop
- **Policies**: Social Media Block, Adult Content Block, Bedtime Schedule
- **Sample data**: Usage sessions and block events for last 7 days

The seed output shows IDs you can use for extension configuration.

---

## 4. Running Services

### 4.1 Start Backend

```bash
cd apps/backend
npm install
npm run dev
```

Backend runs at `http://localhost:3001`.

### 4.2 Start Parent Dashboard

```bash
cd apps/parent-dashboard
npm install
npm run dev
```

Dashboard runs at `http://localhost:5173`.

### 4.3 Build & Load Browser Extension

```bash
cd apps/browser-extension
npm install
npm run build
```

Load in Chrome:
1. Go to `chrome://extensions`
2. Enable "Developer mode"
3. Click "Load unpacked"
4. Select `apps/browser-extension/dist` folder

---

## 5. Test Accounts

| Role   | Email                  | Password (for testing) |
|--------|------------------------|------------------------|
| Parent | parent@example.com     | Use JWT from login API |

To get a JWT token for testing:

```bash
curl -X POST http://localhost:3001/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"parent@example.com","password":"testpassword"}'
```

Note: The seeded password hash is `$2b$10$seeded` which is not a valid bcrypt hash. For actual login testing, register a new user or update the seed to use a proper hash.

---

## 6. Extension Configuration for Testing

After seeding, configure the extension with the device ID from seed output:

1. Open extension options page
2. Enter the `device_id` from seed output (the one with type `extension`)
3. The extension will start syncing policies from `/api/devices/:id/sync`

Alternatively, for quick testing, hardcode in `src/config/index.ts`:

```typescript
export const config = {
  apiBaseUrl: 'http://localhost:3001',
  deviceId: '<paste-extension-device-id-here>',
};
```

---

## 7. Verifying the Setup

### 7.1 Backend Health Check

```bash
curl http://localhost:3001/api/health
# Should return: {"status":"ok"}
```

### 7.2 List Children (requires auth)

```bash
curl http://localhost:3001/api/children \
  -H "Authorization: Bearer <your-jwt-token>"
```

### 7.3 Get Device Sync Payload

```bash
curl http://localhost:3001/api/devices/<device-id>/sync \
  -H "Authorization: Bearer <your-jwt-token>"
```

### 7.4 Parent Dashboard

1. Open `http://localhost:5173`
2. Log in with test credentials
3. Verify children and devices appear

### 7.5 Browser Extension

1. Click extension icon in browser
2. Verify it shows current domain status
3. Navigate to a blocked domain (e.g., `facebook.com`)
4. Verify block page appears

---

## 8. Troubleshooting

### CORS Errors

Ensure `CORS_ORIGINS` in backend `.env` includes:
- `http://localhost:5173` (dashboard)
- `chrome-extension://*` (Chrome extension)
- `moz-extension://*` (Firefox extension)

### Extension Not Syncing

1. Check browser console for errors
2. Verify `apiBaseUrl` is correct
3. Ensure device ID is valid and paired
4. Check backend logs for sync requests

### Database Connection Issues

1. Verify PostgreSQL is running
2. Check `DATABASE_URL` format
3. Ensure database `guardian_dev` exists

---

## 9. Next Steps

Once local setup is verified:

1. **Phase 1**: Test policy blocking flow (dashboard → backend → extension)
2. **Phase 2**: Test child request flow (extension → backend → dashboard → extension)
3. **Phase 3**: Implement risk scoring and insights

See `GUARDIAN_PARENT_DASHBOARD_E2E_IMPLEMENTATION_CHECKLIST.md` for detailed tasks.
