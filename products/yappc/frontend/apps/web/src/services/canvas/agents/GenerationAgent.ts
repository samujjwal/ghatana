/**
 * Generation Agent
 *
 * Thin client wrapper for Java Canvas AI Backend code generation service.
 * Delegates AI-powered code generation to Java backend via gRPC.
 *
 * @doc.type class
 * @doc.purpose Code generation from canvas design (thin client for Java backend)
 * @doc.layer product
 * @doc.pattern Agent, Proxy
 * @architecture Hybrid Backend - Node.js thin client -> Java gRPC service
 */

import type { CanvasState } from '../../../types/canvas';
import type { LifecyclePhase } from '../../../types/lifecycle';
import type { ValidationReport } from './ValidationAgent';
import { AgentExecutor } from './AgentExecutor';
import { GenerationAgentContract } from './AgentContract';
import {
  getCanvasAIService,
  isCanvasAIAvailable,
} from '../api/CanvasAIService';

// Reuse existing code generation infrastructure
import {
  generateCodeFromNode,
  generateCodeFromFlow,
  type CodeGenerationOptions,
  type CodeGenerationResult as LibCodeGenerationResult,
  type GeneratedFile,
} from '@ghatana/canvas';

// ============================================================================
// Types
// ============================================================================

/**
 * Generation request options
 */
export interface GenerationOptions {
  /** Target language/framework */
  language?: 'typescript' | 'java' | 'python' | 'go';
  framework?: string;

  /** Generation scope */
  includeTests?: boolean;
  includeDocumentation?: boolean;
  includeConfiguration?: boolean;

  /** Output options */
  outputFormat?: 'files' | 'zip' | 'inline';
  outputPath?: string;

  /** Validation context */
  validationReport?: ValidationReport;

  /** AI options */
  useAI?: boolean;
  aiModel?: string;
  temperature?: number;
}

/**
 * Generated artifact types
 */
export type ArtifactType =
  | 'source' // Source code files
  | 'test' // Test files
  | 'config' // Configuration files
  | 'documentation' // Documentation files
  | 'schema' // Database schemas
  | 'api' // API definitions
  | 'infrastructure'; // Infrastructure as code

/**
 * Generated artifact
 */
export interface GeneratedArtifact {
  id: string;
  type: ArtifactType;
  path: string;
  content: string;
  language: string;
  framework?: string;
  metadata?: Record<string, unknown>;
}

/**
 * Code generation result
 */
export interface CodeGenerationResult {
  success: boolean;
  artifacts: GeneratedArtifact[];
  summary: string;
  statistics: {
    totalFiles: number;
    totalLines: number;
    byType: Record<ArtifactType, number>;
    byLanguage: Record<string, number>;
  };
  errors: string[];
  warnings: string[];
}

/**
 * Generation progress callback
 */
export type GenerationProgressCallback = (progress: {
  stage: 'analyzing' | 'generating' | 'validating' | 'finalizing';
  percent: number;
  message: string;
}) => void;

// ============================================================================
// Generation Agent
// ============================================================================

/**
 * Generation Agent
 *
 * Thin client wrapper - delegates to Java backend for AI-powered code generation.
 * Falls back to local templates if backend unavailable.
 */
export class GenerationAgent {
  private executor: AgentExecutor;
  private useBackend: boolean = true;

  constructor() {
    this.executor = new AgentExecutor(GenerationAgentContract);
  }

  /**
   * Generate code from canvas design
   */
  async generate(
    canvasState: CanvasState,
    lifecyclePhase: LifecyclePhase,
    options: GenerationOptions = {}
  ): Promise<CodeGenerationResult> {
    // Check if Java backend is available
    const backendAvailable = await isCanvasAIAvailable();

    if (backendAvailable && this.useBackend) {
      return this.generateViaBackend(canvasState, lifecyclePhase, options);
    } else {
      console.warn(
        '[GenerationAgent] Java backend unavailable, falling back to local generation'
      );
      return this.generateLocally(canvasState, lifecyclePhase, options);
    }
  }

