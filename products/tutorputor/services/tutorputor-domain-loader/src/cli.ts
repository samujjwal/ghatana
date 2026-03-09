#!/usr/bin/env node
/**
 * Domain Loader CLI
 *
 * Command-line interface for loading domain content into the database.
 *
 * @doc.type module
 * @doc.purpose CLI for domain content loading
 * @doc.layer product
 * @doc.pattern CLI
 */

import { Command } from "commander";
import { createPrismaClient } from "./prisma-utils.js";
import { resolve } from "path";
import { dirname } from "path";
import { fileURLToPath } from "url";
import { loadDomainContent, validateDomainContent } from "./loaders/domain-loader";
import { parsePhysicsJSON } from "./parsers/physics-parser";
import { parseChemistryJSON } from "./parsers/chemistry-parser";
import { generateModulesFromConcepts } from "./generators/module-generator";
import { generateLearningPaths } from "./generators/learning-path-generator";
import { generateContentBlocks } from "./generators/content-block-generator";
import { generateManifestsFromConcepts } from "./generators/manifest-generator";
import { generateSimulationTemplates } from "./generators/simulation-template-generator";
import { readFileSync } from "fs";

/**
 * CLI options for load command.
 */
interface LoadOptions {
  tenant: string;
  domain: "physics" | "chemistry" | "all";
  contentDir?: string;
  user: string;
  dryRun: boolean;
  skipModules: boolean;
  skipPaths: boolean;
  withContentBlocks: boolean;
  withManifests: boolean;
  verbose: boolean;
}

/**
 * CLI options for validate command.
 */
interface ValidateOptions {
  contentDir?: string;
  domain: "physics" | "chemistry" | "all";
  verbose: boolean;
}

/**
 * CLI options for stats command.
 */
interface StatsOptions {
  contentDir?: string;
}

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

function getTutorputorRootDir(): string {
  return resolve(__dirname, "../../..");
}

const program = new Command();

program
  .name("tutorputor-domain-loader")
  .description("Load domain content (physics, chemistry, etc.) into TutorPutor database")
  .version("0.1.0");

