/**
 * Test script to verify seed functionality
 */
import { PrismaClient } from '../generated/prisma/index.js';

const prisma = new PrismaClient();

async function testSeed() {
  try {
    console.log('Testing Prisma connection...');
    
    // Check if users exist
    const userCount = await prisma.user.count();
    console.log(`✅ Found ${userCount} users in database`);
    
    // List users
    const users = await prisma.user.findMany({
      select: { email: true, displayName: true },
      take: 5,
    });
    
    console.log('\n📋 Sample Users:');
    users.forEach((user) => {
      console.log(`  - ${user.email} (${user.displayName})`);
    });
    
    // Count spheres
    const sphereCount = await prisma.sphere.count();
    console.log(`\n✅ Found ${sphereCount} spheres`);
    
    // Count moments
    const momentCount = await prisma.moment.count();
    console.log(`✅ Found ${momentCount} moments`);
    
    console.log('\n✨ Seed verification complete!');
    
  } catch (error) {
    console.error('❌ Error:', error);
    process.exit(1);
  } finally {
    await prisma.$disconnect();
  }
}

testSeed();