  /**
   * Generate via Java backend (preferred - uses AI)
   */
  private async generateViaBackend(
    canvasState: CanvasState,
    lifecyclePhase: LifecyclePhase,
    options: GenerationOptions = {}
  ): Promise<CodeGenerationResult> {
    const service = await getCanvasAIService();

    try {
      const result = await service.generateCode({
        canvasState,
        options: {
          language: this.mapLanguageToProto(options.language),
          framework: options.framework,
          includeTests: options.includeTests,
          includeDocumentation: options.includeDocumentation,
          includeConfiguration: options.includeConfiguration,
          useAi: options.useAI ?? true,
          aiModel: options.aiModel,
          temperature: options.temperature,
        },
      });

      return result as CodeGenerationResult;
    } catch (error) {
      console.error(
        '[GenerationAgent] Backend generation failed, falling back:',
        error
      );
      return this.generateLocally(canvasState, lifecyclePhase, options);
    }
  }

  /**
   * Map language to proto enum
   */
  private mapLanguageToProto(language?: string) {
    switch (language) {
      case 'typescript':
        return 'PROGRAMMING_LANGUAGE_TYPESCRIPT';
      case 'java':
        return 'PROGRAMMING_LANGUAGE_JAVA';
      case 'python':
        return 'PROGRAMMING_LANGUAGE_PYTHON';
      case 'go':
        return 'PROGRAMMING_LANGUAGE_GO';
      default:
        return 'PROGRAMMING_LANGUAGE_TYPESCRIPT';
    }
  }

  /**
   * Generate locally (fallback - template-based)
   */
  private async generateLocally(
    canvasState: CanvasState,
    lifecyclePhase: LifecyclePhase,
    options: GenerationOptions = {}
  ): Promise<CodeGenerationResult> {
    const startTime = Date.now();

    try {
      // Execute with contract enforcement
      return await this.executor.execute(
        async () => {
          // Validate preconditions
          this.validatePreconditions(canvasState, options);

          // Generate artifacts
          const artifacts = await this.generateArtifacts(canvasState, options);

          // Validate generated code
          const { errors, warnings } = this.validateGeneration(
            artifacts,
            options
          );

          // Calculate statistics
          const statistics = this.calculateStatistics(artifacts);

          // Build result
          const result: CodeGenerationResult = {
            success: errors.length === 0,
            artifacts,
            summary: this.generateSummary(artifacts, statistics),
            statistics,
            errors,
            warnings,
          };

          const elapsedTime = Date.now() - startTime;
          console.log(
            `[GenerationAgent] Generated ${artifacts.length} artifacts in ${elapsedTime}ms`
          );

          return result;
        },
        canvasState,
        lifecyclePhase
      );
    } catch (error) {
      console.error('[GenerationAgent] Generation failed:', error);
      return {
        success: false,
        artifacts: [],
        summary: 'Code generation failed',
        statistics: {
          totalFiles: 0,
          totalLines: 0,
          byType: {} as Record<ArtifactType, number>,
          byLanguage: {},
        },
        errors: [error instanceof Error ? error.message : 'Unknown error'],
        warnings: [],
      };
    }
  }

  /**
   * Validate preconditions before generation
   */
  private validatePreconditions(
    canvasState: CanvasState,
    options: GenerationOptions
  ): void {
    // Check canvas has elements
    if (!canvasState.elements || canvasState.elements.length === 0) {
      throw new Error('Canvas is empty. Add elements before generating code.');
    }

    // Check validation report if provided
    if (options.validationReport) {
      const errors = options.validationReport.issues.filter(
        (i) => i.severity === 'error'
      );
      if (errors.length > 0) {
        throw new Error(
          `Canvas has ${errors.length} validation errors. Fix errors before generating code.`
        );
      }
    }
  }

  /**
   * Generate artifacts from canvas elements
   */
  private async generateArtifacts(
    canvasState: CanvasState,
    options: GenerationOptions
  ): Promise<GeneratedArtifact[]> {
    const artifacts: GeneratedArtifact[] = [];

    // Generate code for each element
    for (const element of canvasState.elements) {
      if (element.kind !== 'node') continue;

      try {
        const elementArtifacts = await this.generateElementArtifacts(
          element,
          options
        );
        artifacts.push(...elementArtifacts);
      } catch (error) {
        console.error(
          `[GenerationAgent] Failed to generate for element ${element.id}:`,
          error
        );
      }
    }

    // Generate configuration files
    if (options.includeConfiguration) {
      const configArtifacts = this.generateConfigurationFiles(
        canvasState,
        options
      );
      artifacts.push(...configArtifacts);
    }

    // Generate documentation
    if (options.includeDocumentation) {
      const docArtifacts = await this.generateDocumentation(
        canvasState,
        options
      );
      artifacts.push(...docArtifacts);
    }

    return artifacts;
  }

