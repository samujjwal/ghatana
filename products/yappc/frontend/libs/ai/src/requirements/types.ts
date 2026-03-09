/**
 * Type definitions for AI requirements service.
 *
 * @doc.type module
 * @doc.purpose AI requirements service types
 * @doc.layer product
 * @doc.pattern Value Object
 */

/**
 * AI requirement suggestion.
 *
 * @doc.type interface
 * @doc.purpose AI requirement suggestion
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface AISuggestion {
  /** Suggestion ID */
  id: string;
  /** Suggested title */
  title: string;
  /** Suggested description */
  description: string;
  /** Suggested priority */
  priority: 'critical' | 'high' | 'medium' | 'low';
  /** Suggested tags */
  tags: string[];
  /** Confidence score (0-1) */
  confidence: number;
  /** Reasoning */
  reasoning: string;
}

/**
 * Requirement quality analysis.
 *
 * @doc.type interface
 * @doc.purpose Requirement quality analysis
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface QualityAnalysis {
  /** Overall quality score (0-100) */
  score: number;
  /** Quality issues */
  issues: QualityIssue[];
  /** Improvement suggestions */
  suggestions: string[];
  /** Completeness percentage */
  completeness: number;
  /** Clarity score (0-100) */
  clarity: number;
}

/**
 * Quality issue.
 *
 * @doc.type interface
 * @doc.purpose Quality issue
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface QualityIssue {
  /** Issue type */
  type: 'missing-description' | 'unclear-title' | 'vague-requirements' | 'missing-acceptance-criteria';
  /** Issue severity */
  severity: 'critical' | 'high' | 'medium' | 'low';
  /** Issue description */
  description: string;
  /** Suggested fix */
  suggestion: string;
}

/**
 * Generated test case.
 *
 * @doc.type interface
 * @doc.purpose Generated test case
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface GeneratedTestCase {
  /** Test case ID */
  id: string;
  /** Test case name */
  name: string;
  /** Test case description */
  description: string;
  /** Test steps */
  steps: TestStep[];
  /** Expected result */
  expectedResult: string;
  /** Test type */
  type: 'unit' | 'integration' | 'e2e' | 'acceptance';
}

/**
 * Test step.
 *
 * @doc.type interface
 * @doc.purpose Test step
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface TestStep {
  /** Step number */
  number: number;
  /** Step description */
  description: string;
  /** Expected outcome */
  expectedOutcome: string;
}

/**
 * AI service options.
 *
 * @doc.type interface
 * @doc.purpose AI service options
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface AIServiceOptions {
  /** API key for AI service */
  apiKey?: string;
  /** Model to use */
  model?: string;
  /** Temperature (0-1) */
  temperature?: number;
  /** Max tokens */
  maxTokens?: number;
}

/**
 * Suggestion request.
 *
 * @doc.type interface
 * @doc.purpose Suggestion request
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface SuggestionRequest {
  /** Component code */
  code: string;
  /** Component name */
  componentName: string;
  /** Component description */
  componentDescription?: string;
  /** Number of suggestions */
  count?: number;
}

/**
 * Quality analysis request.
 *
 * @doc.type interface
 * @doc.purpose Quality analysis request
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface QualityAnalysisRequest {
  /** Requirement title */
  title: string;
  /** Requirement description */
  description: string;
  /** Requirement priority */
  priority: string;
  /** Requirement status */
  status: string;
}

/**
 * Test case generation request.
 *
 * @doc.type interface
 * @doc.purpose Test case generation request
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface TestCaseRequest {
  /** Requirement ID */
  requirementId: string;
  /** Requirement title */
  title: string;
  /** Requirement description */
  description: string;
  /** Number of test cases */
  count?: number;
  /** Test type */
  testType?: 'unit' | 'integration' | 'e2e' | 'acceptance';
}

/**
 * AI prompt template.
 *
 * @doc.type interface
 * @doc.purpose AI prompt template
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface PromptTemplate {
  /** Template name */
  name: string;
  /** Template content */
  content: string;
  /** Template variables */
  variables: string[];
}

/**
 * Service result.
 *
 * @doc.type interface
 * @doc.purpose Service result
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface ServiceResult<T> {
  /** Success flag */
  success: boolean;
  /** Result data */
  data?: T;
  /** Error message */
  error?: string;
  /** Processing time in ms */
  duration: number;
}
