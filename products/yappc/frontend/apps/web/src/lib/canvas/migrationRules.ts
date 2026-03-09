/**
 * Content Type Migration Rules
 *
 * Defines how data transforms when converting between content types.
 * Provides migration functions and compatibility checking.
 *
 * @doc.type module
 * @doc.purpose Content type conversion logic
 * @doc.layer product
 * @doc.pattern Data Migration
 */

import { logger } from '../../utils/Logger';

import { ArtifactType } from '@/types/fow-stages';

/**
 * Migration function signature
 */
export type MigrationFn<TFrom = unknown, TTo = unknown> = (data: TFrom) => TTo;

/**
 * Artifact data structures for different types
 */
export interface CodeArtifactData {
  code: string;
  language: string;
  filePath?: string;
}

export interface DocumentationData {
  content: string;
  format: 'markdown' | 'html' | 'plaintext';
}

export interface DiagramData {
  nodes: Array<{ id: string; label: string; type: string }>;
  edges: Array<{ from: string; to: string; label?: string }>;
  layout?: string;
}

export interface RequirementData {
  title: string;
  description: string;
  priority: 'high' | 'medium' | 'low';
  acceptanceCriteria?: string[];
}

export interface TestData {
  testCases: Array<{
    name: string;
    steps: string[];
    expected: string;
  }>;
  framework?: string;
}

export interface ApiSpecData {
  endpoints: Array<{
    method: string;
    path: string;
    description: string;
    parameters?: unknown[];
    responses?: unknown[];
  }>;
  baseUrl?: string;
}

/**
 * Helper functions for data extraction
 */

/** Extract code blocks from markdown */
function extractCodeBlocks(markdown: string): string {
  const codeBlockRegex = /```[\w]*\n([\s\S]*?)```/g;
  const matches = [...markdown.matchAll(codeBlockRegex)];
  return matches.map((m) => m[1]).join('\n\n');
}

/** Detect programming language from code content */
function detectLanguage(code: string): string {
  if (
    code.includes('function') ||
    code.includes('const') ||
    code.includes('let')
  )
    return 'javascript';
  if (code.includes('def ') || code.includes('import ')) return 'python';
  if (code.includes('public class') || code.includes('private ')) return 'java';
  if (code.includes('<?php')) return 'php';
  return 'plaintext';
}

/** Convert code to markdown documentation */
function codeToMarkdown(data: CodeArtifactData): string {
  return `# Code Documentation\n\n\`\`\`${data.language}\n${data.code}\n\`\`\`\n\n## File Path\n${data.filePath || 'N/A'}`;
}

/** Parse requirements from markdown */
function parseRequirements(markdown: string): RequirementData {
  const lines = markdown.split('\n');
  const title =
    lines
      .find((l) => l.startsWith('#'))
      ?.replace('#', '')
      .trim() || 'Untitled';
  const description = lines
    .filter((l) => !l.startsWith('#') && l.trim())
    .join('\n');

  const acceptanceCriteria = lines
    .filter((l) => l.trim().startsWith('-') || l.trim().startsWith('*'))
    .map((l) => l.replace(/^[-*]\s*/, '').trim());

  return {
    title,
    description,
    priority: 'medium',
    acceptanceCriteria:
      acceptanceCriteria.length > 0 ? acceptanceCriteria : undefined,
  };
}

