import { Pool } from 'pg';
import dotenv from 'dotenv';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Try loading from local .env
dotenv.config();

// If not found, try loading from root .env
if (!process.env.DATABASE_URL) {
  const rootEnvPath = path.resolve(__dirname, '../../../../../../.env');
  console.log(`Loading .env from ${rootEnvPath}`);
  dotenv.config({ path: rootEnvPath });
}

const pool = new Pool({ connectionString: process.env.DATABASE_URL });

async function main() {
  try {
    console.log('Dropping schema public...');
    await pool.query('DROP SCHEMA IF EXISTS public CASCADE;');
    await pool.query('CREATE SCHEMA public;');
    console.log('Schema public recreated.');
  } catch (e) {
    console.error('Error dropping objects:', e);
  } finally {
    await pool.end();
  }
}

main();