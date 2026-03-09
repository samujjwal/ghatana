# Guardian Backend Database Setup Guide

## Option 1: Use PostgreSQL Locally (Recommended for Development)

### Prerequisites

- PostgreSQL 11+ installed locally
- psql command-line client

### Step 1: Create the database user

```bash
sudo -u postgres psql
```

Then in the PostgreSQL prompt:

```sql
-- Create the guardian user with password
CREATE ROLE guardian WITH LOGIN ENCRYPTED PASSWORD 'guardian123';
ALTER ROLE guardian CREATEDB;

-- Create the database
CREATE DATABASE guardian_db OWNER guardian;

-- Grant privileges
GRANT CREATE ON DATABASE guardian_db TO guardian;

\q
```

### Step 2: Apply the schema

```bash
cd /home/samujjwal/Developments/ghatana/products/dcmaar/apps/guardian/apps/backend

# Test connection first
psql -h localhost -U guardian -d guardian_db -c "SELECT 1"

# Apply schema
psql -h localhost -U guardian -d guardian_db -f src/db/schema.sql
```

### Step 3: Verify connection

```bash
psql -h localhost -U guardian -d guardian_db -c "\dt"
```

Should see the tables: users, refresh_tokens, children, devices, policies, block_events, usage_sessions, etc.

---

## Option 2: Use Docker (If you have Docker installed)

### Step 1: Create a docker-compose.yml for Guardian backend only

Create: `/home/samujjwal/Developments/ghatana/products/dcmaar/apps/guardian/apps/backend/docker-compose.yml`

```yaml
version: "3.8"

services:
  postgres:
    image: postgres:15-alpine
    container_name: guardian-postgres
    environment:
      POSTGRES_USER: guardian
      POSTGRES_PASSWORD: guardian123
      POSTGRES_DB: guardian_db
    ports:
      - "5432:5432"
    volumes:
      - guardian_postgres_data:/var/lib/postgresql/data
      - ./src/db/schema.sql:/docker-entrypoint-initdb.d/01-schema.sql
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U guardian"]
      interval: 10s
      timeout: 5s
      retries: 5

volumes:
  guardian_postgres_data:
```

### Step 2: Start the database container

```bash
cd /home/samujjwal/Developments/ghatana/products/dcmaar/apps/guardian/apps/backend
docker-compose up -d postgres
```

### Step 3: Verify it's running

```bash
docker-compose ps
docker-compose logs postgres
```

---

## Option 3: Use the Main Ghatana Docker Compose (Not Recommended for Guardian)

The main docker-compose has PostgreSQL but uses different credentials:

- Database: `requirements_ai`
- User: `ghatana`
- Password: From `DB_PASSWORD` env var

If you want to use it, modify your Guardian `.env`:

```env
DATABASE_URL=postgresql://ghatana:ghatana123@localhost:5432/requirements_ai
DB_HOST=localhost
DB_PORT=5432
DB_NAME=requirements_ai
DB_USER=ghatana
DB_PASSWORD=ghatana123
```

---

## Verify Your Setup

### Test the connection from the backend

```bash
cd /home/samujjwal/Developments/ghatana/products/dcmaar/apps/guardian/apps/backend

# Check that .env is configured
cat .env | grep DATABASE_URL

# Try to start the backend server
npm run dev  # or pnpm dev
```

You should see:

```
✓ Database connected successfully
✓ Server listening on http://localhost:3001
```

### Test with psql directly

```bash
psql -h localhost -U guardian -d guardian_db -c "SELECT * FROM users LIMIT 1"
```

---

## Troubleshooting

### Error: "password authentication failed for user guardian"

**Causes:**

1. PostgreSQL user doesn't exist
2. Password in `.env` doesn't match database password
3. PostgreSQL not running
4. Wrong host/port

**Fix:**

1. Verify PostgreSQL is running: `sudo systemctl status postgresql` (or `docker-compose ps`)
2. Check `.env` credentials match database
3. Recreate the user: See Option 1, Step 1
4. Verify connectivity: `psql -h localhost -U guardian -d guardian_db`

### Error: "database guardian_db does not exist"

**Fix:**

1. Apply the schema: `psql -h localhost -U guardian -f src/db/schema.sql`
2. Or use docker-compose which auto-applies schema on startup

### Error: "role guardian does not exist"

**Fix:**
Create the user: See Option 1, Step 1

---

## Quick Start (Copy & Paste)

```bash
# Navigate to backend
cd /home/samujjwal/Developments/ghatana/products/dcmaar/apps/guardian/apps/backend

# Option A: Setup locally with PostgreSQL
chmod +x setup-db.sh
./setup-db.sh

# Option B: Setup with Docker (create docker-compose.yml first, then:)
docker-compose up -d postgres
docker-compose logs -f postgres  # Wait for "database system is ready"

# Start the backend
npm run dev  # or pnpm dev

# Test the API
curl http://localhost:3001/health
```

---

## Next Steps

Once database is set up and backend is running:

1. Run database migrations (if any): `npm run migrate`
2. Seed test data: `npm run seed`
3. Run tests: `npm test`
4. Start frontend: `cd ../../../parent-dashboard && pnpm dev`
