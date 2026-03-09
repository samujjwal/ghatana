#!/usr/bin/env node

/**
 * Initialize the TutorPutor database
 * Creates the database directory and logs instructions
 */

import path from 'path';
import { fileURLToPath } from 'url';
import { existsSync, mkdirSync } from 'fs';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const dbPath = path.resolve(__dirname, 'prisma', 'dev.db');
const dbDir = path.dirname(dbPath);

console.log('[TutorPutor DB] Initializing database...');
console.log('[TutorPutor DB] Database path:', dbPath);

try {
  // Create directory if it doesn't exist
  if (!existsSync(dbDir)) {
    mkdirSync(dbDir, { recursive: true });
    console.log('[TutorPutor DB] Created database directory');
  }

  // Database will be created by Prisma on first query
  // With Prisma 7.x and libsql, the schema is synced dynamically
  console.log('[TutorPutor DB] Database directory ready');
  console.log('[TutorPutor DB] ');
  console.log('[TutorPutor DB] To sync the schema to the database, run:');
  console.log('[TutorPutor DB]   cd services/tutorputor-db');
  console.log('[TutorPutor DB]   npx prisma db push --accept-data-loss');
  console.log('[TutorPutor DB] ');
  console.log('[TutorPutor DB] Or use in-memory fallback (no database sync needed)');
  
  process.exit(0);
} catch (error) {
  console.error('[TutorPutor DB] Failed to initialize database directory');
  console.error(error instanceof Error ? error.message : error);
  process.exit(1);
}
