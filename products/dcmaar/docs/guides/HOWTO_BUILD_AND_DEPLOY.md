# HOWTO: Build, Install and Deploy Guardian components (Dev / Test / Prod)

This HOWTO provides step-by-step instructions for building, installing, and deploying each Guardian component across development, test, and production environments. It covers the common Guardian components:

- Backend services (`apps/backend`)
- Parent Dashboard (`apps/parent-dashboard`)
- Browser Extension (`apps/browser-extension`)
- Parent Mobile (`apps/parent-mobile`)
- Child Mobile (`apps/child-mobile`)
- Agent React Native (`apps/agent-react-native`) — shared agent implementation
- Desktop Agent Plugin (`apps/agent-desktop`)
- Datastores: PostgreSQL and ClickHouse (where used)
- Cache: Redis

Use the corresponding environment sections (Dev / Test / Prod) below for each component. These instructions assume you are working from the repository root and have the necessary credentials and access.

---

## Prerequisites

Install the tools required for building and deploying Guardian components.

- Node (LTS) and `pnpm` (workspace):

```bash
# recommended: Node 18+ or Node 20 LTS depending on repo engines
node -v
pnpm -v
pnpm install -g pnpm
```

- Java JDK (for Java backend builds) — JDK 17+ (follow the backend README if it uses a specific version).
- Rust & Cargo (for `apps/agent-desktop`):

```bash
rustup toolchain install stable
rustup default stable
cargo --version
```

- Android SDK / Xcode (for mobile builds) — follow the mobile app README.
- Docker & Docker Compose (for local dev/test/prod deployments):

```bash
docker --version
docker compose version
```

- Optional: `make` if the repo exposes Makefile targets, and Git.

Clone the repo and install workspace dependencies:

```bash
git clone <repo-url>
cd products/dcmaar/apps/guardian
pnpm install
```

---

## Shared environment and .env

Guardian uses `.env` at the product root to drive `docker-compose` and some build scripts. Copy the template and edit the values as needed.

```bash
cp .env.example .env
# Edit .env to set database passwords, JWT secrets and host/port mappings
```

Important variables to confirm:

- `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB`, `POSTGRES_PORT`
- `REDIS_PASSWORD`, `REDIS_PORT`
- `BACKEND_PORT`, `DASHBOARD_PORT`
- `VITE_API_URL` / `VITE_WS_URL` for frontend bundles
- `JWT_SECRET`, `SENTRY_DSN` (if used)

---

## 1) Backend (`apps/backend`)

### Dev (local)

1. Ensure `.env` is present and points to a local Postgres/Redis (or use Docker Compose dev stack below).
2. Start backend in dev mode (if backend uses Gradle/Gradle wrapper):

```bash
# from repo root
cd apps/backend
# Run in dev mode (depends on backend project tooling)
./gradlew bootRun   # or: ./gradlew run
```

If the backend is a Node service instead, run the equivalent `pnpm` script:

```bash
pnpm --filter @yappc/guardian-backend dev
```

3. Verify health:

```bash
curl http://localhost:3000/health
```

### Test

- Build and run tests:

```bash
cd apps/backend
./gradlew test
# or via pnpm script
pnpm --filter @yappc/guardian-backend test
```

- Optionally run integration tests using Testcontainers or a test Postgres instance.

### Prod (release)

1. Build the artifact(s):

```bash
cd apps/backend
./gradlew clean build
# Output: build/libs/*.jar or distribution in ./build/distributions
```

2. Containerize (example Dockerfile present in backend):

```bash
docker build -t registry.example.com/guardian/backend:1.2.3 -f Dockerfile .
docker push registry.example.com/guardian/backend:1.2.3
```

3. Deploy via Docker Compose or your orchestrator (Kubernetes/Helm). Example using product `deploy` scripts:

```bash
# In product root
pnpm deploy:prod   # builds images and runs docker compose with production env
```

4. Run DB migrations during deployment (see Migrations section below).

---

## 2) Parent Dashboard (`apps/parent-dashboard`)