  /**
   * Generate artifacts for a single element
   */
  private async generateElementArtifacts(
    element: unknown,
    options: GenerationOptions
  ): Promise<GeneratedArtifact[]> {
    const artifacts: GeneratedArtifact[] = [];
    const elementType = element.type;

    // Map element types to generation strategies
    switch (elementType) {
      case 'api':
      case 'backend-api':
        artifacts.push(...(await this.generateAPIArtifacts(element, options)));
        break;

      case 'data':
      case 'database':
        artifacts.push(...this.generateDataArtifacts(element, options));
        break;

      case 'component':
      case 'page':
        artifacts.push(
          ...(await this.generateComponentArtifacts(element, options))
        );
        break;

      case 'infrastructure':
        artifacts.push(
          ...this.generateInfrastructureArtifacts(element, options)
        );
        break;

      default:
        console.warn(`[GenerationAgent] Unknown element type: ${elementType}`);
    }

    return artifacts;
  }

  /**
   * Generate API artifacts (routes, controllers, services)
   */
  private async generateAPIArtifacts(
    element: unknown,
    options: GenerationOptions
  ): Promise<GeneratedArtifact[]> {
    const artifacts: GeneratedArtifact[] = [];
    const apiName = element.data?.label || 'API';
    const language = options.language || 'typescript';

    // Generate service layer
    artifacts.push({
      id: `${element.id}-service`,
      type: 'source',
      path: `src/services/${this.toFileName(apiName)}.service.${this.getFileExtension(language)}`,
      content: this.generateServiceCode(element, options),
      language,
      framework: options.framework,
    });

    // Generate API routes
    artifacts.push({
      id: `${element.id}-routes`,
      type: 'source',
      path: `src/routes/${this.toFileName(apiName)}.routes.${this.getFileExtension(language)}`,
      content: this.generateRoutesCode(element, options),
      language,
      framework: options.framework,
    });

    // Generate tests
    if (options.includeTests) {
      artifacts.push({
        id: `${element.id}-test`,
        type: 'test',
        path: `src/services/${this.toFileName(apiName)}.service.test.${this.getFileExtension(language)}`,
        content: this.generateTestCode(element, options),
        language,
      });
    }

    return artifacts;
  }

  /**
   * Generate data/database artifacts (schemas, migrations)
   */
  private generateDataArtifacts(
    element: unknown,
    options: GenerationOptions
  ): GeneratedArtifact[] {
    const artifacts: GeneratedArtifact[] = [];
    const modelName = element.data?.label || 'Model';

    // Generate Prisma schema
    artifacts.push({
      id: `${element.id}-schema`,
      type: 'schema',
      path: `prisma/schema/${this.toFileName(modelName)}.prisma`,
      content: this.generatePrismaSchema(element, options),
      language: 'prisma',
    });

    // Generate migration
    artifacts.push({
      id: `${element.id}-migration`,
      type: 'schema',
      path: `prisma/migrations/${Date.now()}_${this.toFileName(modelName)}.sql`,
      content: this.generateMigration(element, options),
      language: 'sql',
    });

    return artifacts;
  }

  /**
   * Generate component artifacts (React/Vue/Angular components)
   */
  private async generateComponentArtifacts(
    element: unknown,
    options: GenerationOptions
  ): Promise<GeneratedArtifact[]> {
    const artifacts: GeneratedArtifact[] = [];
    const componentName = element.data?.label || 'Component';
    const framework = options.framework || 'react';
    const language = options.language || 'typescript';

    // Generate component
    artifacts.push({
      id: `${element.id}-component`,
      type: 'source',
      path: `src/components/${componentName}.${this.getComponentExtension(framework, language)}`,
      content: this.generateComponentCode(element, options),
      language,
      framework,
    });

    // Generate styles
    artifacts.push({
      id: `${element.id}-styles`,
      type: 'source',
      path: `src/components/${componentName}.module.css`,
      content: this.generateStylesCode(element, options),
      language: 'css',
    });

    // Generate tests
    if (options.includeTests) {
      artifacts.push({
        id: `${element.id}-test`,
        type: 'test',
        path: `src/components/${componentName}.test.${this.getFileExtension(language)}`,
        content: this.generateComponentTestCode(element, options),
        language,
      });
    }

    return artifacts;
  }