/** Extract API endpoints from code */
function parseEndpointsFromCode(code: string): ApiSpecData {
  const endpoints: ApiSpecData['endpoints'] = [];

  // Simple regex patterns for common API frameworks
  const restRegex = /@(GET|POST|PUT|DELETE|PATCH)\(['"]([^'"]+)['"]\)/g;
  const expressRegex = /app\.(get|post|put|delete|patch)\(['"]([^'"]+)['"]/g;

  let match;
  while ((match = restRegex.exec(code)) !== null) {
    endpoints.push({
      method: match[1],
      path: match[2],
      description: `${match[1]} endpoint`,
    });
  }

  while ((match = expressRegex.exec(code)) !== null) {
    endpoints.push({
      method: match[1].toUpperCase(),
      path: match[2],
      description: `${match[1].toUpperCase()} endpoint`,
    });
  }

  return {
    endpoints:
      endpoints.length > 0
        ? endpoints
        : [
            {
              method: 'GET',
              path: '/api/placeholder',
              description:
                'Placeholder endpoint - update with actual API details',
            },
          ],
  };
}

/** Convert diagram nodes to components */
function diagramToComponents(data: DiagramData): {
  components: Array<{ id: string; name: string; type: string }>;
  connections: Array<{ from: string; to: string; relationship: string }>;
} {
  return {
    components: data.nodes.map((node) => ({
      id: node.id,
      name: node.label,
      type: node.type,
    })),
    connections: data.edges.map((edge) => ({
      from: edge.from,
      to: edge.to,
      relationship: edge.label || 'uses',
    })),
  };
}

/** Extract actors from diagram nodes */
function extractActors(nodes: unknown[]): string[] {
  return nodes
    .filter((n) => n.type === 'actor' || n.type === 'participant')
    .map((n) => n.label);
}

/** Extract messages from diagram edges */
function extractMessages(edges: unknown[]): unknown[] {
  return edges.map((edge, index) => ({
    id: index,
    from: edge.from,
    to: edge.to,
    message: edge.label || 'message',
  }));
}

/** Generate test scaffolding from requirements */
function requirementToTest(data: RequirementData): TestData {
  const testCases = (data.acceptanceCriteria || [data.description]).map(
    (criteria, index) => ({
      name: `Test case ${index + 1}: ${criteria.substring(0, 50)}`,
      steps: [
        'Setup test environment',
        'Execute the scenario',
        'Verify outcome',
      ],
      expected: criteria,
    })
  );

  return {
    testCases,
    framework: 'jest',
  };
}

/**
 * Migration Rules Matrix
 *
 * Defines transformation functions between content types.
 * Only includes migrations that preserve meaningful data.
 */
export const migrationRules: Record<
  ArtifactType,
  Partial<Record<ArtifactType, MigrationFn>>
> = {
  // Requirement migrations
  REQUIREMENT: {
    ARCHITECTURE_DECISION_RECORD: (data: CodeArtifactData) => ({
      content: codeToMarkdown(data),
      format: 'markdown' as const,
    }),
    RELEASE_PACKET: (data: CodeArtifactData) => ({
      testCases: [
        {
          name: `Test for ${data.filePath || 'requirement'}`,
          steps: [
            'Import the requirement',
            'Call the function',
            'Assert expected result',
          ],
          expected: 'Requirement validated successfully',
        },
      ],
      framework: detectLanguage(data.code) === 'javascript' ? 'jest' : 'pytest',
    }),
    DELIVERY_EVIDENCE: (data: CodeArtifactData) =>
      parseEndpointsFromCode(data.code),
  },

  // Architecture Decision Record migrations
  ARCHITECTURE_DECISION_RECORD: {
    PLAN: (data: DiagramData) => ({
      content: `# Implementation Plan\n\n## Components\n${data.nodes.map((n) => `- **${n.label}** (${n.type})`).join('\n')}\n\n## Connections\n${data.edges.map((e) => `- ${e.from} → ${e.to}${e.label ? ` (${e.label})` : ''}`).join('\n')}`,
      format: 'markdown' as const,
    }),
  },

  // Plan migrations
  PLAN: {
    DEVSECOPS_ITEM: (data: DocumentationData) => ({
      content:
        extractCodeBlocks(data.content) || '// No code blocks found in plan',
      language: detectLanguage(data.content),
    }),
  },

  // Release Packet migrations
  RELEASE_PACKET: {
    OPS_BASELINE: (data: TestData) => ({
      content: `# Operations Baseline\n\n## Test Cases\n${data.testCases.map((tc) => `### ${tc.name}\n\n**Expected:** ${tc.expected}`).join('\n\n')}`,
      format: 'markdown' as const,
    }),
  },
};

/**
 * Migrate data from one type to another
 */
export function migrateData<TFrom = unknown, TTo = unknown>(
  data: TFrom,
  fromType: ArtifactType,
  toType: ArtifactType
): TTo | null {
  const migrationFn = migrationRules[fromType]?.[toType];

  if (migrationFn) {
    try {
      return migrationFn(data) as TTo;
    } catch (error) {
      logger.error('Migration failed', 'migration-rules', {
        fromType,
        toType,
        error: error instanceof Error ? error.message : String(error),
      });
      return null;
    }
  }

  // No migration rule defined - return null to indicate lossy conversion
  return null;
}

/**
 * Check if a migration is compatible (preserves data)
 */
export function isCompatibleConversion(
  fromType: ArtifactType,
  toType: ArtifactType
): boolean {
  return Boolean(migrationRules[fromType]?.[toType]);
}

/**
 * Get all compatible target types for a given source type
 */
export function getCompatibleTypes(sourceType: ArtifactType): ArtifactType[] {
  const rules = migrationRules[sourceType];
  return rules ? (Object.keys(rules) as ArtifactType[]) : [];
}