### Dev

1. From product root install deps and run the dashboard dev server (Vite):

```bash
cd apps/parent-dashboard
pnpm install
pnpm dev
# Open: http://localhost:8080 (or VITE dev port)
```

2. Set `VITE_API_URL` to `http://localhost:3000` in `.env` or local env when running.

### Test

- Run unit and integration tests:

```bash
pnpm --filter @yappc/guardian-parent-dashboard test
```

- Run lint/format checks:

```bash
pnpm -w lint
pnpm -w format
```

### Prod (build & deploy)

1. Build production bundles:

```bash
pnpm --filter @yappc/guardian-parent-dashboard build
# Output: apps/parent-dashboard/dist
```

2. Serve via Docker or static hosting (the repo contains Dockerfile / compose service `dashboard`). Example with Docker:

```bash
docker build -t registry.example.com/guardian/dashboard:1.2.3 apps/parent-dashboard
docker push registry.example.com/guardian/dashboard:1.2.3
```

3. Use the product `deploy:prod` or `docker compose` to bring up the dashboard along with backend and DB.

---

## 3) Browser Extension (`apps/browser-extension`)

The extension produces browser-specific `dist` directories (chrome/firefox/edge). The codebase uses Vite and `@crxjs` (or similar) to produce artifacts.

### Dev (side-loading & fast iterate)

1. Build development bundle (watch mode optionally):

```bash
cd apps/browser-extension
pnpm install
pnpm build   # or pnpm dev to run watch if available
```

2. Load unpacked extension in Chrome for testing:

- Open `chrome://extensions` → enable Developer mode → Load unpacked → select `apps/browser-extension/dist/chrome`.

3. Use Chrome extension DevTools to inspect background/service worker and popup console.

### Test

- Run unit tests for extension code:

```bash
pnpm --filter @yappc/guardian-browser-extension test
```

- Validate manifest and CSP:

```bash
jq . apps/browser-extension/dist/chrome/manifest.json
```

### Prod (pack & publish)

1. Create production builds for each browser:

```bash
pnpm --filter @yappc/guardian-browser-extension build
# produces dist/chrome, dist/firefox, dist/edge
```

2. Package and publish:

- Chrome: zip `dist/chrome` and upload to Chrome Web Store (use the store uploader or CI integration); or use `webstore` API for automated publishing.
- Firefox: use `web-ext` to sign and publish.
- Edge: publish to Microsoft Addons similarly.

3. Store-specific notes:

- Ensure you don't request `webRequestBlocking` unless enterprise-managed; use DNR rules for blocking.
- Ensure all extension pages follow MV3 CSP (no inline scripts). We migrated fallback helpers to external modules to comply.

---

## 4) Parent Mobile & Child Mobile (`apps/parent-mobile`, `apps/child-mobile`)

Mobile apps are typical React Native apps — follow standard RN build pipelines.

### Dev

1. Install mobile dependencies and run in dev (Metro) or Expo if used:

```bash
cd apps/parent-mobile
pnpm install
pnpm start
# then use emulator or physical device
```

2. For Android emulator/dev:

```bash
pnpm android  # if scripts exist
# or use: npx react-native run-android
```

3. For iOS:

```bash
pnpx pod-install ios
npx react-native run-ios
```

### Test

- Run unit tests and e2e (detox / Playwright mobile or Appium):

```bash
pnpm --filter @yappc/guardian-parent-mobile test
```

### Prod

- Build release artifacts via Gradle/Xcode and sign them correctly:

```bash
# Android - generate AAB/APK
cd apps/parent-mobile/android
./gradlew bundleRelease

# iOS - archive in Xcode and export .ipa
```

- Publish to app stores or distribute via MDM.

---

## 5) Agent React Native (`apps/agent-react-native`)

This package is a shared library used by child-mobile or other agents. Build and test it as a package.

```bash
cd apps/agent-react-native
pnpm install
pnpm build
pnpm test
```