  /**
   * Generate infrastructure artifacts (Docker, K8s, Terraform)
   */
  private generateInfrastructureArtifacts(
    element: unknown,
    options: GenerationOptions
  ): GeneratedArtifact[] {
    const artifacts: GeneratedArtifact[] = [];

    // Generate Dockerfile
    artifacts.push({
      id: `${element.id}-dockerfile`,
      type: 'infrastructure',
      path: 'Dockerfile',
      content: this.generateDockerfile(element, options),
      language: 'dockerfile',
    });

    // Generate docker-compose.yml
    artifacts.push({
      id: `${element.id}-compose`,
      type: 'infrastructure',
      path: 'docker-compose.yml',
      content: this.generateDockerCompose(element, options),
      language: 'yaml',
    });

    return artifacts;
  }

  /**
   * Generate configuration files (package.json, tsconfig, etc.)
   */
  private generateConfigurationFiles(
    canvasState: CanvasState,
    options: GenerationOptions
  ): GeneratedArtifact[] {
    const artifacts: GeneratedArtifact[] = [];
    const language = options.language || 'typescript';

    if (language === 'typescript') {
      // Generate package.json
      artifacts.push({
        id: 'package-json',
        type: 'config',
        path: 'package.json',
        content: this.generatePackageJson(canvasState, options),
        language: 'json',
      });

      // Generate tsconfig.json
      artifacts.push({
        id: 'tsconfig',
        type: 'config',
        path: 'tsconfig.json',
        content: this.generateTsConfig(options),
        language: 'json',
      });
    }

    // Generate .env.example
    artifacts.push({
      id: 'env-example',
      type: 'config',
      path: '.env.example',
      content: this.generateEnvExample(canvasState, options),
      language: 'text',
    });

    return artifacts;
  }

  /**
   * Generate documentation
   */
  private async generateDocumentation(
    canvasState: CanvasState,
    options: GenerationOptions
  ): Promise<GeneratedArtifact[]> {
    const artifacts: GeneratedArtifact[] = [];

    // Generate README
    artifacts.push({
      id: 'readme',
      type: 'documentation',
      path: 'README.md',
      content: this.generateReadme(canvasState, options),
      language: 'markdown',
    });

    // Generate API documentation
    const hasAPI = canvasState.elements.some(
      (e) => e.type === 'api' || e.type === 'backend-api'
    );
    if (hasAPI) {
      artifacts.push({
        id: 'api-docs',
        type: 'documentation',
        path: 'docs/API.md',
        content: this.generateAPIDocumentation(canvasState, options),
        language: 'markdown',
      });
    }

    return artifacts;
  }

  /**
   * Validate generated code
   */
  private validateGeneration(
    artifacts: GeneratedArtifact[],
    options: GenerationOptions
  ): { errors: string[]; warnings: string[] } {
    const errors: string[] = [];
    const warnings: string[] = [];

    // Check for empty artifacts
    for (const artifact of artifacts) {
      if (!artifact.content || artifact.content.trim().length === 0) {
        warnings.push(`Empty artifact: ${artifact.path}`);
      }
    }

    // Check for required files
    const hasSources = artifacts.some((a) => a.type === 'source');
    if (!hasSources) {
      errors.push('No source files generated');
    }

    return { errors, warnings };
  }

  /**
   * Calculate generation statistics
   */
  private calculateStatistics(
    artifacts: GeneratedArtifact[]
  ): CodeGenerationResult['statistics'] {
    const byType: Record<ArtifactType, number> = {
      source: 0,
      test: 0,
      config: 0,
      documentation: 0,
      schema: 0,
      api: 0,
      infrastructure: 0,
    };

    const byLanguage: Record<string, number> = {};
    let totalLines = 0;

    for (const artifact of artifacts) {
      byType[artifact.type]++;
      byLanguage[artifact.language] = (byLanguage[artifact.language] || 0) + 1;
      totalLines += artifact.content.split('\n').length;
    }

    return {
      totalFiles: artifacts.length,
      totalLines,
      byType,
      byLanguage,
    };
  }

