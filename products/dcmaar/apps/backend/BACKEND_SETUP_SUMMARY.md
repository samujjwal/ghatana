# ✅ Guardian Backend Setup - Database Configuration Complete

## What Was Done

Your `.env` file has been updated with working database credentials:

- **Database**: `guardian_db`
- **User**: `guardian`
- **Password**: `guardian123`
- **Host**: `localhost`
- **Port**: `5432`

Created setup documents and scripts:

1. `DATABASE_SETUP.md` - Complete setup guide with 3 options
2. `setup-db.sh` - Automated setup script

## Quick Start (Choose One)

### Option 1: Setup PostgreSQL Locally (Recommended)

If you have PostgreSQL installed locally:

```bash
cd /home/samujjwal/Developments/ghatana/products/dcmaar/apps/guardian/apps/backend

# Create the database and user
sudo -u postgres psql << 'EOF'
CREATE ROLE guardian WITH LOGIN ENCRYPTED PASSWORD 'guardian123';
ALTER ROLE guardian CREATEDB;
CREATE DATABASE guardian_db OWNER guardian;
GRANT CREATE ON DATABASE guardian_db TO guardian;
EOF

# Apply the database schema
psql -h localhost -U guardian -d guardian_db -f src/db/schema.sql

# Start the backend server
pnpm dev
```

### Option 2: Setup with Docker

If you have Docker installed:

```bash
cd /home/samujjwal/Developments/ghatana/products/dcmaar/apps/guardian/apps/backend

# Use the automated setup script
chmod +x setup-db.sh
./setup-db.sh

# Or manually with docker-compose
# First, create docker-compose.yml using content from DATABASE_SETUP.md
# Then:
docker-compose up -d postgres
docker-compose logs -f postgres  # Wait for ready message

# Once ready, start the backend
pnpm dev
```

### Option 3: Use the Main Ghatana Docker Setup

If the main docker-compose is already running:

```bash
cd /home/samujjwal/Developments/ghatana

# Start main services
docker-compose up -d postgres redis

# Wait for PostgreSQL to be ready, then start Guardian backend
cd products/dcmaar/apps/guardian/apps/backend
pnpm dev
```

## Next: Start the Backend Server

Once the database is set up:

```bash
cd /home/samujjwal/Developments/ghatana/products/dcmaar/apps/guardian/apps/backend
pnpm install  # If not already done
pnpm dev
```

You should see:

```
✓ Database pool created
✓ Server listening on http://localhost:3001
```

## Verify Setup

In another terminal:

```bash
# Test the API health endpoint
curl http://localhost:3001/health

# Should return:
# {"status":"ok","timestamp":"2024-01-XX..."}
```

## Then: Start the Frontend

In yet another terminal:

```bash
cd /home/samujjwal/Developments/ghatana/products/dcmaar/apps/guardian/apps/parent-dashboard
pnpm dev
```

## Summary of Changes Made

### Files Modified:

1. **`.env`** - Updated database credentials to use `guardian123` password
   - Changed: `DB_PASSWORD=your_secure_database_password_min_16_chars` → `DB_PASSWORD=guardian123`
   - Now matches: `postgresql://guardian:guardian123@localhost:5432/guardian_db`

### Files Created:

1. **`DATABASE_SETUP.md`** - Complete setup guide with 3 options and troubleshooting
2. **`setup-db.sh`** - Automated database initialization script
3. **`BACKEND_SETUP_SUMMARY.md`** - This file

## Troubleshooting

### Error: "password authentication failed"

→ PostgreSQL user/database not created yet. Run Option 1 or 2 above.

### Error: "database guardian_db does not exist"

→ Schema not applied. Run: `psql -h localhost -U guardian -d guardian_db -f src/db/schema.sql`

### Error: "connection refused"

→ PostgreSQL not running. Check:

- Local: `sudo systemctl status postgresql`
- Docker: `docker-compose ps` or check logs

### Cannot connect as root/sudo in terminal?

→ This is normal. Use the `guardian` user credentials instead:

```bash
psql -h localhost -U guardian -d guardian_db
```

## Full Application Stack

Once everything is running:

1. **Frontend** (React/Vite): http://localhost:5173
2. **Backend API** (Fastify): http://localhost:3001
3. **Database** (PostgreSQL): localhost:5432/guardian_db

You should be able to register, login, and use the Guardian parental control application!