Do not publish a separate app for this; it is consumed by the child/parent mobile apps.

---

## 6) Desktop Agent Plugin (`apps/agent-desktop`)

### Dev

1. Build Rust plugin locally:

```bash
cd apps/agent-desktop
cargo build
# For release build
cargo build --release
```

2. The compiled library will be under `target/debug` or `target/release`.

3. Integrate into DCMAAR agent distribution for local testing.

### Prod

- Build release binary, sign if needed, and include in agent distribution packages for desktops. Use your platform packaging pipelines (deb/rpm/msi/mac dmg) to deploy.

---

## Datastore & Migrations

### PostgreSQL / ClickHouse

- For dev: use Docker Compose services included in the Guardian product root (default `db`, `clickhouse`, `redis`).

```bash
# From products/dcmaar/apps/guardian
pnpm deploy:dev
# or if using docker directly
docker compose up -d db redis clickhouse
```

### Migrations

- Backend migrations are under `apps/backend/migrations` (tooling varies: Flyway / Liquibase / custom script).
- Typical flow:

```bash
# Generate migration (backend-specific)
pnpm --filter @yappc/guardian-backend run migration:generate --name add-field

# Apply migrations (migration step typically runs as part of deploy scripts)
pnpm deploy:prod   # ensure migration step runs during deployment
```

- Always run migrations in a staging environment first and take backups before applying in production.

---

## Docker Compose (Local Dev / Test / Prod)

Guardian provides compose files and deploy helpers. Typical commands:

```bash
# Start dev stack (local databases + backend + dashboard)
pnpm deploy:dev

# Stop stack
pnpm deploy:down

# Deploy production images after building/pushing to registry
pnpm deploy:prod
```

`deploy:prod` usually builds images, tags them, optionally pushes to a registry, and runs compose files for production.

If you prefer Kubernetes, create images and push them to your registry; follow your cluster/Helm deployment conventions.

---

## CI / Release Notes

- CI should run `pnpm -w lint`, `pnpm -w test`, and `pnpm -w build` for the workspace or affected packages.
- Tag releases in Git and include versioned artifact builds. Example release pipeline steps:
  1. Checkout tag `vX.Y.Z`.
  2. Build backend, frontends, mobile bundles.
  3. Build and push Docker images to registry.
  4. Publish browser extension packages to stores.
  5. Publish mobile artifacts to app store pipelines or upload to MDM.

---

## Rollback & Emergency Steps

1. If a deployment causes failures, revert to the previous image tag in your orchestrator and roll back DB schema changes if safe.
2. For DB schema changes that are not backward compatible, use feature flags and deploy changes in multiple phases (deploy read-only schema changes first, then switch code).
3. Keep backups of Postgres (`pg_dump`) and ClickHouse snapshots for restore.

---

## Troubleshooting pointers

- Extension CSP errors: ensure no inline scripts; move helpers into external modules.
- Service worker `window is not defined`: use `chrome.alarms` for periodic tasks in service worker context.
- DNR rule quotas: consolidate rules, avoid per-URL rules if possible.
- Build failures: run `pnpm install` at repo root and ensure Node/Rust/Java versions match repo expectations.

---

## Validation checklist (before production deploy)

- [ ] Tests: `pnpm -w test` green
- [ ] Lint/format: `pnpm -w lint` and `pnpm -w format` applied
- [ ] Build artifacts produced for backend, dashboard, extension and mobile
- [ ] Migrated DB schema validated in staging
- [ ] Backups taken for DBs
- [ ] Monitoring and alerting configured (Sentry, metrics)
- [ ] Rollback plan documented with image tags

---

## Where to look next

- `docs/OPERATIONS.md` for runbook and incident checklist
- `docs/GUARDIAN_ARCHITECTURE_AND_CONTRACTS.md` for contract and data flow details
- `apps/*/README.md` for component-specific build caveats (mobile native signing, extension store publishing steps)

---

## Demo scenarios, features to test, and test credentials