  /**
   * Generate summary
   */
  private generateSummary(
    artifacts: GeneratedArtifact[],
    statistics: CodeGenerationResult['statistics']
  ): string {
    const parts: string[] = [];

    parts.push(
      `Generated ${statistics.totalFiles} files (${statistics.totalLines} lines)`
    );

    if (statistics.byType.source > 0) {
      parts.push(`${statistics.byType.source} source files`);
    }
    if (statistics.byType.test > 0) {
      parts.push(`${statistics.byType.test} test files`);
    }
    if (statistics.byType.config > 0) {
      parts.push(`${statistics.byType.config} config files`);
    }
    if (statistics.byType.documentation > 0) {
      parts.push(`${statistics.byType.documentation} docs`);
    }

    return parts.join(', ');
  }

  // ========================================================================
  // Code Generation Templates (Simplified - extend with AI later)
  // ========================================================================

  private generateServiceCode(
    element: unknown,
    options: GenerationOptions
  ): string {
    const serviceName = this.toPascalCase(element.data?.label || 'Service');
    const language = options.language || 'typescript';

    if (language === 'typescript') {
      return `/**
 * ${serviceName} Service
 * 
 * @doc.type class
 * @doc.purpose Service layer for ${serviceName}
 * @doc.layer product
 * @doc.pattern Service
 */

export class ${serviceName}Service {
    /**
     * List all items
     */
    async list(options: { limit?: number; offset?: number } = {}): Promise<unknown[]> {
        // Implement list logic with database/API integration
        const query = new QueryBuilder()
            .select('*')
            .from('${serviceName.toLowerCase()}s')
            .limit(options.limit || 50)
            .offset(options.offset || 0)
            .build();
        
        const results = await this.database.query(query);
        return results.map(this.mapToEntity);
    }

    /**
     * Get item by ID
     */
    async getById(id: string): Promise<unknown | null> {
        // Implement get logic with error handling
        if (!id) {
            throw new Error('ID is required');
        }
        
        const query = new QueryBuilder()
            .select('*')
            .from('${serviceName.toLowerCase()}s')
            .where('id = ?', [id])
            .build();
        
        const result = await this.database.queryOne(query);
        return result ? this.mapToEntity(result) : null;
    }

    /**
     * Create new item
     */
    async create(data: unknown): Promise<unknown> {
        // Implement create logic with validation
        if (!data) {
            throw new Error('Data is required');
        }
        
        const entity = this.validateAndSanitize(data);
        const query = new QueryBuilder()
            .insertInto('${serviceName.toLowerCase()}s')
            .values(entity)
            .build();
        
        const result = await this.database.insert(query);
        return this.mapToEntity({ ...entity, id: result.insertId });
    }

    /**
     * Update item
     */
    async update(id: string, data: unknown): Promise<unknown> {
        // Implement update logic with validation
        if (!id || !data) {
            throw new Error('ID and data are required');
        }
        
        const entity = this.validateAndSanitize(data);
        const query = new QueryBuilder()
            .update('${serviceName.toLowerCase()}s')
            .set(entity)
            .where('id = ?', [id])
            .build();
        
        await this.database.update(query);
        return this.getById(id);
    }
        /**
     * Delete item
     */
    async delete(id: string): Promise<void> {
        // Implement delete logic with validation
        if (!id) {
            throw new Error('ID is required');
        }
        
        const query = new QueryBuilder()
            .deleteFrom('${serviceName.toLowerCase()}s')
            .where('id = ?', [id])
            .build();
        
        await this.database.delete(query);
    }

    /**
     * Helper method to validate and sanitize data
     */
    private validateAndSanitize(data: unknown): unknown {
        // Implement validation logic based on schema
        if (typeof data !== 'object' || data === null) {
            throw new Error('Invalid data format');
        }
        
        // Remove sensitive fields and validate required fields
        const sanitized = { ...data };
        delete sanitized.password;
        delete sanitized.secret;
        
        return sanitized;
    }

    /**
     * Helper method to map database results to entities
     */
    private mapToEntity(result: unknown): unknown {
        // Implement mapping logic
        return {
            id: result.id,
            ...result.data,
            createdAt: result.created_at,
            updatedAt: result.updated_at
        };
    }
}
`;
    }

    return `// ${serviceName} Service - ${language} implementation`;
  }

