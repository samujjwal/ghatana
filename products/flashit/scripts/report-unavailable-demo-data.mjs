#!/usr/bin/env node
/**
 * FlashIt non-production demo data seeder.
 *
 * Creates realistic demo users, spheres, and moments using the gateway's
 * Prisma client.  ALL data is clearly tagged as demo-data and can be rolled
 * back via the `--command rollback` flag.
 *
 * Usage:
 *   node report-unavailable-demo-data.mjs --command seed-users
 *   node report-unavailable-demo-data.mjs --command seed-moments
 *   node report-unavailable-demo-data.mjs --command rollback
 *   node report-unavailable-demo-data.mjs --command status
 *
 * NOTE: This script targets a LOCAL development database only.  Never run
 * against a production database.  DATABASE_URL must not point to production.
 */

import { createRequire } from 'module';
import { argv, exit } from 'process';

// ---------------------------------------------------------------------------
// Guard: refuse to run against a production-looking DATABASE_URL
// ---------------------------------------------------------------------------

const dbUrl = process.env.DATABASE_URL ?? '';
const productionPatterns = [
  /\.prod\./i,
  /production/i,
  /\.rds\.amazonaws\.com/i,
  /\.supabase\.co/i,
  /neon\.tech/i,
];
if (productionPatterns.some((p) => p.test(dbUrl))) {
  console.error(
    '[demo-data] ERROR: DATABASE_URL looks like a production endpoint. Refusing to seed demo data.',
  );
  exit(1);
}

// ---------------------------------------------------------------------------
// Command parsing
// ---------------------------------------------------------------------------

const args = argv.slice(2);
const commandFlag = args.indexOf('--command');
const command = commandFlag !== -1 ? args[commandFlag + 1] : args[0] ?? 'help';

const DEMO_TAG = '[DEMO]';

// ---------------------------------------------------------------------------
// Lazy-load Prisma client from the gateway package
// ---------------------------------------------------------------------------

async function loadPrisma() {
  const require = createRequire(import.meta.url);
  // Resolve the generated Prisma client from the gateway workspace
  const { PrismaClient } = require('../backend/gateway/generated/prisma/index.js');
  return new PrismaClient({ log: [] });
}

// ---------------------------------------------------------------------------
// Seed functions
// ---------------------------------------------------------------------------

async function seedUsers(prisma) {
  const bcrypt = await import('bcryptjs');
  const demoPasswordHash = await bcrypt.hash('demo-password-not-real', 12);

  const users = [
    { email: 'alice.demo@flashit.invalid', displayName: 'Alice Demo' },
    { email: 'bob.demo@flashit.invalid', displayName: 'Bob Demo' },
    { email: 'carol.demo@flashit.invalid', displayName: 'Carol Demo' },
  ];

  let created = 0;
  let skipped = 0;
  for (const user of users) {
    const existing = await prisma.user.findUnique({ where: { email: user.email } });
    if (existing) {
      skipped++;
      continue;
    }
    await prisma.user.create({
      data: {
        email: user.email,
        passwordHash: demoPasswordHash,
        displayName: `${DEMO_TAG} ${user.displayName}`,
        emailVerified: true,
      },
    });
    created++;
  }
  console.log(`[demo-data] seed-users: created=${created} skipped=${skipped}`);
}

async function seedMoments(prisma) {
  const alice = await prisma.user.findUnique({
    where: { email: 'alice.demo@flashit.invalid' },
  });

  if (!alice) {
    console.error('[demo-data] seed-moments: run seed-users first (alice.demo not found)');
    exit(1);
  }

  // Create a demo sphere
  let sphere = await prisma.sphere.findFirst({
    where: { userId: alice.id, name: `${DEMO_TAG} Demo Sphere` },
  });
  if (!sphere) {
    sphere = await prisma.sphere.create({
      data: {
        userId: alice.id,
        name: `${DEMO_TAG} Demo Sphere`,
        description: 'Automatically created for demo purposes.',
        isPrivate: true,
      },
    });
  }

  // Create demo moments
  const momentTexts = [
    'Had a great idea about product design this morning.',
    'Reflections on the team retro — we really improved our feedback loops.',
    'Reading "Thinking, Fast and Slow" — the dual-process theory is fascinating.',
    'First run of the season completed! 5km in 28 minutes.',
    'Gratitude journal: thankful for my supportive colleagues.',
  ];

  let created = 0;
  let skipped = 0;
  for (const text of momentTexts) {
    const taggedContent = `${DEMO_TAG} ${text}`;
    const existing = await prisma.moment.findFirst({
      where: { userId: alice.id, content: taggedContent },
    });
    if (existing) {
      skipped++;
      continue;
    }
    await prisma.moment.create({
      data: {
        userId: alice.id,
        sphereId: sphere.id,
        content: taggedContent,
        tags: ['demo', 'seed'],
      },
    });
    created++;
  }
  console.log(`[demo-data] seed-moments: created=${created} skipped=${skipped}`);
}

async function rollback(prisma) {
  // Delete all records tagged with the demo prefix — work bottom-up by FK order

  const deletedMoments = await prisma.moment.deleteMany({
    where: { content: { startsWith: DEMO_TAG } },
  });
  const deletedSpheres = await prisma.sphere.deleteMany({
    where: { name: { startsWith: DEMO_TAG } },
  });
  const deletedUsers = await prisma.user.deleteMany({
    where: { email: { endsWith: '@flashit.invalid' } },
  });

  console.log(
    `[demo-data] rollback: deleted moments=${deletedMoments.count} spheres=${deletedSpheres.count} users=${deletedUsers.count}`,
  );
}

async function status(prisma) {
  const userCount = await prisma.user.count({
    where: { email: { endsWith: '@flashit.invalid' } },
  });
  const momentCount = await prisma.moment.count({
    where: { content: { startsWith: DEMO_TAG } },
  });
  console.log(`[demo-data] status: demo users=${userCount} demo moments=${momentCount}`);
}

// ---------------------------------------------------------------------------
// Main
// ---------------------------------------------------------------------------

async function main() {
  let prisma;
  try {
    prisma = await loadPrisma();
  } catch (err) {
    console.error('[demo-data] Failed to load Prisma client:', err.message);
    console.error(
      '[demo-data] Ensure you have run `pnpm --filter @flashit/web-api exec prisma generate` first.',
    );
    exit(1);
  }

  try {
    switch (command) {
      case 'seed-users':
        await seedUsers(prisma);
        break;
      case 'seed-moments':
        await seedMoments(prisma);
        break;
      case 'rollback':
        await rollback(prisma);
        break;
      case 'status':
        await status(prisma);
        break;
      case 'help':
      default:
        console.log(
          [
            'FlashIt demo data seeder.',
            '',
            'Usage:',
            '  node report-unavailable-demo-data.mjs --command <command>',
            '',
            'Commands:',
            '  seed-users    Create demo user accounts',
            '  seed-moments  Create demo moments (requires seed-users)',
            '  rollback      Delete all demo-tagged data',
            '  status        Show current demo data counts',
            '',
            'IMPORTANT: Only run against a local development database.',
          ].join('\n'),
        );
        break;
    }
  } finally {
    await prisma.$disconnect();
  }
}

main().catch((err) => {
  console.error('[demo-data] Unexpected error:', err);
  exit(1);
});

