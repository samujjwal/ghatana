import { describe, it, expect } from 'vitest';
import { ProductSurfaceSchema, getValidCombinationsForLanguage, getCombinationRecoveryGuidance } from '../ProductSurfaceSchema.js';

describe('ProductSurfaceSchema', () => {
  describe('valid surface configurations', () => {
    it('accepts minimal valid surface', () => {
      const result = ProductSurfaceSchema.safeParse({
        type: 'backend-api',
        adapter: 'GradleJavaServiceAdapter',
        path: 'products/example/backend-api',
      });
      expect(result.success).toBe(true);
    });

    it('accepts Java with java-jre and Gradle', () => {
      const result = ProductSurfaceSchema.safeParse({
        type: 'backend-api',
        adapter: 'GradleJavaServiceAdapter',
        path: 'products/example/backend-api',
        language: 'java',
        runtime: 'java-jre',
        buildSystem: 'gradle',
      });
      expect(result.success).toBe(true);
    });

    it('accepts Java with java-jdk and Maven', () => {
      const result = ProductSurfaceSchema.safeParse({
        type: 'backend-api',
        adapter: 'MavenJavaServiceAdapter',
        path: 'products/example/backend-api',
        language: 'java',
        runtime: 'java-jdk',
        buildSystem: 'maven',
      });
      expect(result.success).toBe(true);
    });

    it('accepts TypeScript with nodejs and pnpm', () => {
      const result = ProductSurfaceSchema.safeParse({
        type: 'web',
        adapter: 'PnpmViteReactAdapter',
        path: 'products/example/web',
        language: 'typescript',
        runtime: 'nodejs',
        buildSystem: 'pnpm',
      });
      expect(result.success).toBe(true);
    });

    it('accepts TypeScript with browser and npm', () => {
      const result = ProductSurfaceSchema.safeParse({
        type: 'web',
        adapter: 'PnpmViteReactAdapter',
        path: 'products/example/web',
        language: 'typescript',
        runtime: 'browser',
        buildSystem: 'npm',
      });
      expect(result.success).toBe(true);
    });

    it('accepts Rust with rust-native and cargo', () => {
      const result = ProductSurfaceSchema.safeParse({
        type: 'worker',
        adapter: 'CargoRustAdapter',
        path: 'products/example/worker',
        language: 'rust',
        runtime: 'rust-native',
        buildSystem: 'cargo',
      });
      expect(result.success).toBe(true);
    });

    it('accepts Rust with rust-wasm and cargo', () => {
      const result = ProductSurfaceSchema.safeParse({
        type: 'worker',
        adapter: 'CargoRustAdapter',
        path: 'products/example/worker',
        language: 'rust',
        runtime: 'rust-wasm',
        buildSystem: 'cargo',
      });
      expect(result.success).toBe(true);
    });

    it('accepts Python with python and poetry', () => {
      const result = ProductSurfaceSchema.safeParse({
        type: 'worker',
        adapter: 'PythonPyprojectAdapter',
        path: 'products/example/worker',
        language: 'python',
        runtime: 'python',
        buildSystem: 'poetry',
      });
      expect(result.success).toBe(true);
    });

    it('accepts Kotlin with kotlin-jvm and Gradle', () => {
      const result = ProductSurfaceSchema.safeParse({
        type: 'mobile-android',
        adapter: 'GradleAndroidAdapter',
        path: 'products/example/mobile-android',
        language: 'kotlin',
        runtime: 'kotlin-jvm',
        buildSystem: 'gradle',
      });
      expect(result.success).toBe(true);
    });

    it('accepts Swift with mobile-ios and xcode', () => {
      const result = ProductSurfaceSchema.safeParse({
        type: 'mobile-ios',
        adapter: 'XcodeIosAdapter',
        path: 'products/example/mobile-ios',
        language: 'swift',
        runtime: 'mobile-ios',
        buildSystem: 'xcode',
      });
      expect(result.success).toBe(true);
    });
  });

  describe('language-specific config', () => {
    it('accepts Java config for Java language', () => {
      const result = ProductSurfaceSchema.safeParse({
        type: 'backend-api',
        adapter: 'GradleJavaServiceAdapter',
        path: 'products/example/backend-api',
        language: 'java',
        runtime: 'java-jre',
        buildSystem: 'gradle',
        javaConfig: {
          gradleProjectPath: 'backend-api/build.gradle.kts',
          javaVersion: '21',
        },
      });
      expect(result.success).toBe(true);
    });

    it('accepts TypeScript config for TypeScript language', () => {
      const result = ProductSurfaceSchema.safeParse({
        type: 'web',
        adapter: 'PnpmViteReactAdapter',
        path: 'products/example/web',
        language: 'typescript',
        runtime: 'browser',
        buildSystem: 'pnpm',
        typescriptConfig: {
          tsconfigPath: 'tsconfig.json',
          packageManager: 'pnpm',
          framework: 'react',
        },
      });
      expect(result.success).toBe(true);
    });

    it('accepts Rust config for Rust language', () => {
      const result = ProductSurfaceSchema.safeParse({
        type: 'worker',
        adapter: 'CargoRustAdapter',
        path: 'products/example/worker',
        language: 'rust',
        runtime: 'rust-native',
        buildSystem: 'cargo',
        rustConfig: {
          cargoTomlPath: 'Cargo.toml',
          workspace: true,
          targetTriple: 'x86_64-unknown-linux-gnu',
          binaryName: 'example-worker',
        },
      });
      expect(result.success).toBe(true);
    });

    it('accepts Python config for Python language', () => {
      const result = ProductSurfaceSchema.safeParse({
        type: 'worker',
        adapter: 'PythonPyprojectAdapter',
        path: 'products/example/worker',
        language: 'python',
        runtime: 'python',
        buildSystem: 'poetry',
        pythonConfig: {
          pyprojectPath: 'pyproject.toml',
          venvStrategy: 'poetry',
          packageManager: 'poetry',
        },
      });
      expect(result.success).toBe(true);
    });

    it('rejects Java config for non-Java language', () => {
      const result = ProductSurfaceSchema.safeParse({
        type: 'web',
        adapter: 'PnpmViteReactAdapter',
        path: 'products/example/web',
        language: 'typescript',
        runtime: 'browser',
        buildSystem: 'pnpm',
        javaConfig: {
          gradleProjectPath: 'build.gradle.kts',
        },
      });
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues.some((issue) => issue.message.includes('Language-specific config must match declared language'))).toBe(true);
      }
    });

    it('rejects TypeScript config for non-TypeScript language', () => {
      const result = ProductSurfaceSchema.safeParse({
        type: 'backend-api',
        adapter: 'GradleJavaServiceAdapter',
        path: 'products/example/backend-api',
        language: 'java',
        runtime: 'java-jre',
        buildSystem: 'gradle',
        typescriptConfig: {
          tsconfigPath: 'tsconfig.json',
        },
      });
      expect(result.success).toBe(false);
    });

    it('rejects extra fields in language-specific config (strict mode)', () => {
      const result = ProductSurfaceSchema.safeParse({
        type: 'backend-api',
        adapter: 'GradleJavaServiceAdapter',
        path: 'products/example/backend-api',
        language: 'java',
        runtime: 'java-jre',
        buildSystem: 'gradle',
        javaConfig: {
          gradleProjectPath: 'build.gradle.kts',
          extraField: 'not allowed',
        },
      });
      expect(result.success).toBe(false);
    });
  });

  describe('invalid language/runtime/buildSystem combinations', () => {
    it('rejects Java with browser runtime', () => {
      const result = ProductSurfaceSchema.safeParse({
        type: 'backend-api',
        adapter: 'GradleJavaServiceAdapter',
        path: 'products/example/backend-api',
        language: 'java',
        runtime: 'browser',
        buildSystem: 'gradle',
      });
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues.some((issue) => issue.message.includes('Invalid language/runtime/buildSystem combination'))).toBe(true);
      }
    });

    it('rejects TypeScript with java-jre runtime', () => {
      const result = ProductSurfaceSchema.safeParse({
        type: 'web',
        adapter: 'PnpmViteReactAdapter',
        path: 'products/example/web',
        language: 'typescript',
        runtime: 'java-jre',
        buildSystem: 'pnpm',
      });
      expect(result.success).toBe(false);
    });

    it('rejects Rust with nodejs runtime', () => {
      const result = ProductSurfaceSchema.safeParse({
        type: 'worker',
        adapter: 'CargoRustAdapter',
        path: 'products/example/worker',
        language: 'rust',
        runtime: 'nodejs',
        buildSystem: 'cargo',
      });
      expect(result.success).toBe(false);
    });

    it('rejects Python with cargo build system', () => {
      const result = ProductSurfaceSchema.safeParse({
        type: 'worker',
        adapter: 'PythonPyprojectAdapter',
        path: 'products/example/worker',
        language: 'python',
        runtime: 'python',
        buildSystem: 'cargo',
      });
      expect(result.success).toBe(false);
    });

    it('allows partial specification (only language)', () => {
      const result = ProductSurfaceSchema.safeParse({
        type: 'backend-api',
        adapter: 'GradleJavaServiceAdapter',
        path: 'products/example/backend-api',
        language: 'java',
      });
      expect(result.success).toBe(true);
    });

    it('allows partial specification (language and runtime)', () => {
      const result = ProductSurfaceSchema.safeParse({
        type: 'backend-api',
        adapter: 'GradleJavaServiceAdapter',
        path: 'products/example/backend-api',
        language: 'java',
        runtime: 'java-jre',
      });
      expect(result.success).toBe(true);
    });
  });

  describe('strict mode validation', () => {
    it('rejects extra fields not in schema', () => {
      const result = ProductSurfaceSchema.safeParse({
        type: 'backend-api',
        adapter: 'GradleJavaServiceAdapter',
        path: 'products/example/backend-api',
        customField: 'not allowed',
      });
      expect(result.success).toBe(false);
    });

    it('rejects empty adapter string', () => {
      const result = ProductSurfaceSchema.safeParse({
        type: 'backend-api',
        adapter: '',
        path: 'products/example/backend-api',
      });
      expect(result.success).toBe(false);
    });

    it('rejects empty path string', () => {
      const result = ProductSurfaceSchema.safeParse({
        type: 'backend-api',
        adapter: 'GradleJavaServiceAdapter',
        path: '',
      });
      expect(result.success).toBe(false);
    });

    it('rejects invalid surface type', () => {
      const result = ProductSurfaceSchema.safeParse({
        type: 'invalid-type' as any,
        adapter: 'GradleJavaServiceAdapter',
        path: 'products/example/backend-api',
      });
      expect(result.success).toBe(false);
    });

    it('rejects invalid language', () => {
      const result = ProductSurfaceSchema.safeParse({
        type: 'backend-api',
        adapter: 'GradleJavaServiceAdapter',
        path: 'products/example/backend-api',
        language: 'invalid-language' as any,
      });
      expect(result.success).toBe(false);
    });

    it('rejects invalid runtime', () => {
      const result = ProductSurfaceSchema.safeParse({
        type: 'backend-api',
        adapter: 'GradleJavaServiceAdapter',
        path: 'products/example/backend-api',
        runtime: 'invalid-runtime' as any,
      });
      expect(result.success).toBe(false);
    });

    it('rejects invalid build system', () => {
      const result = ProductSurfaceSchema.safeParse({
        type: 'backend-api',
        adapter: 'GradleJavaServiceAdapter',
        path: 'products/example/backend-api',
        buildSystem: 'invalid-build-system' as any,
      });
      expect(result.success).toBe(false);
    });
  });


  describe('helper functions', () => {
    describe('getValidCombinationsForLanguage', () => {
      it('returns valid combinations for Java', () => {
        const combos = getValidCombinationsForLanguage('java');
        expect(combos).toHaveLength(4);
        expect(combos).toContainEqual({ runtime: 'java-jre', buildSystem: 'gradle' });
        expect(combos).toContainEqual({ runtime: 'java-jdk', buildSystem: 'maven' });
      });

      it('returns valid combinations for TypeScript', () => {
        const combos = getValidCombinationsForLanguage('typescript');
        expect(combos.length).toBeGreaterThan(0);
        expect(combos.some((c) => c.runtime === 'nodejs' && c.buildSystem === 'pnpm')).toBe(true);
        expect(combos.some((c) => c.runtime === 'browser' && c.buildSystem === 'pnpm')).toBe(true);
      });

      it('returns valid combinations for Rust', () => {
        const combos = getValidCombinationsForLanguage('rust');
        expect(combos).toHaveLength(2);
        expect(combos).toContainEqual({ runtime: 'rust-native', buildSystem: 'cargo' });
        expect(combos).toContainEqual({ runtime: 'rust-wasm', buildSystem: 'cargo' });
      });

      it('returns empty array for unsupported language', () => {
        const combos = getValidCombinationsForLanguage('unsupported');
        expect(combos).toEqual([]);
      });
    });

    describe('getCombinationRecoveryGuidance', () => {
      it('provides guidance for invalid combination', () => {
        const guidance = getCombinationRecoveryGuidance('java', 'browser', 'gradle');
        expect(guidance).toContain('Invalid combination');
        expect(guidance).toContain('java');
        expect(guidance).toContain('runtime: browser');
        expect(guidance).toContain('buildSystem: gradle');
      });

      it('provides guidance for unsupported language', () => {
        const guidance = getCombinationRecoveryGuidance('unsupported', 'jvm', 'gradle');
        expect(guidance).toContain('no valid combinations');
        expect(guidance).toContain('supported language');
      });

      it('indicates valid combination is actually valid', () => {
        const guidance = getCombinationRecoveryGuidance('java', 'java-jre', 'gradle');
        expect(guidance).toContain('actually valid');
      });
    });
  });
});


