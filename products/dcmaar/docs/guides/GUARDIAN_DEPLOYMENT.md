# Guardian Deployment Guide

## 1. Purpose and Scope

This guide explains how to deploy the **Guardian** parental control product within the **DCMAAR** ecosystem.

It covers:

- **Server-side setup**: backend, database, cache, Docker, environment configuration.
- **Client-side setup**: what must be installed for **parents** versus **children**.
- How the different Guardian components connect and what artifacts you deploy.

This document is self-contained and should be the primary reference for Guardian deployment.

---

## 2. Architecture Overview (Deployment View)

Guardian is a product that sits on top of the DCMAAR platform.

- **Server-side (operator responsibility)**
  - `apps/backend`: Guardian backend API and services.
  - PostgreSQL database (`db` service in Docker Compose).
  - Redis cache (`redis` service in Docker Compose).
  - `apps/parent-dashboard`: Parent web UI, served via Docker.

- **Client-side (deployed to end-user devices)**
  - **Parent-side**
    - Parent Dashboard (web, in browser).
    - Parent Mobile app (`@guardian/parent-mobile`).
  - **Child-side (agents)**
    - Child Mobile app (`@guardian/child-mobile`) – ships to child devices and embeds the shared agent implementation.
    - Guardian Agent React Native implementation (`@guardian/agent-react-native`) – shared agent module (services/hooks/types) consumed by the child app and other surfaces.
    - Browser Extension (`apps/browser-extension`).
    - Desktop Agent Plugin (`apps/agent-desktop`, Rust crate integrated into DCMAAR agent-daemon).

The Guardian workspace coordinates build, test, and deployment across these components via scripts in `scripts/build.sh` and `scripts/deploy.sh` and Docker Compose files at the product root.

---

## 3. Server-Side Deployment

### 3.1 Prerequisites

On the host where you will run Guardian server components (VM, bare metal, or container host):

- **System dependencies**
  - Docker `>= 20.x`.
  - Docker Compose (either `docker compose` or `docker-compose`).
  - `bash`.

- **Network / Ports** (defaults, configurable via `.env`):
  - PostgreSQL: `5432` (`POSTGRES_PORT`).
  - Redis: `6379` (`REDIS_PORT`).
  - Backend API: `3000` (`BACKEND_PORT`).
  - Parent Dashboard: `8080` (`DASHBOARD_PORT`).

- **Source checkout**
  - Clone the repo and navigate to the Guardian product root:

    ```bash
    cd products/dcmaar/apps/guardian
    ```

### 3.2 Environment Configuration

Guardian uses a `.env` file at the product root to drive Docker Compose and backend configuration.

1. **Create `.env` from template**

   ```bash
   cd products/dcmaar/apps/guardian
   cp .env.example .env
   ```

2. **Edit `.env` for your environment**

   Adjust at least the following groups (names may vary slightly; consult `docker-compose.yml` and backend config):

   - **Database**
     - `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB`, `POSTGRES_PORT`.
   - **Redis**
     - `REDIS_PASSWORD`, `REDIS_PORT`.
   - **Backend**
     - `BACKEND_PORT`.
     - `JWT_SECRET`, `JWT_EXPIRES_IN`.
     - `SENTRY_DSN`, `LOG_LEVEL`.
   - **Dashboard**
     - `DASHBOARD_PORT`.
     - `VITE_API_URL`, `VITE_WS_URL` (public URLs the dashboard uses to reach the backend).

3. **Production hardening recommendations**

   - Use **strong, non-default passwords** for database and Redis.
   - Ensure `JWT_SECRET` and `JWT_REFRESH_SECRET` are randomly generated and at least 32 characters.
   - Configure `CORS_ORIGIN` and any other production-specific env vars required by backend validation.
   - Enable Sentry (`SENTRY_DSN`, `VITE_SENTRY_DSN`) for observability where appropriate.

### 3.3 Building Guardian Components

From the Guardian product root:

- **Full production build (with tests):**

  ```bash
  # Using Makefile
  make build ENV=production

  # Or using package.json script
  pnpm build   # runs: bash scripts/build.sh
  ```

- **Quick build (skip tests):**

  ```bash
  make build-quick
  ```