  private generateRoutesCode(element: unknown, options: GenerationOptions): string {
    const serviceName = this.toPascalCase(element.data?.label || 'Service');
    const routePath = this.toKebabCase(element.data?.label || 'service');

    return `/**
 * ${serviceName} Routes
 */

import { Router } from 'express';
import { ${serviceName}Service } from '../services/${this.toFileName(serviceName)}.service';

const router = Router();
const service = new ${serviceName}Service();

/**
 * GET /${routePath}
 * List all items
 */
router.get('/${routePath}', async (req, res) => {
    try {
        const items = await service.list({
            limit: Number(req.query.limit) || 50,
            offset: Number(req.query.offset) || 0,
        });
        res.json(items);
    } catch (error) {
        res.status(500).json({ error: 'Failed to list items' });
    }
});

/**
 * GET /${routePath}/:id
 * Get item by ID
 */
router.get('/${routePath}/:id', async (req, res) => {
    try {
        const item = await service.getById(req.params.id);
        if (!item) {
            return res.status(404).json({ error: 'Not found' });
        }
        res.json(item);
    } catch (error) {
        res.status(500).json({ error: 'Failed to get item' });
    }
});

/**
 * POST /${routePath}
 * Create new item
 */
router.post('/${routePath}', async (req, res) => {
    try {
        const item = await service.create(req.body);
        res.status(201).json(item);
    } catch (error) {
        res.status(500).json({ error: 'Failed to create item' });
    }
});

export default router;
`;
  }

  private generateTestCode(element: unknown, options: GenerationOptions): string {
    const serviceName = this.toPascalCase(element.data?.label || 'Service');

    return `/**
 * ${serviceName} Service Tests
 */

import { describe, it, expect } from 'vitest';
import { ${serviceName}Service } from './${this.toFileName(serviceName)}.service';

describe('${serviceName}Service', () => {
    it('should list items', async () => {
        const service = new ${serviceName}Service();
        const items = await service.list();
        expect(items).toBeInstanceOf(Array);
    });

    it('should get item by ID', async () => {
        const service = new ${serviceName}Service();
        const item = await service.getById('test-id');
        expect(item).toBeDefined();
    });

    it('should create item', async () => {
        const service = new ${serviceName}Service();
        const item = await service.create({ name: 'Test' });
        expect(item).toBeDefined();
    });
});
`;
  }

  private generatePrismaSchema(
    element: unknown,
    options: GenerationOptions
  ): string {
    const modelName = this.toPascalCase(element.data?.label || 'Model');

    return `model ${modelName} {
  id        String   @id @default(cuid())
  createdAt DateTime @default(now())
  updatedAt DateTime @updatedAt
  
  // NOTE: Add model fields
  name      String
}
`;
  }

  private generateMigration(element: unknown, options: GenerationOptions): string {
    const tableName = this.toSnakeCase(element.data?.label || 'model');

    return `-- CreateTable
CREATE TABLE "${tableName}" (
    "id" TEXT NOT NULL,
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP(3) NOT NULL,
    "name" TEXT NOT NULL,

    CONSTRAINT "${tableName}_pkey" PRIMARY KEY ("id")
);
`;
  }

  private generateComponentCode(
    element: unknown,
    options: GenerationOptions
  ): string {
    const componentName = this.toPascalCase(element.data?.label || 'Component');

    return `/**
 * ${componentName} Component
 */

import React from 'react';
import styles from './${componentName}.module.css';

export interface ${componentName}Props {
    // NOTE: Add props
}

export const ${componentName}: React.FC<${componentName}Props> = (props) => {
    return (
        <div className={styles.container}>
            <h1>${componentName}</h1>
            {/* NOTE: Implement component */}
        </div>
    );
};
`;
  }

  private generateStylesCode(element: unknown, options: GenerationOptions): string {
    return `.container {
    padding: 1rem;
}
`;
  }

  private generateComponentTestCode(
    element: unknown,
    options: GenerationOptions
  ): string {
    const componentName = this.toPascalCase(element.data?.label || 'Component');

    return `/**
 * ${componentName} Tests
 */

import { render, screen } from '@testing-library/react';
import { ${componentName} } from './${componentName}';

describe('${componentName}', () => {
    it('should render', () => {
        render(<${componentName} />);
        expect(screen.getByText('${componentName}')).toBeInTheDocument();
    });
});
`;
  }

  private generateDockerfile(element: unknown, options: GenerationOptions): string {
    return `FROM node:20-alpine

WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build

EXPOSE 3000

CMD ["npm", "start"]
`;
  }

