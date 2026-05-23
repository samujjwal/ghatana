import { z } from "zod";

/**
 * Language-specific configuration schemas
 */
const JavaConfigSchema = z.object({
  gradleProjectPath: z.string().optional(),
  mavenProjectPath: z.string().optional(),
  javaVersion: z.string().regex(/^\d+$/).optional(),
}).strict();

const TypeScriptConfigSchema = z.object({
  tsconfigPath: z.string().optional(),
  packageManager: z.enum(["pnpm", "npm", "yarn"]).optional(),
  framework: z.enum(["react", "vue", "angular", "solid", "svelte", "none"]).optional(),
}).strict();

const RustConfigSchema = z.object({
  cargoTomlPath: z.string().optional(),
  workspace: z.boolean().optional(),
  targetTriple: z.string().optional(),
  binaryName: z.string().optional(),
}).strict();

const PythonConfigSchema = z.object({
  pyprojectPath: z.string().optional(),
  venvStrategy: z.enum(["venv", "conda", "poetry", "uv", "none"]).optional(),
  packageManager: z.enum(["pip", "poetry", "uv"]).optional(),
}).strict();

const KotlinConfigSchema = z.object({
  gradleProjectPath: z.string().optional(),
  kotlinVersion: z.string().optional(),
}).strict();

const SwiftConfigSchema = z.object({
  xcodeProjectPath: z.string().optional(),
  swiftVersion: z.string().optional(),
}).strict();

/**
 * Valid language/runtime/buildSystem combinations
 * Using ProductUnitSurface enum values for consistency
 */
const VALID_COMBINATIONS = [
  { language: "java", runtime: "java-jre", buildSystem: "gradle" },
  { language: "java", runtime: "java-jdk", buildSystem: "gradle" },
  { language: "java", runtime: "java-jre", buildSystem: "maven" },
  { language: "java", runtime: "java-jdk", buildSystem: "maven" },
  { language: "typescript", runtime: "nodejs", buildSystem: "pnpm" },
  { language: "typescript", runtime: "nodejs-bun", buildSystem: "pnpm" },
  { language: "typescript", runtime: "nodejs", buildSystem: "npm" },
  { language: "typescript", runtime: "nodejs", buildSystem: "yarn" },
  { language: "typescript", runtime: "browser", buildSystem: "pnpm" },
  { language: "typescript", runtime: "browser", buildSystem: "npm" },
  { language: "typescript", runtime: "browser", buildSystem: "yarn" },
  { language: "javascript", runtime: "nodejs", buildSystem: "pnpm" },
  { language: "javascript", runtime: "nodejs", buildSystem: "npm" },
  { language: "javascript", runtime: "nodejs", buildSystem: "yarn" },
  { language: "rust", runtime: "rust-native", buildSystem: "cargo" },
  { language: "rust", runtime: "rust-wasm", buildSystem: "cargo" },
  { language: "python", runtime: "python", buildSystem: "poetry" },
  { language: "python", runtime: "python-uv", buildSystem: "poetry" },
  { language: "python", runtime: "python", buildSystem: "pip" },
  { language: "python", runtime: "python-uv", buildSystem: "pip" },
  { language: "kotlin", runtime: "kotlin-jvm", buildSystem: "gradle" },
  { language: "kotlin", runtime: "kotlin-jvm", buildSystem: "maven" },
  { language: "kotlin", runtime: "kotlin-native", buildSystem: "gradle" },
  { language: "swift", runtime: "mobile-ios", buildSystem: "xcode" },
  { language: "swift", runtime: "swift", buildSystem: "xcode" },
  { language: "go", runtime: "go", buildSystem: "none" },
  { language: "go", runtime: "cli-native", buildSystem: "none" },
  { language: "other", runtime: "other", buildSystem: "other" },
] as const;

/**
 * Zod schema for ProductSurface validation with strict typing
 * Using ProductUnitSurface enum values for consistency
 */