The build script (`scripts/build.sh`) will build:

- Backend API: `apps/backend` (outputs `apps/backend/dist/`).
- Parent Dashboard: `apps/parent-dashboard` (outputs `apps/parent-dashboard/dist/`).
- Parent Mobile app bundles: `apps/parent-mobile` (outputs JS bundles/assets into `apps/parent-mobile/dist/`).
- Browser Extension: `apps/browser-extension` (outputs into `apps/browser-extension/dist/<browser>/`).
- Guardian Agent React Native web export: `apps/agent-react-native` (outputs into `apps/agent-react-native/dist/`).
- Desktop Agent plugin: `apps/agent-desktop` (Rust artifacts under workspace `target/release/`).

At the end, the script prints a **Build Summary** listing each component and its artifacts.

### 3.4 Deploying with Docker Compose

Guardian provides a deployment script that wraps Docker Compose: `scripts/deploy.sh`.

#### 3.4.1 Core commands (via `pnpm`)

From `products/dcmaar/apps/guardian`:

```bash
# Deploy to development environment
pnpm deploy:dev

# Deploy to production (build images first)
pnpm deploy:prod    # runs: bash scripts/deploy.sh --env=production --build

# Bring services up without extra scripts
pnpm deploy:up

# Tear services down
pnpm deploy:down

# Tail logs
pnpm deploy:logs

# Show status
pnpm deploy:status
```

#### 3.4.2 Core commands (via Makefile)

Alternatively, you can use Makefile targets:

```bash
# Deploy all services for the current ENV (default: development)
make deploy ENV=development

# Build images and deploy for production
make deploy-build ENV=production COMPONENT=full-stack

# Stop all services
make stop ENV=production

# Restart backend only
make restart ENV=production COMPONENT=backend

# View logs
make logs ENV=production COMPONENT=backend

# Show service status
make status ENV=production
```

Under the hood, these commands call `scripts/deploy.sh`, which:

- Selects the appropriate compose files for the chosen environment.
- Optionally builds images (`--build`).
- Starts or stops Docker services.

### 3.5 Services and Ports

The primary services are defined in `docker-compose.yml`:

- **db** (PostgreSQL)
  - Image: `postgres:15-alpine`.
  - Port: `${POSTGRES_PORT:-5432}` → 5432 inside container.
  - Data: `postgres-data` volume.

- **redis**
  - Image: `redis:7-alpine`.
  - Port: `${REDIS_PORT:-6379}` → 6379.
  - Data: `redis-data` volume.

- **backend**
  - Build context: `./backend`.
  - Port: `${BACKEND_PORT:-3000}` → 3000.
  - Environment: `DATABASE_URL`, `REDIS_URL`, JWT settings, etc.

- **dashboard**
  - Build context: `./apps/parent-dashboard`.
  - Port: `${DASHBOARD_PORT:-8080}` → 8080.
  - Environment: `VITE_API_URL`, `VITE_WS_URL`, etc.

After `deploy`/`deploy-build`:

- Backend health check: `http://<backend-host>:3000/health`.
- Dashboard: `http://<dashboard-host>:8080/` (or mapped domain).

### 3.6 Operational Checks

Once services are running:

- Check containers:

  ```bash
  docker compose ps
  ```

- Tail logs:

  ```bash
  pnpm deploy:logs
  # or: make logs ENV=production COMPONENT=backend
  ```

- Confirm dashboard can authenticate parents and display child/device data.

---

## 4. Client-Side Deployment

This section explains what must be installed on **parent** devices vs **child** devices, and what each piece does.

### 4.1 Parent-Facing Components

These are used by **parents** to configure and monitor Guardian.

#### 4.1.1 Parent Dashboard (Web)

- **Code:** `apps/parent-dashboard` (React + Vite).
- **Runs on:** Any modern browser on a parent’s device.
- **Served by:** `dashboard` Docker service.

**Deployment:**

- Already handled by server-side deployment (build + Docker Compose).
- Expose dashboard over HTTPS (via reverse proxy or load balancer) at a URL like:

  ```text
  https://guardian.example.com
  ```

**What parents do:**

- Open the dashboard URL in a browser.
- Create accounts / login.
- Add children and devices.
- View usage, configure policies, and review alerts.