This section provides short demo scenarios, the key features to verify for each component, and suggested test accounts or credentials to use in development and test environments. **Never use real production secrets** in non-production environments; replace placeholders with secure test values and rotate them after use.

Note: The exact sample commands and DB seeding utilities may vary by backend implementation. If your backend provides seed scripts (e.g., `scripts/seed-dev.sh` or a Gradle task), prefer those. The examples below are generic and intended to be adapted.

### 1) Backend

- Features to test
  - Health and readiness endpoints (`GET /health`).
  - Policy evaluation endpoint (simulate a UsageEvent and verify returned `PolicyAction`).
  - Authentication and token issuance (JWT flow) for Dashboard and mobile apps.
  - Migrations: apply migrations, verify schema and rollback where possible.

- Quick demo scenario
  1. Start backend and DB (`pnpm deploy:dev` or run backend locally).
  2. Seed an admin user and a sample family with one child and one device.
  3. Call the policy evaluation endpoint with a simulated `UsageEvent` and assert the response contains an expected `BLOCK`/`ALLOW` action.

- Example seed commands (adapt to your project scripts):

```bash
# Example: run a seed script if present
cd apps/backend
pnpm run seed:dev   # or: ./gradlew runSeed

# Or insert a user directly (psql example)
psql "$DATABASE_URL" -c "INSERT INTO users (email,password,role) VALUES ('admin@example.test','password','admin');"
```

- Suggested test credentials (dev only)

```
Admin user: admin@example.test / Password: Password123!
Demo parent: parent1@example.test / Password: Parent123!
```

### Credentials and Seeding (explicit steps)

If your backend does not provide a `seed:demo` script, follow these explicit steps to create demo users and demo data. All commands assume the repository is checked out at:

`/home/samujjwal/Developments/ghatana/products/ghatana`

Adjust the path if your workspace is located elsewhere.

1. Generate a bcrypt password hash (requires `bcryptjs`):

```bash
# From repository root
cd /home/samujjwal/Developments/ghatana/products/dcmaar/apps/guardian
# Install bcryptjs locally for this task
pnpm add -w -D bcryptjs

# Generate hash for Password123!
node -e "console.log(require('bcryptjs').hashSync('Password123!', 10))" > /tmp/demo_password.hash
cat /tmp/demo_password.hash
# Copy the printed hash for the SQL insert step below
```

2. Insert demo users directly into Postgres using `psql`.

Replace `<HASH_FROM_ABOVE>` with the output from the previous step. Replace `$DATABASE_URL` with the actual connection string or set it in the environment.

```bash
export DATABASE_URL="postgresql://postgres:postgres@localhost:5432/guardian_dev"

# Using psql to insert a basic admin user - adapt column names to your schema
psql "$DATABASE_URL" <<SQL
INSERT INTO users (email, password_hash, role, created_at)
VALUES ('admin@example.test', '<HASH_FROM_ABOVE>', 'admin', now());

INSERT INTO users (email, password_hash, role, created_at)
VALUES ('parent1@example.test', '<HASH_FROM_ABOVE>', 'parent', now());
SQL
```

Notes:

- Column names (`password_hash`) and the users table location may differ per backend implementation. Inspect `/home/samujjwal/Developments/ghatana/products/dcmaar/apps/guardian/apps/backend/src/main/resources` (or equivalent) to confirm schema and adjust SQL accordingly.
- If your backend uses a different user storage (e.g., `accounts` table, or salted hash + salt columns) adapt the insert accordingly.

3. Verify login via backend (example, replace host/port if different):

```bash
curl -X POST "http://localhost:3000/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@example.test","password":"Password123!"}'
```

If login fails, consult backend logs at `/home/samujjwal/Developments/ghatana/products/dcmaar/apps/guardian/apps/backend/logs` or run the backend locally to see detailed errors.

4. Seed sample family, child and device entries

If your backend exposes an admin HTTP API to create families/devices, prefer that. Otherwise insert minimal rows via SQL. Example placeholders (adapt table/column names):

