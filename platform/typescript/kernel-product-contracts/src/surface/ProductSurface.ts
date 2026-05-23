export type ProductSurfaceType =
  | 'backend-api'
  | 'web'
  | 'worker'
  | 'operator'
  | 'mobile-ios'
  | 'mobile-android'
  | 'sdk'
  | 'domain-pack';

/**
 * Supported programming languages for product surfaces
 * Aligned with ProductUnitSurface enum values
 */
export type ProductLanguage =
  | 'java'
  | 'typescript'
  | 'javascript'
  | 'rust'
  | 'python'
  | 'swift'
  | 'kotlin'
  | 'go'
  | 'other';

/**
 * Supported runtime environments for product surfaces
 * Aligned with ProductUnitSurface enum values
 */
export type ProductRuntime =
  | 'java-jre'
  | 'java-jdk'
  | 'nodejs'
  | 'nodejs-bun'
  | 'python'
  | 'python-uv'
  | 'rust-native'
  | 'rust-wasm'
  | 'go'
  | 'swift'
  | 'kotlin-jvm'
  | 'kotlin-native'
  | 'docker-container'
  | 'docker-compose'
  | 'browser'
  | 'mobile-ios'
  | 'mobile-android'
  | 'cli-native'
  | 'none'
  | 'other';

/**
 * Supported build systems for product surfaces
 * Aligned with ProductUnitSurface enum values
 */
export type ProductBuildSystem =
  | 'gradle'
  | 'maven'
  | 'pnpm'
  | 'npm'
  | 'yarn'
  | 'cargo'
  | 'poetry'
  | 'pip'
  | 'xcode'
  | 'buck'
  | 'bazel'
  | 'docker'
  | 'compose'
  | 'none'
  | 'other';

/**
 * Java-specific configuration
 */
export interface JavaConfig {
  readonly gradleProjectPath?: string;
  readonly mavenProjectPath?: string;
  readonly javaVersion?: string;
}

/**
 * TypeScript-specific configuration
 */
export interface TypeScriptConfig {
  readonly tsconfigPath?: string;
  readonly packageManager?: 'pnpm' | 'npm' | 'yarn';
  readonly framework?: 'react' | 'vue' | 'angular' | 'solid' | 'svelte' | 'none';
}

/**
 * Rust-specific configuration
 */
export interface RustConfig {
  readonly cargoTomlPath?: string;
  readonly workspace?: boolean;
  readonly targetTriple?: string;
  readonly binaryName?: string;
}

/**
 * Python-specific configuration
 */
export interface PythonConfig {
  readonly pyprojectPath?: string;
  readonly venvStrategy?: 'venv' | 'conda' | 'poetry' | 'uv' | 'none';
  readonly packageManager?: 'pip' | 'poetry' | 'uv';
}

/**
 * Kotlin-specific configuration
 */
export interface KotlinConfig {
  readonly gradleProjectPath?: string;
  readonly kotlinVersion?: string;
}

/**
 * Swift-specific configuration
 */
export interface SwiftConfig {
  readonly xcodeProjectPath?: string;
  readonly swiftVersion?: string;
}

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