// Load command
program
  .command("load")
  .description("Load domain content from JSON files into database")
  .option("-t, --tenant <tenantId>", "Tenant ID", "default")
  .option("-d, --domain <domain>", "Domain to load (physics, chemistry, all)", "all")
  .option("-c, --content-dir <path>", "Path to domain content directory")
  .option("-u, --user <userId>", "User ID for author attribution", "system")
  .option("--dry-run", "Validate only, don't persist", false)
  .option("--skip-modules", "Skip module generation", false)
  .option("--skip-paths", "Skip learning path generation", false)
  .option("--with-content-blocks", "Generate content blocks for modules", false)
  .option("--with-manifests", "Generate simulation manifests", false)
  .option("-v, --verbose", "Verbose output", false)
  .action(async (opts: unknown) => {
    const options = opts as LoadOptions;
    const prisma = createPrismaClient();

    try {
      console.log("🚀 Starting domain content load...\n");

      const contentDir = options.contentDir
        ? resolve(options.contentDir)
        : resolve(getTutorputorRootDir(), "content", "domains");

      console.log(`📁 Content directory: ${contentDir}`);
      console.log(`🏢 Tenant: ${options.tenant}`);
      console.log(`📚 Domain: ${options.domain}`);
      console.log(`${options.dryRun ? "🔍 DRY RUN - no changes will be made\n" : ""}`);

      // Step 1: Load concepts
      console.log("📥 Loading concepts...");
      const result = await loadDomainContent(prisma, {
        tenantId: options.tenant,
        domain: options.domain,
        contentDir,
        dryRun: options.dryRun,
        verbose: options.verbose,
      });

      console.log(`\n✅ Concepts loaded: ${result.stats.conceptsLoaded}`);
      console.log(`   By domain:`);
      for (const [domain, count] of Object.entries(result.stats.conceptsByDomain)) {
        console.log(`     - ${domain}: ${count}`);
      }
      console.log(`   By level:`);
      for (const [level, count] of Object.entries(result.stats.conceptsByLevel)) {
        if (count > 0) console.log(`     - ${level}: ${count}`);
      }
      console.log(`   Prerequisites linked: ${result.stats.prerequisiteLinks}`);
      console.log(`   Cross-domain links: ${result.stats.crossDomainLinks}`);

      if (options.dryRun) {
        console.log("\n🔍 Dry run complete. No changes were made.");
        printWarningsAndErrors(result.warnings, result.errors);
        return;
      }

      // Step 2: Generate modules
      if (!options.skipModules) {
        console.log("\n📦 Generating modules...");

        // Re-parse to get concepts for module generation
        const allConcepts = [];

        if (options.domain === "physics" || options.domain === "all") {
          const physicsPath = resolve(contentDir, "physics.json");
          const physicsData = JSON.parse(readFileSync(physicsPath, "utf-8"));
          allConcepts.push(...parsePhysicsJSON(physicsData));
        }

        if (options.domain === "chemistry" || options.domain === "all") {
          const chemistryPath = resolve(contentDir, "chemistry.json");
          const chemistryData = JSON.parse(readFileSync(chemistryPath, "utf-8"));
          allConcepts.push(...parseChemistryJSON(chemistryData));
        }

        const moduleResult = await generateModulesFromConcepts(prisma, allConcepts, {
          tenantId: options.tenant,
          authorId: options.user,
          skipExisting: true,
          verbose: options.verbose,
        });

        console.log(`   Modules created: ${moduleResult.modulesCreated}`);
        console.log(`   Modules skipped: ${moduleResult.modulesSkipped}`);

        // Step 3: Generate content blocks for modules
        if (options.withContentBlocks) {
          console.log("\n📝 Generating content blocks...");

          let blocksCreated = 0;
          for (const concept of allConcepts) {
            const mapping = moduleResult.mappings.find((m) => m.conceptId === concept.id);
            if (mapping) {
              const blockResult = await generateContentBlocks(prisma, mapping.moduleId, concept, {
                includeSimulation: true,
                includeAiTutor: true,
                includeExercise: true,
                includeCompetencies: true,
                verbose: options.verbose,
              });
              blocksCreated += blockResult.blocksCreated;
            }
          }

          console.log(`   Content blocks created: ${blocksCreated}`);
        }

        // Step 4: Generate simulation manifests
        if (options.withManifests) {
          console.log("\n🎬 Generating simulation manifests and templates...");

          const manifestResults = generateManifestsFromConcepts(allConcepts, {
            tenantId: options.tenant,
            authorId: options.user,
            placeholderSteps: true,
            verbose: options.verbose,
          });

          console.log(`   Manifests generated (in-memory): ${manifestResults.size}`);

          // Group by template type
          const templateCounts = new Map<string, number>();
          for (const [, result] of manifestResults) {
            const type = result.templateType;
            templateCounts.set(type, (templateCounts.get(type) ?? 0) + 1);
          }

          if (options.verbose) {
            console.log("   By template:");
            for (const [type, count] of templateCounts) {
              console.log(`     - ${type}: ${count}`);
            }
          }

          // Persist manifests and create SimulationTemplate rows
          const templateResult = await generateSimulationTemplates(
            prisma,
            allConcepts,
            manifestResults,
            moduleResult.mappings,
            {
              tenantId: options.tenant,
              authorId: options.user,
              verbose: options.verbose,
            }
          );

          console.log(`   Manifests persisted: ${templateResult.manifestsCreated}`);
          console.log(`   Templates created/updated: ${templateResult.templatesCreated}`);

          if (templateResult.warnings.length > 0 && options.verbose) {
            console.log("   Template warnings:");
            for (const warning of templateResult.warnings.slice(0, 10)) {
              console.log(`     - ${warning}`);
            }
            if (templateResult.warnings.length > 10) {
              console.log(
                `     ... and ${templateResult.warnings.length - 10} more warnings`
              );
            }
          }

          if (templateResult.errors.length > 0) {
            console.log("   Template errors:");
            for (const error of templateResult.errors) {
              console.log(`     - ${error}`);
            }
          }
        }

        // Step 5: Generate learning paths
        if (!options.skipPaths) {
          console.log("\n🛤️  Generating learning paths...");

          const pathResult = await generateLearningPaths(prisma, allConcepts, moduleResult.mappings, {
            tenantId: options.tenant,
            userId: options.user,
            createCrossLevelPaths: true,
            verbose: options.verbose,
          });

          console.log(`   Paths created: ${pathResult.pathsCreated}`);
        }
      }

      console.log(`\n✅ Load complete in ${result.durationMs}ms`);
      printWarningsAndErrors(result.warnings, result.errors);
    } catch (error) {
      console.error("❌ Error:", error instanceof Error ? error.message : String(error));
      process.exit(1);
    } finally {
      await prisma.$disconnect();
    }
  });