export const ProductSurfaceSchema = z
  .object({
    type: z.enum(["backend-api", "web", "worker", "operator", "mobile-ios", "mobile-android", "sdk", "domain-pack"]),
    adapter: z.string().min(1),
    path: z.string().min(1),
    implementationStatus: z.enum(["implemented", "planned", "backend-only"]).optional(),
    language: z.enum(["java", "typescript", "javascript", "rust", "python", "swift", "kotlin", "go", "other"]).optional(),
    runtime: z.enum(["java-jre", "java-jdk", "nodejs", "nodejs-bun", "python", "python-uv", "rust-native", "rust-wasm", "go", "swift", "kotlin-jvm", "kotlin-native", "docker-container", "docker-compose", "browser", "mobile-ios", "mobile-android", "cli-native", "none", "other"]).optional(),
    buildSystem: z.enum(["gradle", "maven", "pnpm", "npm", "yarn", "cargo", "poetry", "pip", "xcode", "buck", "bazel", "docker", "compose", "none", "other"]).optional(),
    packagePath: z.string().optional(),
    javaConfig: JavaConfigSchema.optional(),
    typescriptConfig: TypeScriptConfigSchema.optional(),
    rustConfig: RustConfigSchema.optional(),
    pythonConfig: PythonConfigSchema.optional(),
    kotlinConfig: KotlinConfigSchema.optional(),
    swiftConfig: SwiftConfigSchema.optional(),
  })
  .strict()
  .refine(
    (data) => {
      // If language, runtime, and buildSystem are all specified, validate combination
      if (data.language && data.runtime && data.buildSystem) {
        return VALID_COMBINATIONS.some(
          (combo) =>
            combo.language === data.language &&
            combo.runtime === data.runtime &&
            combo.buildSystem === data.buildSystem,
        );
      }
      return true;
    },
    {
      message: "Invalid language/runtime/buildSystem combination",
      path: ["language"],
    },
  )
  .refine(
    (data) => {
      // First, reject any config when no language is declared
      if (!data.language) {
        return (
          !data.javaConfig &&
          !data.typescriptConfig &&
          !data.rustConfig &&
          !data.pythonConfig &&
          !data.kotlinConfig &&
          !data.swiftConfig
        );
      }

      // Reject mismatched config - if language is declared, only matching config is allowed
      if (data.language !== "java" && data.javaConfig) return false;
      if (data.language !== "typescript" && data.typescriptConfig) return false;
      if (data.language !== "rust" && data.rustConfig) return false;
      if (data.language !== "python" && data.pythonConfig) return false;
      if (data.language !== "kotlin" && data.kotlinConfig) return false;
      if (data.language !== "swift" && data.swiftConfig) return false;

      // All other cases are valid (matching config or no config)
      return true;
    },
    {
      message: "Language-specific config must match declared language",
      path: ["language"],
    },
  )

export type ProductSurfaceInput = z.infer<typeof ProductSurfaceSchema>;

/**
 * Get valid combinations for a given language
 */
export function getValidCombinationsForLanguage(language: string): Array<{ runtime: string; buildSystem: string }> {
  return VALID_COMBINATIONS.filter((combo) => combo.language === language).map((combo) => ({
    runtime: combo.runtime,
    buildSystem: combo.buildSystem,
  }));
}

/**
 * Get recovery guidance for invalid combinations
 */
export function getCombinationRecoveryGuidance(language: string, runtime: string, buildSystem: string): string {
  const validCombos = getValidCombinationsForLanguage(language);
  if (validCombos.length === 0) {
    return `Language '${language}' has no valid combinations. Please use a supported language: ${VALID_COMBINATIONS.map((c) => c.language).join(", ")}`;
  }
  const currentCombo = validCombos.find((c) => c.runtime === runtime && c.buildSystem === buildSystem);
  if (currentCombo) {
    return `Combination '${language}/${runtime}/${buildSystem}' is actually valid. Check other validation errors.`;
  }
  return `Invalid combination for '${language}' (runtime: ${runtime}, buildSystem: ${buildSystem}). Valid combinations are:\n${validCombos.map((c) => `  - runtime: ${c.runtime}, buildSystem: ${c.buildSystem}`).join("\n")}`;
}
