import { defineConfig } from 'prisma/config';
import dotenv from 'dotenv';

// Load environment variables
dotenv.config();

export default defineConfig({
  schema: './prisma/schema.prisma',
  datasource: {
    url: process.env.DATABASE_URL || "postgresql://ghatana:ghatana123@localhost:5432/yappc_dev?schema=public"
  },
  // For Prisma 7.0+ migrations, we need to provide the database URL
  // The migrate commands will use this configuration
  migrations: {
    seed: 'npx tsx ./prisma/seed-minimal.ts',
  },
});