import Ajv from 'ajv';
import { promises as fs } from 'node:fs';
import * as path from 'node:path';

interface ValidatorFunction {
  (data: unknown): boolean;
  errors?: Array<{ message?: string }>;
}

/**
 * JSON Schema validator for configuration files
 */
export class SchemaValidator {
  private readonly ajv: Ajv;
  private readonly repoRoot: string;

  constructor(repoRoot: string = process.cwd()) {
    this.repoRoot = repoRoot;
    this.ajv = new Ajv({ strict: false });
  }

  /**
   * Validate a file against a schema
   */
  async validateFile(filePath: string, schemaPath: string): Promise<{ valid: boolean; errors: Array<{ path: string; message: string }> }> {
    try {
      // Load schema
      const schemaContent = await fs.readFile(schemaPath, 'utf-8');
      const schema = JSON.parse(schemaContent) as Record<string, unknown>;

      // Load data file
      const dataContent = await fs.readFile(filePath, 'utf-8');
      const data = JSON.parse(dataContent);

      // Validate
      const validator = this.ajv.compile(schema) as ValidatorFunction;
      const valid = validator(data);

      if (!valid && validator.errors) {
        const errors = validator.errors.map((error) => ({
          path: 'root',
          message: error.message || 'Validation failed',
        }));
        return { valid: false, errors };
      }

      return { valid: true, errors: [] };
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      return {
        valid: false,
        errors: [{ path: 'root', message: `Failed to validate: ${message}` }],
      };
    }
  }

  /**
   * Validate product lifecycle profiles
   */
  async validateProductLifecycleProfiles(): Promise<{ valid: boolean; errors: Array<{ path: string; message: string }> }> {
    const profilesPath = path.join(this.repoRoot, 'config', 'product-lifecycle-profiles.json');
    const schemaPath = path.join(this.repoRoot, 'config', 'product-lifecycle-profiles-schema.json');

    try {
      await fs.access(profilesPath);
    } catch {
      return {
        valid: false,
        errors: [{ path: 'product-lifecycle-profiles.json', message: 'File not found' }],
      };
    }

    return this.validateFile(profilesPath, schemaPath);
  }

  /**
   * Validate toolchain adapter registry
   */
  async validateToolchainAdapterRegistry(): Promise<{ valid: boolean; errors: Array<{ path: string; message: string }> }> {
    const registryPath = path.join(this.repoRoot, 'config', 'toolchain-adapter-registry.json');
    const schemaPath = path.join(this.repoRoot, 'config', 'toolchain-adapter-registry-schema.json');

    try {
      await fs.access(registryPath);
    } catch {
      return {
        valid: false,
        errors: [{ path: 'toolchain-adapter-registry.json', message: 'File not found' }],
      };
    }

    return this.validateFile(registryPath, schemaPath);
  }

  /**
   * Validate product lifecycle profiles with artifact type checks
   */
  async validateProductLifecycleProfilesWithArtifacts(): Promise<{
    valid: boolean;
    errors: Array<{ path: string; message: string }>;
    warnings: Array<{ path: string; message: string }>;
  }> {
    const profilesResult = await this.validateProductLifecycleProfiles();
    const warnings: Array<{ path: string; message: string }> = [];

    if (!profilesResult.valid) {
      return { ...profilesResult, warnings };
    }

    // Load profiles to check artifact types
    try {
      const profilesPath = path.join(this.repoRoot, 'config', 'product-lifecycle-profiles.json');
      const content = await fs.readFile(profilesPath, 'utf-8');
      const profiles = JSON.parse(content) as Record<string, unknown>;

      // Validate that all referenced artifact types are known
      const validArtifactTypes = [
        'jar',
        'war',
        'static-web-bundle',
        'docker-image',
        'npm-package',
        'test-report',
        'coverage-report',
        'source-map',
        'documentation',
      ];

      const profilesObj = profiles.profiles as Record<string, unknown> | undefined;
      if (profilesObj) {
        for (const [profileId, profile] of Object.entries(profilesObj)) {
          if (typeof profile === 'object' && profile !== null && 'defaultArtifacts' in profile) {
            const artifacts = (profile as Record<string, unknown>).defaultArtifacts as string[] | undefined;
            if (Array.isArray(artifacts)) {
              for (const artifact of artifacts) {
                if (!validArtifactTypes.includes(artifact)) {
                  warnings.push({
                    path: `profiles.${profileId}.defaultArtifacts`,
                    message: `Unknown artifact type: ${artifact}`,
                  });
                }
              }
            }
          }
        }
      }
    } catch (error) {
      // Silently continue if we can't load for additional validation
    }

    return { valid: profilesResult.valid, errors: profilesResult.errors, warnings };
  }
}