#### 4.1.2 Parent Mobile App (`@guardian/parent-mobile`)

- **Code:** `apps/parent-mobile` (React Native).
- **Runs on:** Parent’s phone (Android / iOS).

**Build artifacts (workspace / internal use):**

From Guardian product root (via `make build`), or directly:

```bash
cd products/dcmaar/apps/guardian/apps/parent-mobile
pnpm run build
# generates JS bundles and assets under dist/
```

**Production distribution:**

- Use standard React Native mobile pipelines:
  - Android: Gradle build from `android/` → `.apk` / `.aab`.
  - iOS: Xcode build from `ios/` → `.ipa`.
- Publish to your chosen app-store(s) or distribute via MDM/enterprise channels.

**What parents do:**

- Install the **Parent Mobile** app from the appropriate store.
- Sign in using the same account as the web dashboard.
- Configure policies and review child activity directly from their phone.

### 4.2 Child-Facing Components (Agents)

These components are installed on **child devices** to collect usage data and enforce policies.

#### 4.2.1 Child Mobile App (`@guardian/child-mobile`)

- **Code:** `apps/child-mobile` (React Native).
- **Runs on:** Child’s phone.

**Deployment pattern:**

- Built as a standard mobile app using the RN toolchain.
- Installed on each child’s device (via store or MDM).

Internally, the Child Mobile app embeds the **shared agent implementation** exported from `@guardian/agent-react-native/agent`:

- Uses the agent’s services (event sync, policy notifications, bridge integration).
- Reuses the connector/contract layer provided by `@guardian/agent-react-native` and `@guardian/agent-core`.

**What parents/operators do:**

- Ensure the Child Mobile app is installed on each child device.
- During onboarding, link the device to the child profile in the Parent Dashboard.

#### 4.2.2 Guardian Agent React Native implementation (`@guardian/agent-react-native`)

- **Code:** `apps/agent-react-native` (Expo + React Native).
- **Entry point for reuse:** `@guardian/agent-react-native/agent`.

**Role:**

- Implements the **agent layer** used by the Child Mobile app and potentially other clients:
  - Event synchronization and reporting.
  - Policy polling and notification services.
  - Native bridge coordination (usage stats, permissions, background sync).
- Reuses `@guardian/agent-core` contracts and DCMAAR connector framework.

**Usage in deployments:**

- In normal production deployments you do **not** ship `@guardian/agent-react-native` as a separate end-user app.
- Instead, you:
  - Build and ship `@guardian/child-mobile` to child devices.
  - Rely on `child-mobile` to embed the agent implementation from `@guardian/agent-react-native/agent`.

#### 4.2.3 Browser Extension (`apps/browser-extension`)

- **Code:** `apps/browser-extension` (Vite-based multi-browser extension).
- **Runs on:** Child’s desktop or laptop browsers (Chrome, Firefox, Edge).

**Build artifacts:**

From Guardian product root:

```bash
make build COMPONENT=browser-extension
# produces dist/chrome, dist/firefox, dist/edge under apps/browser-extension
```

Each `dist/<browser>/` folder contains the packaged extension for that browser.

**Deployment:**

- **QA / internal:** side-load from `dist/<browser>`.
- **Production:**
  - Package and publish to:
    - Chrome Web Store (for `dist/chrome`).
    - Firefox Add-ons (for `dist/firefox`).
    - Edge Add-ons (for `dist/edge`).

**What parents/operators do:**

- Ensure the extension is installed in child browsers.
- Configure extension settings (e.g. backend URL, policy endpoint) if not auto-discovered.

#### 4.2.4 Desktop Agent Plugin (`apps/agent-desktop`)

- **Code:** `apps/agent-desktop` (Rust library / plugin).
- **Runs on:** Child’s desktop/laptop as part of the DCMAAR agent-daemon.

**Build artifacts:**

From Guardian product root:

```bash
make build COMPONENT=agent-desktop
# invokes cargo build --release under apps/agent-desktop
# artifacts end up under the DCMAAR workspace target/release directory
```

You will see Rust artifacts like `libguardian_agent_desktop.*` in the workspace `target/release`.

**Deployment:**

