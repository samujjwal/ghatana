import { createPrismaClient, seedBaseData, DEFAULT_TENANT_ID, DEFAULT_USER_ID } from "./index.js";

/**
 * Domain content loader integration.
 * Conditionally loads domain content if the domain-loader package is available.
 */
async function loadDomainContent(prisma: any, tenantId: string, userId: string): Promise<void> {
  try {
    // Dynamic import to avoid hard dependency
    // @ts-expect-error - Optional package that may not be installed
    const domainLoader = await import("@ghatana/tutorputor-domain-loader");

    console.log("Loading domain content...");

    const contentDir = process.env.DOMAIN_CONTENT_DIR;

    const result = await domainLoader.loadDomainContent(prisma, {
      tenantId,
      domain: "all",
      contentDir,
      verbose: true,
      skipManifests: false,
      skipLearningPaths: false,
      skipModules: false,
    });

    if (result.success) {
      console.log(`✅ Domain content loaded successfully!`);
      console.log(`   - Concepts: ${result.stats.conceptsLoaded}`);
      console.log(`   - Modules: ${result.stats.modulesCreated}`);
      console.log(`   - Prerequisites: ${result.stats.prerequisiteLinks}`);
      console.log(`   - Learning paths: ${result.stats.learningPathsCreated}`);
      console.log(`   - Duration: ${result.durationMs}ms`);
    } else {
      console.warn("⚠️ Domain content load completed with errors:");
      result.errors.forEach((err: string) => console.warn(`   - ${err}`));
    }

    if (result.warnings.length > 0) {
      console.log(`   Warnings: ${result.warnings.length}`);
    }
  } catch (error) {
    if ((error as any)?.code === "ERR_MODULE_NOT_FOUND" ||
      (error as any)?.message?.includes("Cannot find module")) {
      console.log("ℹ️ Domain loader not available. Skipping domain content seeding.");
      console.log("   To enable, install: pnpm add @ghatana/tutorputor-domain-loader");
    } else {
      console.warn("⚠️ Failed to load domain content:", error);
    }
  }
}

interface SeedOptions {
  profile?: "base" | "demo";

  /** Skip base data seeding */
  skipBaseData?: boolean;

  /** Skip domain content loading */
  skipDomainContent?: boolean;

  /** Tenant ID */
  tenantId?: string;

  /** User ID */
  userId?: string;
}

async function main(options: SeedOptions = {}) {
  const profile = options.profile ?? "base";

  if (profile === "demo") {
    const { seedAdminDemoData } = await import("../prisma/seed-admin.js");
    await seedAdminDemoData();
    return;
  }

  const prisma = createPrismaClient();
  const tenantId = options.tenantId ?? process.env.TUTORPUTOR_DEFAULT_TENANT_ID ?? DEFAULT_TENANT_ID;
  const userId = options.userId ?? DEFAULT_USER_ID;

  try {
    // Seed base data
    if (!options.skipBaseData) {
      await seedBaseData(prisma, { tenantId, seedUserId: userId });
      console.log("✅ Base data seeded.");
    }

    // Load domain content
    if (!options.skipDomainContent) {
      await loadDomainContent(prisma, tenantId, userId);
    }

    console.log("✅ TutorPutor database seeding complete.");
  } finally {
    await prisma.$disconnect();
  }
}

// Parse CLI args
const args = process.argv.slice(2);
const options: SeedOptions = {
  profile: (args.find(a => a.startsWith("--profile="))?.split("=")[1] as SeedOptions["profile"]) ?? (process.env.TUTORPUTOR_SEED_PROFILE as SeedOptions["profile"]) ?? "base",
  skipBaseData: args.includes("--skip-base"),
  skipDomainContent: args.includes("--skip-domain"),
  tenantId: args.find(a => a.startsWith("--tenant="))?.split("=")[1],
  userId: args.find(a => a.startsWith("--user="))?.split("=")[1],
};

main(options).catch((error) => {
  console.error("Failed to seed TutorPutor database", error);
  process.exit(1);
});