```bash
psql "$DATABASE_URL" <<SQL
INSERT INTO families (name, created_at) VALUES ('Demo Family', now()) RETURNING id;
-- note returned id, use it in next inserts
INSERT INTO children (family_id, name, created_at) VALUES (1, 'Child One', now()) RETURNING id;
INSERT INTO devices (child_id, name, device_type, created_at) VALUES (1, 'Child Laptop', 'browser', now());
SQL
```

5. Clean up demo data

```bash
psql "$DATABASE_URL" -c "DELETE FROM devices WHERE name='Child Laptop';"
psql "$DATABASE_URL" -c "DELETE FROM children WHERE name='Child One';"
psql "$DATABASE_URL" -c "DELETE FROM families WHERE name='Demo Family';"
psql "$DATABASE_URL" -c "DELETE FROM users WHERE email IN ('admin@example.test','parent1@example.test');"
```

If you prefer automation, I can add a small seed script (`scripts/seed-demo.sh`) that wraps the commands above and validates inserted rows. Ask me to create it and I'll add it to `/home/samujjwal/Developments/ghatana/products/dcmaar/apps/guardian/scripts/seed-demo.sh`.

## Appendix A: Build Size & Optimization

### Why is my build large?

Agent-desktop **final artifact: 1.4MB** (shared library `.so`). The Rust build cache is ~24GB but is **not distributed**.

For detailed analysis and optimization commands, see:

- `docs/guides/BUILD_SIZE_ANALYSIS.md` — full breakdown of artifact sizes and cleanup instructions

Quick verify:

```bash
# Check actual compiled library size
ls -lh /home/samujjwal/Developments/ghatana/products/dcmaar/target/release/deps/libguardian_agent_desktop.so
# Expected: ~1.4MB

# Strip symbols for 30% reduction (0.9MB)
strip /home/samujjwal/Developments/ghatana/products/dcmaar/target/release/deps/libguardian_agent_desktop.so

# Check total build cache size (not distributed)
du -sh /home/samujjwal/Developments/ghatana/products/dcmaar/target/
# Expected: ~24GB (development only, never included in release)
```

## Appendix B: Cross-Platform Installers

### Building Installers for Release

Create native installers for macOS, Windows, and Linux:

```bash
# All platforms
./scripts/build-installers.sh --platform=all --version=1.0.0

# Single platform
./scripts/build-installers.sh --platform=macos --version=1.0.0
./scripts/build-installers.sh --platform=windows --version=1.0.0
./scripts/build-installers.sh --platform=linux --version=1.0.0
```

Installers generated in `dist/installers/`:

- **macOS:** `Guardian-1.0.0.dmg` (drag-and-drop), `Guardian.pkg` (package)
- **Windows:** `Guardian-1.0.0-Setup.exe` (NSIS installer)
- **Linux:** `guardian-1.0.0_amd64.deb`, `guardian-1.0.0-1.x86_64.rpm`, `Guardian-1.0.0-x86_64.AppImage`

### Installer Documentation

For detailed installation instructions per platform (system requirements, post-install setup, troubleshooting), see:
- `docs/guides/INSTALLERS.md` — Complete cross-platform installer guide

### Quick Install Commands

**macOS:**
```bash
# Download and drag Guardian.app to Applications, or:
pkginstall Guardian.pkg
```

**Windows:**
```batch
# Run installer
Guardian-1.0.0-Setup.exe
```

**Linux:**
```bash
# Ubuntu/Debian
sudo apt install ./guardian-1.0.0_amd64.deb

# Red Hat/CentOS/Fedora
sudo rpm -i guardian-1.0.0-1.x86_64.rpm

# Any Linux
sudo bash scripts/linux/install.sh
```

### 2) Parent Dashboard

- Features to test
  - Login and session handling.
  - Listing children and devices, viewing usage metrics.
  - Creating and scheduling a policy (time window, block list) and saving it.
  - UI for triggering manual policy re-sync (if present).

