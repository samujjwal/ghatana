# Database Setup Fix - YAPPC API

## Problem
The API server starts but returns errors because database tables don't exist:
- `401 Unauthorized` on `/api/workspaces`
- `500 Error` with "The table `public.Workspace` does not exist"

## Solution

### Option 1: Apply Migrations (Recommended)

Run the migration script from the API directory:

```bash
cd products/yappc/app-creator/apps/api
npx tsx apply-migrations.mjs
```

Or manually:

```bash
cd products/yappc/app-creator/apps/api
npx prisma migrate deploy --schema=prisma/schema.prisma
npx prisma generate --schema=prisma/schema.prisma
```

### Option 2: Quick DB Push (Dev Only)

For local development, push the schema without migrations:

```bash
cd products/yappc/app-creator/apps/api
npx prisma db push --schema=prisma/schema.prisma
```

### Option 3: Fresh Migration (If starting from scratch)

```bash
cd products/yappc/app-creator/apps/api
npx prisma migrate dev --schema=prisma/schema.prisma --name init
```

## Verification

After applying migrations:

1. **Check tables exist:**
   ```bash
   PGPASSWORD=ghatana123 psql -h localhost -U ghatana -d yappc_dev -c "\dt"
   ```

2. **Test API:**
   ```bash
   curl http://localhost:7003/api/workspaces
   ```

3. **Seed sample data (optional):**
   ```bash
   cd products/yappc/app-creator/apps/api
   npx tsx prisma/seed.ts
   ```

## What Changed

✅ **devAuth.ts** - Added fallback for missing DB tables  
✅ **apply-migrations.mjs** - Automated migration script  
✅ Enhanced logging to debug auth issues  

## Restart Servers

After migrations:

```bash
# Terminal 1 - API
cd products/yappc/app-creator/apps/api
npx tsx watch src/index.ts

# Terminal 2 - Web
cd products/yappc/app-creator/apps/web
pnpm dev
```

## Troubleshooting

### Still getting 401 errors?
Check that `devAuth` is setting `request.user`:
- Look for log: `[DevAuth] Set request.user: dev-xxxxx`
- Verify `NODE_ENV !== 'production'` (it should be `development`)

### Migration fails?
1. Check DATABASE_URL in `.env`
2. Verify PostgreSQL is running: `pg_isready`
3. Check DB exists: `psql -l | grep yappc`

### Need to reset DB?
```bash
npx prisma migrate reset --schema=prisma/schema.prisma
```

## Environment Variables

Ensure `apps/api/.env` has:
```env
DATABASE_URL="postgresql://ghatana:ghatana123@localhost:5432/yappc_dev?schema=public"
PORT=7003
NODE_ENV=development
```

## Next Steps

Once migrations are applied and servers restarted:

1. Navigate to http://localhost:7002/app/workspaces
2. You should see the workspaces page (may be empty initially)
3. Create a workspace using the "New Workspace" button
4. Explore the canvas at `/app/w/:workspaceId/p/:projectId/canvas`

---

**Status:** Database schema exists, tables need to be created via migration.  
**Quick Fix:** Run `npx tsx apply-migrations.mjs` from `apps/api/`