// Validate command
program
  .command("validate")
  .description("Validate domain content without loading")
  .option("-d, --domain <domain>", "Domain to validate (physics, chemistry, all)", "all")
  .option("-c, --content-dir <path>", "Path to domain content directory")
  .option("-v, --verbose", "Verbose output", false)
  .action(async (opts: unknown) => {
    const options = opts as ValidateOptions;
    try {
      console.log("🔍 Validating domain content...\n");

      const contentDir = options.contentDir
        ? resolve(options.contentDir)
        : resolve(getTutorputorRootDir(), "content", "domains");

      const result = await validateDomainContent({
        tenantId: "validation",
        domain: options.domain,
        contentDir,
        verbose: options.verbose,
      });

      console.log(`📊 Validation Results:`);
      console.log(`   Concepts validated: ${result.conceptCount}`);
      console.log(`   Valid: ${result.valid ? "✅ Yes" : "❌ No"}`);

      if (result.errors.length > 0) {
        console.log(`\n❌ Errors (${result.errors.length}):`);
        for (const error of result.errors) {
          console.log(`   - [${error.code}] ${error.conceptId ? `${error.conceptId}: ` : ""}${error.message}`);
        }
      }

      if (result.warnings.length > 0) {
        console.log(`\n⚠️  Warnings (${result.warnings.length}):`);
        for (const warning of result.warnings.slice(0, 20)) {
          console.log(`   - [${warning.code}] ${warning.conceptId ? `${warning.conceptId}: ` : ""}${warning.message}`);
        }
        if (result.warnings.length > 20) {
          console.log(`   ... and ${result.warnings.length - 20} more warnings`);
        }
      }

      process.exit(result.valid ? 0 : 1);
    } catch (error) {
      console.error("❌ Error:", error instanceof Error ? error.message : String(error));
      process.exit(1);
    }
  });

// Stats command
program
  .command("stats")
  .description("Show statistics about domain content")
  .option("-c, --content-dir <path>", "Path to domain content directory")
  .action(async (opts: unknown) => {
    const options = opts as StatsOptions;
    try {
      const contentDir = options.contentDir
        ? resolve(options.contentDir)
        : resolve(getTutorputorRootDir(), "content", "domains");

      console.log("📊 Domain Content Statistics\n");

      // Load and analyze each domain
      const domains = ["physics", "chemistry"];

      for (const domain of domains) {
        try {
          const filePath = resolve(contentDir, `${domain}.json`);
          const data = JSON.parse(readFileSync(filePath, "utf-8"));

          const concepts =
            domain === "physics" ? parsePhysicsJSON(data) : parseChemistryJSON(data);

          const levels = new Map<string, number>();
          const simTypes = new Map<string, number>();
          let totalPrereqs = 0;
          let totalObjectives = 0;

          for (const c of concepts) {
            levels.set(c.level, (levels.get(c.level) ?? 0) + 1);
            simTypes.set(
              c.simulationMetadata.simulationType,
              (simTypes.get(c.simulationMetadata.simulationType) ?? 0) + 1
            );
            totalPrereqs += c.prerequisites.length;
            totalObjectives += c.pedagogicalMetadata.learningObjectives.length;
          }

          console.log(`📚 ${domain.charAt(0).toUpperCase() + domain.slice(1)}:`);
          console.log(`   Total concepts: ${concepts.length}`);
          console.log(`   By level:`);
          for (const [level, count] of levels) {
            console.log(`     - ${level}: ${count}`);
          }
          console.log(`   Simulation types:`);
          for (const [type, count] of simTypes) {
            console.log(`     - ${type}: ${count}`);
          }
          console.log(`   Total prerequisites: ${totalPrereqs}`);
          console.log(`   Total learning objectives: ${totalObjectives}`);
          console.log();
        } catch {
          console.log(`   ${domain}: Not found or invalid`);
        }
      }
    } catch (error) {
      console.error("❌ Error:", error instanceof Error ? error.message : String(error));
      process.exit(1);
    }
  });

function printWarningsAndErrors(warnings: string[], errors: string[]): void {
  if (errors.length > 0) {
    console.log(`\n❌ Errors (${errors.length}):`);
    for (const error of errors) {
      console.log(`   - ${error}`);
    }
  }

  if (warnings.length > 0) {
    console.log(`\n⚠️  Warnings (${warnings.length}):`);
    for (const warning of warnings.slice(0, 10)) {
      console.log(`   - ${warning}`);
    }
    if (warnings.length > 10) {
      console.log(`   ... and ${warnings.length - 10} more warnings`);
    }
  }
}

program.parse();
