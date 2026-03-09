
const { PrismaClient } = require('./src/generated/prisma');

const prisma = new PrismaClient();

async function main() {
  try {
    const result = await prisma.$queryRaw`
      SELECT indexname, indexdef
      FROM pg_indexes
      WHERE tablename = 'Workspace';
    `;
    console.log('Indexes on Workspace table:', result);

    // Also check constraints
    const constraints = await prisma.$queryRaw`
        SELECT conname, contype, pg_get_constraintdef(oid)
        FROM pg_constraint
        WHERE conrelid = 'Workspace'::regclass;
    `
    console.log('Constraints on Workspace table:', constraints);

  } catch (e) {
    console.error(e);
  } finally {
    await prisma.$disconnect();
  }
}

main();
