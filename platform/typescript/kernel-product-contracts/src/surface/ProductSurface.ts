import { z } from "zod";

export const ProductSurfaceTypeSchema = z.enum([
  'backend-api',
  'web',
  'worker',
  'operator',
  'mobile-ios',
  'mobile-android',
  'sdk',
  'domain-pack',
]);

export const ProductLanguageSchema = z.enum([
  'java',
  'typescript',
  'javascript',
  'rust',
  'python',
  'swift',
  'kotlin',
  'go',
  'other',
]);

export const ProductRuntimeSchema = z.enum([
  'java-jre',
  'java-jdk',
  'nodejs',
  'nodejs-bun',
  'python',
  'python-uv',
  'rust-native',
  'rust-wasm',
  'go',
  'swift',
  'kotlin-jvm',
  'kotlin-native',
  'docker-container',
  'docker-compose',
  'browser',
  'mobile-ios',
  'mobile-android',
  'cli-native',
  'none',
  'other',
]);

export const ProductBuildSystemSchema = z.enum([
  'gradle',
  'maven',
  'pnpm',
  'npm',
  'yarn',
  'cargo',
  'poetry',
  'pip',
  'xcode',
  'buck',
  'bazel',
  'docker',
  'compose',
  'none',
  'other',
]);

export type ProductSurfaceType = z.infer<typeof ProductSurfaceTypeSchema>;
export type ProductLanguage = z.infer<typeof ProductLanguageSchema>;
export type ProductRuntime = z.infer<typeof ProductRuntimeSchema>;
export type ProductBuildSystem = z.infer<typeof ProductBuildSystemSchema>;

/**
 * Java-specific configuration.
 */
export interface JavaConfig {
  readonly gradleProjectPath?: string;
  readonly mavenProjectPath?: string;
  readonly javaVersion?: string;
}

/**
 * TypeScript-specific configuration.
 */
export interface TypeScriptConfig {
  readonly tsconfigPath?: string;
  readonly packageManager?: 'pnpm' | 'npm' | 'yarn';
  readonly framework?: 'react' | 'vue' | 'angular' | 'solid' | 'svelte' | 'none';
}

/**
 * Rust-specific configuration.
 */
export interface RustConfig {
  readonly cargoTomlPath?: string;
  readonly workspace?: boolean;
  readonly targetTriple?: string;
  readonly binaryName?: string;
}

/**
 * Python-specific configuration.
 */
export interface PythonConfig {
  readonly pyprojectPath?: string;
  readonly venvStrategy?: 'venv' | 'conda' | 'poetry' | 'uv' | 'none';
  readonly packageManager?: 'pip' | 'poetry' | 'uv';
}

/**
 * Kotlin-specific configuration.
 */
export interface KotlinConfig {
  readonly gradleProjectPath?: string;
  readonly kotlinVersion?: string;
}

/**
 * Swift-specific configuration.
 */
export interface SwiftConfig {
  readonly xcodeProjectPath?: string;
  readonly swiftVersion?: string;
}

export const JavaConfigSchema = z
  .object({
    gradleProjectPath: z.string().trim().min(1).optional(),
    mavenProjectPath: z.string().trim().min(1).optional(),
    javaVersion: z.string().trim().min(1).optional(),
  })
  .strict();

export const TypeScriptConfigSchema = z
  .object({
    tsconfigPath: z.string().trim().min(1).optional(),
    packageManager: z.enum(['pnpm', 'npm', 'yarn']).optional(),
    framework: z.enum(['react', 'vue', 'angular', 'solid', 'svelte', 'none']).optional(),
  })
  .strict();

export const RustConfigSchema = z
  .object({
    cargoTomlPath: z.string().trim().min(1).optional(),
    workspace: z.boolean().optional(),
    targetTriple: z.string().trim().min(1).optional(),
    binaryName: z.string().trim().min(1).optional(),
  })
  .strict();

export const PythonConfigSchema = z
  .object({
    pyprojectPath: z.string().trim().min(1).optional(),
    venvStrategy: z.enum(['venv', 'conda', 'poetry', 'uv', 'none']).optional(),
    packageManager: z.enum(['pip', 'poetry', 'uv']).optional(),
  })
  .strict();

export const KotlinConfigSchema = z
  .object({
    gradleProjectPath: z.string().trim().min(1).optional(),
    kotlinVersion: z.string().trim().min(1).optional(),
  })
  .strict();

export const SwiftConfigSchema = z
  .object({
    xcodeProjectPath: z.string().trim().min(1).optional(),
    swiftVersion: z.string().trim().min(1).optional(),
  })
  .strict();

export const ProductSurfaceSchema = z
  .object({
    type: ProductSurfaceTypeSchema,
    adapter: z.string().trim().min(1),
    path: z.string().trim().min(1),
    implementationStatus: z.enum(['implemented', 'planned', 'backend-only']).optional(),
    language: ProductLanguageSchema.optional(),
    runtime: ProductRuntimeSchema.optional(),
    buildSystem: ProductBuildSystemSchema.optional(),
    packagePath: z.string().trim().min(1).optional(),
    javaConfig: JavaConfigSchema.optional(),
    typescriptConfig: TypeScriptConfigSchema.optional(),
    rustConfig: RustConfigSchema.optional(),
    pythonConfig: PythonConfigSchema.optional(),
    kotlinConfig: KotlinConfigSchema.optional(),
    swiftConfig: SwiftConfigSchema.optional(),
  })
  .strict();

export interface ProductSurface {
  readonly type: ProductSurfaceType;
  readonly adapter: string;
  readonly path: string;
  readonly implementationStatus?: 'implemented' | 'planned' | 'backend-only';
  readonly language?: ProductLanguage;
  readonly runtime?: ProductRuntime;
  readonly buildSystem?: ProductBuildSystem;
  readonly packagePath?: string;
  readonly javaConfig?: JavaConfig;
  readonly typescriptConfig?: TypeScriptConfig;
  readonly rustConfig?: RustConfig;
  readonly pythonConfig?: PythonConfig;
  readonly kotlinConfig?: KotlinConfig;
  readonly swiftConfig?: SwiftConfig;
}

export function validateProductSurfaceType(value: unknown): value is ProductSurfaceType {
  return ProductSurfaceTypeSchema.safeParse(value).success;
}

export function validateProductLanguage(value: unknown): value is ProductLanguage {
  return ProductLanguageSchema.safeParse(value).success;
}

export function validateProductRuntime(value: unknown): value is ProductRuntime {
  return ProductRuntimeSchema.safeParse(value).success;
}

export function validateProductBuildSystem(value: unknown): value is ProductBuildSystem {
  return ProductBuildSystemSchema.safeParse(value).success;
}

export function validateJavaConfig(value: unknown): value is JavaConfig {
  return JavaConfigSchema.safeParse(value).success;
}

export function validateTypeScriptConfig(value: unknown): value is TypeScriptConfig {
  return TypeScriptConfigSchema.safeParse(value).success;
}

export function validateRustConfig(value: unknown): value is RustConfig {
  return RustConfigSchema.safeParse(value).success;
}

export function validatePythonConfig(value: unknown): value is PythonConfig {
  return PythonConfigSchema.safeParse(value).success;
}

export function validateKotlinConfig(value: unknown): value is KotlinConfig {
  return KotlinConfigSchema.safeParse(value).success;
}

export function validateSwiftConfig(value: unknown): value is SwiftConfig {
  return SwiftConfigSchema.safeParse(value).success;
}

export function validateProductSurface(value: unknown): value is ProductSurface {
  return ProductSurfaceSchema.safeParse(value).success;
}
