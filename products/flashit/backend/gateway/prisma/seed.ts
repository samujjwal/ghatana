import { PrismaClient } from '../generated/prisma/index.js';
import { Pool } from 'pg';
import { PrismaPg } from '@prisma/adapter-pg';
import bcrypt from 'bcrypt';

const connectionString = process.env.DATABASE_URL;
if (!connectionString) {
  throw new Error('DATABASE_URL environment variable is not set');
}

const pool = new Pool({ connectionString });
const adapter = new PrismaPg(pool);
const prisma = new PrismaClient({ adapter });

function writeLine(message = ''): void {
  process.stdout.write(`${message}\n`);
}

function writeError(message = ''): void {
  process.stderr.write(`${message}\n`);
}

const USERS = [
  { email: 'user@example.com', name: 'Test User', password: 'user123' },
  { email: 'alice@example.com', name: 'Alice Anderson', password: 'alice123!' },
  { email: 'bob@example.com', name: 'Bob Builder', password: 'bob123!' },
  { email: 'charlie@example.com', name: 'Charlie Chen', password: 'charlie123!' },
  { email: 'diana@example.com', name: 'Diana Davis', password: 'diana123!' },
  { email: 'eve@example.com', name: 'Eve Evans', password: 'eve123!' },
];

const SPHERE_TYPES = {
  PERSONAL: { type: 'PERSONAL', visibility: 'PRIVATE' },
  WORK: { type: 'WORK', visibility: 'PRIVATE' },
  FAMILY: { type: 'FAMILY', visibility: 'INVITE_ONLY' },
  HOBBY: { type: 'HOBBY', visibility: 'PUBLIC' },
};

const EMOTIONS = ['Joy', 'Sadness', 'Anger', 'Fear', 'Surprise', 'Disgust', 'Trust', 'Anticipation', 'Calm', 'Excitement'];
const TAGS = ['#work', '#family', '#ideas', '#reflection', '#goals', '#health', '#learning', '#travel', '#food', '#project-x'];
const CONTENT_TYPES = ['TEXT', 'VOICE', 'IMAGE', 'MIXED'];

const SAMPLE_TEXTS = [
  'Just had a great idea for the new project architecture.',
  'Feeling a bit overwhelmed with the deadline coming up.',
  'Had a wonderful dinner with the family tonight.',
  'Need to remember to buy milk and eggs.',
  'The sunset was absolutely beautiful today.',
  'Finished reading that book about cognitive science. Highly recommend.',
  'Meeting with the client went better than expected.',
  'Why is it so hard to center a div in CSS?',
  'Going for a run in the morning was a good decision.',
  'Thinking about where to go for the next vacation.',
  "Reflecting on the past year and what I've achieved.",
  'The coffee at the new place is amazing.',
  'Need to schedule a dentist appointment.',
  'Working on the new feature for the app.',
  'Saw a really cool movie last night.',
  'Feeling grateful for my friends.',
  'Struggling with this bug for hours.',
  'Finally fixed the issue! Such a relief.',
  'Planning a surprise party for mom.',
  'Learning a new programming language is fun but challenging.',
];

function getRandomItem<T>(array: T[]): T {
  return array[Math.floor(Math.random() * array.length)];
}

function getRandomItems<T>(array: T[], count: number): T[] {
  const shuffled = [...array].sort(() => 0.5 - Math.random());
  return shuffled.slice(0, count);
}

function getRandomDate(start: Date, end: Date): Date {
  return new Date(start.getTime() + Math.random() * (end.getTime() - start.getTime()));
}