- Integrate the compiled Rust plugin into your DCMAAR agent-daemon distribution.
- Deploy the combined agent package to child desktops (Windows/macOS/Linux) using your standard distribution model.

**What operators do:**

- Include the Guardian agent plugin in desktop agent releases.
- Ensure configuration points to the correct Guardian backend endpoints.

---

## 5. "What Goes Where" – Parent vs Child Summary

### 5.1 Table: Components by Consumer

| Component                                  | Type             | Who installs it?        | Where it runs                            |
|--------------------------------------------|------------------|-------------------------|------------------------------------------|
| Guardian Backend (`apps/backend`)          | API + services   | Operator / DevOps       | Server / cluster (Docker)                |
| PostgreSQL + Redis (`db`, `redis`)         | Datastores       | Operator / DevOps       | Server / cluster (Docker)                |
| Parent Dashboard (`apps/parent-dashboard`) | Web UI           | Operator (hosts), Parent (uses) | Browser on parent devices         |
| Parent Mobile (`@guardian/parent-mobile`)  | Mobile app       | Parent                  | Parent’s phone (Android / iOS)           |
| Child Mobile (`@guardian/child-mobile`)    | Mobile app       | Parent on child device  | Child’s phone                            |
| Agent RN (`@guardian/agent-react-native`)  | Shared agent implementation (library) | N/A (embedded via child app) | Bundled inside `@guardian/child-mobile` on child devices |
| Browser Extension (`apps/browser-extension`)| Browser plugin  | Parent on child browser | Child’s desktop/laptop browser           |
| Desktop Agent Plugin (`apps/agent-desktop`)| Rust plugin      | Operator / IT           | Child’s desktop/laptop via DCMAAR agent  |

### 5.2 High-Level Onboarding Flow

1. **Operator deploys backend + dashboard**
   - Build Guardian components.
   - Deploy with `pnpm deploy:prod` or `make deploy-build ENV=production`.
   - Verify health checks and dashboard availability.

2. **Parent signs in to Guardian**
   - Uses the **Parent Dashboard** (web) or **Parent Mobile** app.

3. **Parent registers children and devices**
   - Creates child profiles.
   - Registers child devices (mobile, desktop) and browsers.

4. **Parent installs child-side components**
   - Installs Child Mobile app and/or Guardian Agent RN app on each child device.
   - Installs Browser Extension on child browsers.
   - (Operator) ensures Desktop Agent Plugin is present in DCMAAR desktop agent deployments.

5. **Policy configuration and enforcement**
   - Parent configures policies via dashboard or mobile.
   - Agents and extensions enforce policies and send telemetry to Guardian backend.

---

## 6. Recommended Operator Workflow

1. **Prepare infrastructure**
   - Provision server(s) or containers.
   - Configure DNS and TLS termination for dashboard and backend.

2. **Configure environment**
   - Copy `.env.example` → `.env` and set secure values.

3. **Build Guardian**
   - `make build-prod` (or `pnpm build`) from Guardian product root.

4. **Deploy with Docker Compose**
   - `pnpm deploy:prod` or `make deploy-build ENV=production COMPONENT=full-stack`.

5. **Verify deployment**
   - Check `docker compose ps`, logs, `/health` endpoint, and dashboard access.

6. **Roll out client components**
   - Distribute mobile apps and desktop agents via your normal app-store/MDM/desktop deployment pipelines.
   - Provide browser extension install instructions for supported browsers.

7. **Hand off to parents**
   - Provide parents with dashboard URL and app store links.
   - Document how to add children and devices, install child agents, and review usage.

---

## 7. References

- **Build system**: `docs/LOCAL_DEVELOPMENT_SETUP.md`, `Makefile`, `scripts/build.sh`.
- **Backend operations**: `apps/backend/docs/operations/OPERATIONS.md`.
- **Architecture**: `docs/GUARDIAN_ARCHITECTURE_AND_CONTRACTS.md`, `docs/DESIGN_ARCHITECTURE.md`.
- **Usage docs**: `docs/usage/USER_MANUAL.md`, `docs/usage/TECHNICAL_REFERENCE.md`.

This deployment guide complements those documents by focusing specifically on **how to deploy and where each component goes (parent vs child)**.
