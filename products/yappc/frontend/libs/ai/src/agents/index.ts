/**
 * @ghatana/yappc-agents
 *
 * Intelligent AI agents for automated development tasks.
 *
 * Agents:
 * - BaseAgent: Abstract base class for all agents
 * - DesignAgent: Design review and UI/UX analysis
 * - ReviewAgent: Code review and quality analysis
 * - CodeAgent: Code generation and refactoring
 *
 * Orchestration:
 * - AgentOrchestrator: Multi-agent coordination and workflows
 *
 * @example
 * ```typescript
 * import { DesignAgent, ReviewAgent, AgentOrchestrator } from '@ghatana/yappc-ai-core/agents';
 * import { OpenAIProvider } from '@ghatana/yappc-ai';
 *
 * const aiService = new OpenAIProvider({ apiKey: '...' });
 *
 * // Create agents
 * const designAgent = new DesignAgent({
 *   id: 'design-1',
 *   name: 'Design Reviewer',
 *   description: 'Reviews UI designs for accessibility and consistency',
 *   aiService,
 * });
 *
 * const reviewAgent = new ReviewAgent({
 *   id: 'review-1',
 *   name: 'Code Reviewer',
 *   description: 'Reviews code for quality and security',
 *   aiService,
 * });
 *
 * // Initialize agents
 * await designAgent.initialize();
 * await reviewAgent.initialize();
 *
 * // Execute tasks
 * const designResult = await designAgent.execute({
 *   code: '<button>Click me</button>',
 *   styles: 'button { color: red; }',
 * });
 *
 * const codeResult = await reviewAgent.execute({
 *   code: 'function add(a, b) { return a + b; }',
 *   language: 'javascript',
 * });
 *
 * // Orchestrate multiple agents
 * const orchestrator = new AgentOrchestrator();
 * orchestrator.registerAgent(designAgent);
 * orchestrator.registerAgent(reviewAgent);
 *
 * const result = await orchestrator.executeTask('design-review', {
 *   code: '<div>Hello</div>',
 * });
 * ```
 */

// Base classes and types
export { BaseAgent } from './base/Agent';
export type {
  IAgent,
  AgentConfig,
  AgentStatus,
  AgentTask,
  TaskResult,
  AgentEventType,
  AgentEventListener,
  AgentEvent,
  AgentMetrics,
  AgentCapability,
  TaskPriority,
  TaskStatus,
} from './types';

// Design Agent
export { DesignAgent } from './agents/DesignAgent';
export type {
  DesignReviewInput,
  DesignReviewOutput,
  DesignSystemRules,
  DesignIssue,
  DesignIssueSeverity,
  DesignIssueCategory,
} from './agents/DesignAgent';

// Review Agent
export { ReviewAgent } from './agents/ReviewAgent';
export type {
  CodeReviewInput,
  CodeReviewOutput,
  CodeIssue,
  CodeIssueSeverity,
  CodeIssueCategory,
  CodeMetrics,
} from './agents/ReviewAgent';

// Code Agent
export { CodeAgent } from './agents/CodeAgent';
export type {
  CodeGenerationInput,
  CodeGenerationOutput,
} from './agents/CodeAgent';

// Orchestration
export { AgentOrchestrator } from './orchestration/AgentOrchestrator';
export type {
  Workflow,
  WorkflowStep,
  WorkflowResult,
} from './orchestration/AgentOrchestrator';

// API clients
export { AIAgentAPIClient, AIAgentClientFactory } from './api-client';
export type {
  AIAgentClientConfig,
  CopilotInput,
  CopilotOutput,
  QueryParserInput,
  QueryParserOutput,
  PredictionInput,
  PredictionOutput,
  AnomalyInput,
  AnomalyOutput,
} from './api-client';
