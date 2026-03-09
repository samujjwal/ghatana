/**
 * Apply Prisma Migrations Script
 * 
 * Run this to create/update database tables from the Prisma schema.
 * 
 * Usage:
 *   npx tsx apply-migrations.mjs
 */

import { execSync } from 'child_process';
import { readFileSync, existsSync } from 'fs';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

// Load .env
const envPath = join(__dirname, '.env');
if (existsSync(envPath)) {
    const envContent = readFileSync(envPath, 'utf-8');
    const lines = envContent.split('\n');
    for (const line of lines) {
        if (line.trim() && !line.startsWith('#')) {
            const [key, ...valueParts] = line.split('=');
            const value = valueParts.join('=').replace(/^["']|["']$/g, '');
            process.env[key.trim()] = value.trim();
        }
    }
    console.log('✅ Loaded .env file');
}

console.log('\n📦 Applying Prisma migrations to database...\n');

try {
    // Check migration status
    console.log('🔍 Checking migration status...');
    execSync('npx prisma migrate status --schema=prisma/schema.prisma', {
        stdio: 'inherit',
        cwd: __dirname
    });

    console.log('\n🚀 Applying migrations...');
    execSync('npx prisma migrate deploy --schema=prisma/schema.prisma', {
        stdio: 'inherit',
        cwd: __dirname
    });

    console.log('\n✅ Migrations applied successfully!');

    console.log('\n🌱 Generating Prisma Client...');
    execSync('npx prisma generate --schema=prisma/schema.prisma', {
        stdio: 'inherit',
        cwd: __dirname
    });

    console.log('\n✨ All done! Your database is ready.');
    console.log('\n💡 Tip: Run `npx tsx prisma/seed.ts` to seed with sample data.\n');

} catch (error) {
    console.error('\n❌ Error applying migrations:', error.message);
    console.log('\n💡 Try running manually:');
    console.log('   cd apps/api');
    console.log('   npx prisma migrate deploy --schema=prisma/schema.prisma');
    process.exit(1);
}