function getRandomInt(min: number, max: number): number {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

async function main(): Promise<void> {
  writeLine('Starting seed...');

  const users = [];

  for (const userData of USERS) {
    const passwordHash = await bcrypt.hash(userData.password, 10);

    const user = await prisma.user.upsert({
      where: { email: userData.email },
      update: {
        displayName: userData.name,
        passwordHash,
      },
      create: {
        email: userData.email,
        displayName: userData.name,
        passwordHash,
      },
    });
    users.push(user);
    writeLine(`User created: ${user.email} (Password: ${userData.password})`);
  }

  const [alice, bob, charlie, diana, eve] = users;

  const spheres = [];

  spheres.push(await createSphere(alice.id, 'Personal', 'My private thoughts', SPHERE_TYPES.PERSONAL));
  spheres.push(await createSphere(alice.id, 'Work', 'Professional notes', SPHERE_TYPES.WORK));
  const aliceFamilySphere = await createSphere(alice.id, 'Family', 'Family memories', SPHERE_TYPES.FAMILY);
  spheres.push(aliceFamilySphere);

  spheres.push(await createSphere(bob.id, 'Personal', "Bob's world", SPHERE_TYPES.PERSONAL));
  const bobHobbiesSphere = await createSphere(bob.id, 'Hobbies', 'Woodworking and hiking', SPHERE_TYPES.HOBBY);
  spheres.push(bobHobbiesSphere);

  spheres.push(await createSphere(charlie.id, 'Personal', "Charlie's notes", SPHERE_TYPES.PERSONAL));
  spheres.push(await createSphere(diana.id, 'Personal', "Diana's diary", SPHERE_TYPES.PERSONAL));
  spheres.push(await createSphere(eve.id, 'Personal', "Eve's journal", SPHERE_TYPES.PERSONAL));

  writeLine(`Created/upserted ${spheres.length} spheres`);

  await prisma.sphereAccess.upsert({
    where: {
      sphereId_userId: {
        sphereId: aliceFamilySphere.id,
        userId: bob.id,
      },
    },
    update: {},
    create: {
      sphereId: aliceFamilySphere.id,
      userId: bob.id,
      role: 'VIEWER',
      grantedBy: alice.id,
    },
  });
  writeLine("Granted Bob access to Alice's Family sphere");

  writeLine('Generating moments...');

  const endDate = new Date();
  const startDate = new Date();
  startDate.setDate(startDate.getDate() - 60);

  let totalMoments = 0;

  for (const sphere of spheres) {
    let momentCount = 10;
    if (sphere.userId === alice.id) momentCount = 40;
    if (sphere.userId === bob.id) momentCount = 25;

    for (let i = 0; i < momentCount; i++) {
      const capturedAt = getRandomDate(startDate, endDate);
      const contentType = getRandomItem(CONTENT_TYPES);
      const text = getRandomItem(SAMPLE_TEXTS);
      const contentText = `${text} (${i + 1})`;

      await prisma.moment.create({
        data: {
          userId: sphere.userId,
          sphereId: sphere.id,
          contentText,
          contentTranscript: contentType === 'VOICE' || contentType === 'MIXED' ? 'Transcript of the voice note...' : null,
          contentType,
          emotions: getRandomItems(EMOTIONS, getRandomInt(1, 3)),
          tags: getRandomItems(TAGS, getRandomInt(1, 4)),
          sentimentScore: (Math.random() * 2 - 1).toFixed(2),
          importance: getRandomInt(1, 5),
          capturedAt,
          ingestedAt: capturedAt,
        },
      });
      totalMoments++;
    }
  }

  writeLine();
  writeLine('Seed completed successfully.');
  writeLine();
  writeLine('Test Users:');
  writeLine('-'.repeat(60));
  for (const userData of USERS) {
    writeLine(`  Email:    ${userData.email}`);
    writeLine(`  Password: ${userData.password}`);
    writeLine('-'.repeat(60));
  }
  writeLine(`Successfully seeded ${totalMoments} moments across ${spheres.length} spheres.`);
}

async function createSphere(userId: string, name: string, description: string, typeConfig: { type: string, visibility: string }) {
  const existing = await prisma.sphere.findFirst({
    where: { userId, name },
  });

  if (existing) return existing;

  return prisma.sphere.create({
    data: {
      userId,
      name,
      description,
      type: typeConfig.type,
      visibility: typeConfig.visibility,
      sphereAccess: {
        create: {
          userId,
          role: 'OWNER',
          grantedBy: userId,
        },
      },
    },
  });
}

main()
  .catch((error: unknown) => {
    writeError(error instanceof Error && error.stack ? error.stack : String(error));
    process.exit(1);
  })
  .finally(async () => {
    await prisma.$disconnect();
    await pool.end();
  });
