/**
 * Test script to verify seed functionality.
 */
import { PrismaClient } from '../generated/prisma/index.js';

const prisma = new PrismaClient();

function writeLine(message = ''): void {
  process.stdout.write(`${message}\n`);
}

function writeError(message = ''): void {
  process.stderr.write(`${message}\n`);
}

async function testSeed(): Promise<void> {
  try {
    writeLine('Testing Prisma connection...');

    const userCount = await prisma.user.count();
    writeLine(`Found ${userCount} users in database`);

    const users = await prisma.user.findMany({
      select: { email: true, displayName: true },
      take: 5,
    });

    writeLine();
    writeLine('Sample Users:');
    users.forEach((user) => {
      writeLine(`  - ${user.email} (${user.displayName})`);
    });

    const sphereCount = await prisma.sphere.count();
    writeLine();
    writeLine(`Found ${sphereCount} spheres`);

    const momentCount = await prisma.moment.count();
    writeLine(`Found ${momentCount} moments`);

    writeLine();
    writeLine('Seed verification complete.');
  } catch (error) {
    writeError(`Error: ${error instanceof Error ? error.message : String(error)}`);
    process.exit(1);
  } finally {
    await prisma.$disconnect();
  }
}

void testSeed();