- Quick demo scenario
  1. Build/run the dashboard locally and open `http://localhost:8080`.
  2. Log in using the Demo Parent credentials above.
  3. Create a policy to block `example.com` for a child during a specified time window.
  4. Verify the policy appears in the policy list and that the backend receives the policy update (check backend logs).

- Test data and commands

```bash
# Ensure dashboard is pointing to local backend:
export VITE_API_URL=http://localhost:3000
pnpm --filter @yappc/guardian-parent-dashboard dev
```

### 3) Browser Extension

- Features to test
  - Installation and onboarding flow (initial registration with backend).
  - Policy application: ensure DNR rules or equivalent block/allow behavior when policy is active.
  - Telemetry: extension sends usage events to backend.
  - Popup UI: metrics display, dashboard link opens correctly.

- Quick demo scenario
  1. Build the extension and load the unpacked `dist/chrome` in Chrome.
  2. Use the Parent Dashboard to create a policy that blocks `example.com` for the test child device.
  3. In the child browser, navigate to `http://example.com`; confirm the extension blocks or redirects according to the policy.
  4. Inspect extension's Service Worker console for telemetry or rule-application logs.

- Test accounts and notes

```
Extension config: ensure runtime API url points to http://localhost:3000 (dev)
```

### 4) Parent Mobile & Child Mobile

- Features to test
  - Authentication and device linking.
  - Policy arrival and enforcement (child device respects time windows and app limits).
  - Background sync and offline behavior.

- Quick demo scenario
  1. Install the Parent Mobile on a device/emulator and log in with Parent demo account.
  2. Register a child and install the Child Mobile on a separate emulator/device.
  3. From the Parent app, create a policy (limit app time or block a website) and push it to the device.
  4. Verify that the child device enforces the policy and reports telemetry back to backend.

- Test credentials

```
Parent: parent1@example.test / Parent123!
Child device: link via onboarding code shown in Parent app
```

### 5) Agent React Native (library)

- Features to test
  - Integration tests: ensure the agent package compiles and exposes the expected APIs.
  - Simulate policy delivery and event sync using unit/integration tests in `apps/agent-react-native`.

- Quick demo

```bash
cd apps/agent-react-native
pnpm test
pnpm build
```

### 6) Desktop Agent Plugin

- Features to test
  - Plugin loads correctly in the DCMAAR agent-daemon.
  - Enforces desktop-level policies and reports device status.

- Quick demo scenario
  1. Build the plugin (`cargo build --release`).
  2. Load the plugin into a local DCMAAR agent-daemon and confirm plugin appears in agent logs.

### 7) Datastores (Postgres / ClickHouse)

- Features to test
  - Connectivity from backend.
  - Ingested telemetry appears in ClickHouse tables and is queryable.
  - Restore from backup works.

- Quick demo scenario
  1. Start the DB via `pnpm deploy:dev`.
  2. Run a simple query to ensure the `device_usage` table exists and sample rows are present after seeding.

```bash
psql "$DATABASE_URL" -c "SELECT count(*) FROM device_usage LIMIT 1;"
```

### 8) Seed data and automated demos

- If the backend includes seed scripts, use them to populate demo data (users, families, devices, sample usage). Example:

```bash
cd apps/backend
pnpm run seed:demo
```

- If no seed script exists, run SQL inserts or use the backend admin API to create demo accounts and policies.

### Safety and cleanup

- After demos, remove demo accounts and restore any DB snapshots if necessary.
- Use local-only credentials and avoid sharing demo passwords in public places.

---

If you want, I can also:

- Add example Postgres seed SQL and a small `seed:demo` script that inserts admin/parent/child data and a sample policy; or
- Add a one-button demo script (bash) that runs the backend, seeds data, builds the dashboard and extension, and opens the dashboard URL for testers.

Which of those would you prefer me to create next?

If you want, I can now:

- Add automated CI snippets (GitHub Actions) for building and publishing artifacts, or
- Produce per-component sample `docker-compose.prod.yml` and CI pipeline snippets for your preferred registry.

Which next step do you want me to take?