  private generateDockerCompose(
    element: unknown,
    options: GenerationOptions
  ): string {
    return `version: '3.8'

services:
  app:
    build: .
    ports:
      - "3000:3000"
    environment:
      - NODE_ENV=production
`;
  }

  private generatePackageJson(
    canvasState: CanvasState,
    options: GenerationOptions
  ): string {
    return JSON.stringify(
      {
        name: 'generated-app',
        version: '1.0.0',
        type: 'module',
        scripts: {
          dev: 'vite',
          build: 'tsc && vite build',
          test: 'vitest',
        },
        dependencies: {
          react: '^18.2.0',
          'react-dom': '^18.2.0',
        },
        devDependencies: {
          '@types/react': '^18.2.0',
          '@types/react-dom': '^18.2.0',
          typescript: '^5.0.0',
          vite: '^5.0.0',
          vitest: '^1.0.0',
        },
      },
      null,
      2
    );
  }

  private generateTsConfig(options: GenerationOptions): string {
    return JSON.stringify(
      {
        compilerOptions: {
          target: 'ES2020',
          useDefineForClassFields: true,
          lib: ['ES2020', 'DOM', 'DOM.Iterable'],
          module: 'ESNext',
          skipLibCheck: true,
          moduleResolution: 'bundler',
          allowImportingTsExtensions: true,
          resolveJsonModule: true,
          isolatedModules: true,
          noEmit: true,
          jsx: 'react-jsx',
          strict: true,
          noUnusedLocals: true,
          noUnusedParameters: true,
          noFallthroughCasesInSwitch: true,
        },
        include: ['src'],
      },
      null,
      2
    );
  }

  private generateEnvExample(
    canvasState: CanvasState,
    options: GenerationOptions
  ): string {
    const lines: string[] = [
      '# Environment Variables',
      '',
      '# Database',
      'DATABASE_URL="postgresql://user:password@localhost:5432/db"',
      '',
      '# API',
      'API_PORT=3000',
      'API_SECRET=your-secret-here',
    ];

    return lines.join('\n');
  }

  private generateReadme(
    canvasState: CanvasState,
    options: GenerationOptions
  ): string {
    return `# Generated Application

Generated from canvas design.

## Setup

\`\`\`bash
npm install
\`\`\`

## Development

\`\`\`bash
npm run dev
\`\`\`

## Build

\`\`\`bash
npm run build
\`\`\`

## Test

\`\`\`bash
npm test
\`\`\`
`;
  }

  private generateAPIDocumentation(
    canvasState: CanvasState,
    options: GenerationOptions
  ): string {
    const apiElements = canvasState.elements.filter(
      (e) => e.type === 'api' || e.type === 'backend-api'
    );

    const lines: string[] = ['# API Documentation', '', '## Endpoints', ''];

    for (const api of apiElements) {
      const name = api.data?.label || 'API';
      const path = this.toKebabCase(name);
      lines.push(`### ${name}`);
      lines.push('');
      lines.push(`- \`GET /${path}\` - List all items`);
      lines.push(`- \`GET /${path}/:id\` - Get item by ID`);
      lines.push(`- \`POST /${path}\` - Create new item`);
      lines.push('');
    }

    return lines.join('\n');
  }

  // ========================================================================
  // Utility Methods
  // ========================================================================

  private toFileName(name: string): string {
    return name.replace(/\s+/g, '-').toLowerCase();
  }

  private toPascalCase(str: string): string {
    return str
      .replace(/\w+/g, (w) => w[0].toUpperCase() + w.slice(1).toLowerCase())
      .replace(/\s+/g, '');
  }

  private toKebabCase(str: string): string {
    return str.replace(/\s+/g, '-').toLowerCase();
  }

  private toSnakeCase(str: string): string {
    return str.replace(/\s+/g, '_').toLowerCase();
  }

  private getFileExtension(language: string): string {
    switch (language) {
      case 'typescript':
        return 'ts';
      case 'javascript':
        return 'js';
      case 'python':
        return 'py';
      case 'java':
        return 'java';
      case 'go':
        return 'go';
      default:
        return 'txt';
    }
  }

  private getComponentExtension(framework: string, language: string): string {
    const ext = language === 'typescript' ? 'tsx' : 'jsx';
    return ext;
  }
}
