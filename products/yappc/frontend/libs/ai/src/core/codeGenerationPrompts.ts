/**
 * AI Code Generation Templates
 *
 * Prompt templates for generating code from canvas nodes using LLMs.
 */

interface BaseNodeData {
  label: string;
  description?: string;
}

interface ServiceNodeData extends BaseNodeData {
  type: 'service';
  persona: string;
  status: string;
}

interface APIEndpointNodeData extends BaseNodeData {
  type: 'apiEndpoint';
  path: string;
  method: string;
}

interface DatabaseNodeData extends BaseNodeData {
  type: 'database';
  engine: string;
  schema?: {
    tables?: Array<{
      name: string;
      columns: Array<{
        name: string;
        type: string;
        nullable?: boolean;
      }>;
    }>;
  };
}

interface RequirementNodeData extends BaseNodeData {
  type: 'requirement';
  priority: string;
  userStory?: string;
  acceptanceCriteria?: string[];
  storyPoints?: number;
}

interface ArchitectureNodeData extends BaseNodeData {
  type: 'architecture';
  components?: string[];
  technologies?: string[];
  constraints?: string[];
  decisions?: string[];
}

export function buildServicePrompt(data: ServiceNodeData | RequirementNodeData): string {
  return [
    'Generate a production-ready TypeScript service.',
    `Service name: ${data.label}`,
    data.description ? `Description: ${data.description}` : '',
    data.type === 'requirement' && data.userStory ? `User story: ${data.userStory}` : '',
    data.type === 'requirement' && data.acceptanceCriteria?.length
      ? `Acceptance criteria:\n- ${data.acceptanceCriteria.join('\n- ')}`
      : '',
    'Requirements:',
    '- Use clear typing and interfaces',
    '- Include validation and error handling',
    '- Include JSDoc for public methods',
    '- Keep code modular and testable',
  ]
    .filter(Boolean)
    .join('\n');
}

export function buildAPIRoutePrompt(data: APIEndpointNodeData): string {
  return [
    'Generate a TypeScript API route handler.',
    `Endpoint: ${data.method.toUpperCase()} ${data.path}`,
    `Name: ${data.label}`,
    data.description ? `Description: ${data.description}` : '',
    'Requirements:',
    '- Input validation',
    '- Typed request/response payloads',
    '- Proper HTTP status codes',
    '- Error handling and logging',
  ]
    .filter(Boolean)
    .join('\n');
}

export function buildPrismaSchemaPrompt(data: DatabaseNodeData): string {
  const tableHints =
    data.schema?.tables?.map((t) => `- ${t.name}: ${t.columns.map((c) => `${c.name}:${c.type}`).join(', ')}`).join('\n') ?? '';

  return [
    'Generate a Prisma schema.',
    `Database engine: ${data.engine}`,
    `Domain: ${data.label}`,
    data.description ? `Description: ${data.description}` : '',
    tableHints ? `Known tables:\n${tableHints}` : '',
    'Requirements:',
    '- Include indexes and relations where appropriate',
    '- Use idiomatic Prisma types',
    '- Preserve nullability requirements',
  ]
    .filter(Boolean)
    .join('\n');
}

export function buildArchitecturePrompt(data: ArchitectureNodeData | RequirementNodeData): string {
  return [
    'Generate a concise system architecture document in Markdown.',
    `System: ${data.label}`,
    data.description ? `Context: ${data.description}` : '',
    'components' in data && data.components?.length ? `Components: ${data.components.join(', ')}` : '',
    'technologies' in data && data.technologies?.length ? `Technologies: ${data.technologies.join(', ')}` : '',
    'constraints' in data && data.constraints?.length ? `Constraints: ${data.constraints.join(', ')}` : '',
    'decisions' in data && data.decisions?.length ? `Decisions: ${data.decisions.join(', ')}` : '',
    'Required sections: Overview, Components, Data Flow, Security, Scalability, Risks.',
  ]
    .filter(Boolean)
    .join('\n');
}

export function buildRequirementExtractionPrompt(userInput: string): string {
  return [
    'Extract product requirements from the following input.',
    'Return user stories, acceptance criteria, priority, and assumptions.',
    'Input:',
    userInput,
  ].join('\n');
}

export function buildCodeReviewPrompt(code: string, language: string): string {
  return [
    `Review the following ${language} code.`,
    'Return findings under: Bugs, Security, Performance, Maintainability, Tests.',
    'Code:',
    code,
  ].join('\n');
}

export function buildTestGenerationPrompt(code: string, testType: 'unit' | 'integration' | 'e2e'): string {
  return [
    `Generate ${testType} tests for the following code.`,
    'Include edge cases and failure paths.',
    'Code:',
    code,
  ].join('\n');
}

export const PERSONA_SYSTEM_PROMPTS = {
  pm: `You are an expert Product Manager assistant. Help with:
- Writing clear user stories
- Defining acceptance criteria
- Prioritizing features
- Stakeholder communication
- Requirements gathering
Be concise, user-focused, and data-driven.`,
  architect: `You are an expert Software Architect assistant. Help with:
- System design and architecture
- Technology stack selection
- Scalability planning
- Security architecture
- Integration patterns
Be thorough, consider trade-offs, and focus on long-term maintainability.`,
  developer: `You are an expert Software Developer assistant. Help with:
- Writing production-ready code
- API design and implementation
- Database schema design
- Code review and refactoring
- Testing strategies
Be pragmatic, follow best practices, and write clean, maintainable code.`,
  qa: `You are an expert QA Engineer assistant. Help with:
- Test planning and strategy
- Writing comprehensive test cases
- Test automation
- Quality metrics
- Bug reporting
Be thorough, detail-oriented, and focus on edge cases.`,
  devops: `You are an expert DevOps Engineer assistant. Help with:
- CI/CD pipeline design
- Infrastructure as code
- Deployment strategies
- Monitoring and observability
- Security and compliance
Be practical, automation-focused, and reliability-oriented.`,
} as const;

export type Persona = keyof typeof PERSONA_SYSTEM_PROMPTS;
