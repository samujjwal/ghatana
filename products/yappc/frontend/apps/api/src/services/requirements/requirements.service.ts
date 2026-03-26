/**
 * Requirements Service
 *
 * Provides NLP-based extraction of structured requirements from free-form
 * natural language text. Uses deterministic heuristics so no external AI
 * call is needed for the classification layer.
 *
 * @doc.type class
 * @doc.purpose Natural-language-to-structured-requirement parser
 * @doc.layer product
 * @doc.pattern Service
 */

// ============================================================================
// Types
// ============================================================================

export type RequirementType =
  | 'FUNCTIONAL'
  | 'NON_FUNCTIONAL'
  | 'BUSINESS'
  | 'TECHNICAL';

export type RequirementPriority = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';

export interface ParsedRequirement {
  id: string;
  text: string;
  type: RequirementType;
  priority: RequirementPriority;
  acceptanceCriteria: string[];
  keywords: string[];
}

// ============================================================================
// Keyword dictionaries
// ============================================================================

const FUNCTIONAL_KEYWORDS = [
  'user',
  'users',
  'login',
  'register',
  'upload',
  'download',
  'submit',
  'display',
  'show',
  'list',
  'search',
  'filter',
  'create',
  'update',
  'delete',
  'edit',
  'view',
  'navigate',
  'send',
  'receive',
  'notify',
  'export',
  'import',
  'authenticate',
  'authorize',
];

const NON_FUNCTIONAL_KEYWORDS = [
  'performance',
  'response time',
  'latency',
  'throughput',
  'availability',
  'uptime',
  'reliability',
  'scalability',
  'security',
  'accessibility',
  'usability',
  'maintainability',
  'portability',
  'load',
  'concurrent',
  'faster',
  'within',
  'milliseconds',
  'seconds',
  'percent',
  '%',
];

const BUSINESS_KEYWORDS = [
  'revenue',
  'cost',
  'profit',
  'business',
  'compliance',
  'regulation',
  'legal',
  'contract',
  'sla',
  'agreement',
  'stakeholder',
  'roi',
  'budget',
  'policy',
  'audit',
  'gdpr',
  'hipaa',
  'pci',
];

const TECHNICAL_KEYWORDS = [
  'api',
  'database',
  'schema',
  'architecture',
  'deployment',
  'integration',
  'infrastructure',
  'docker',
  'kubernetes',
  'ci/cd',
  'pipeline',
  'cache',
  'queue',
  'microservice',
  'rest',
  'graphql',
  'websocket',
  'encryption',
  'token',
  'oauth',
];

const CRITICAL_KEYWORDS = [
  'must',
  'critical',
  'essential',
  'mandatory',
  'required',
  'never',
  'always',
  'shall',
];
const HIGH_KEYWORDS = ['should', 'important', 'necessary', 'need'];
const LOW_KEYWORDS = ['nice to have', 'optional', 'may', 'could', 'possibly'];

// ============================================================================
// Helpers
// ============================================================================

/** Generate a short deterministic ID from index for a parsed requirement. */
function makeId(index: number): string {
  return `req-${String(index + 1).padStart(4, '0')}`;
}

/** Tokenise a sentence into lower-cased words for keyword matching. */
function tokenise(text: string): string[] {
  return text.toLowerCase().match(/\b[\w/%-]+\b/g) ?? [];
}

/** Count how many items in `keywords` appear in `tokens`. */
function matchCount(tokens: string[], keywords: string[]): number {
  return keywords.reduce(
    (count, kw) =>
      count + (tokens.some((t) => t.includes(kw.toLowerCase())) ? 1 : 0),
    0
  );
}

/** Classify a sentence into a RequirementType using keyword scoring. */
function classifyType(text: string): RequirementType {
  const tokens = tokenise(text);
  const scores: Record<RequirementType, number> = {
    FUNCTIONAL: matchCount(tokens, FUNCTIONAL_KEYWORDS),
    NON_FUNCTIONAL: matchCount(tokens, NON_FUNCTIONAL_KEYWORDS),
    BUSINESS: matchCount(tokens, BUSINESS_KEYWORDS),
    TECHNICAL: matchCount(tokens, TECHNICAL_KEYWORDS),
  };

  let best: RequirementType = 'FUNCTIONAL';
  let bestScore = -1;
  for (const [type, score] of Object.entries(scores) as [
    RequirementType,
    number,
  ][]) {
    if (score > bestScore) {
      bestScore = score;
      best = type;
    }
  }
  return best;
}

/** Classify the priority of a sentence using modal/signal keywords. */
function classifyPriority(text: string): RequirementPriority {
  const lower = text.toLowerCase();
  if (CRITICAL_KEYWORDS.some((kw) => lower.includes(kw))) return 'CRITICAL';
  if (HIGH_KEYWORDS.some((kw) => lower.includes(kw))) return 'HIGH';
  if (LOW_KEYWORDS.some((kw) => lower.includes(kw))) return 'LOW';
  return 'MEDIUM';
}

/**
 * Extract keywords relevant to the classified type from a sentence.
 * Returns at most 8 distinct keywords to keep the result concise.
 */
function extractKeywords(text: string, type: RequirementType): string[] {
  const lookup: Record<RequirementType, string[]> = {
    FUNCTIONAL: FUNCTIONAL_KEYWORDS,
    NON_FUNCTIONAL: NON_FUNCTIONAL_KEYWORDS,
    BUSINESS: BUSINESS_KEYWORDS,
    TECHNICAL: TECHNICAL_KEYWORDS,
  };
  const tokens = tokenise(text);
  return lookup[type]
    .filter((kw) => tokens.some((t) => t.includes(kw.toLowerCase())))
    .slice(0, 8);
}

/**
 * Generate simple acceptance criteria from a sentence by applying common
 * "given / when / then" scaffolding based on the requirement text.
 */
function generateAcceptanceCriteria(
  text: string,
  type: RequirementType
): string[] {
  const trimmed = text.trim();

  switch (type) {
    case 'FUNCTIONAL':
      return [
        `Given valid input, when the operation is triggered, then the system responds correctly.`,
        `Given invalid input, the system returns a clear error message.`,
        `The feature is accessible to authorised users only.`,
      ];
    case 'NON_FUNCTIONAL':
      return [
        `The system meets the stated performance threshold under normal load.`,
        `Under peak load (2× expected), the system degrades gracefully without data loss.`,
        `Performance metrics are observable via the monitoring dashboard.`,
      ];
    case 'BUSINESS':
      return [
        `The implementation satisfies applicable regulatory or contractual obligations.`,
        `Business stakeholders have reviewed and approved the acceptance criteria.`,
        `Compliance evidence (audit log, report) is produced automatically.`,
      ];
    case 'TECHNICAL':
      return [
        `The implementation follows the documented architectural patterns.`,
        `Integration points are covered by contract tests.`,
        `The solution passes security scanning (OWASP Top 10).`,
      ];
    default:
      return [`${trimmed} is implemented as described.`];
  }
}

/**
 * Split a block of text into individual requirement sentences.
 * Handles full stops, bullet points, numbered lists, and newlines.
 */
function splitIntoSentences(text: string): string[] {
  return text
    .split(/(?:\r?\n|\.(?=\s)|[;!?]|\d+\.\s|\*\s|-\s)/)
    .map((s) => s.trim())
    .filter((s) => s.length > 10); // ignore fragments shorter than 10 chars
}

// ============================================================================
// Service
// ============================================================================

/**
 * Service that converts free-form natural language text into a list of
 * structured requirements ready for project management integration.
 *
 * @doc.type class
 * @doc.purpose NLP-driven requirement parsing
 * @doc.layer product
 * @doc.pattern Service
 */
export class RequirementsService {
  /**
   * Parse natural language text into structured requirement objects.
   *
   * @param text - Free-form description of one or more requirements.
   * @returns Array of parsed requirements ordered by their appearance in the
   *   input text.
   */
  parseNaturalLanguage(text: string): ParsedRequirement[] {
    if (!text || !text.trim()) return [];

    const sentences = splitIntoSentences(text);

    return sentences.map((sentence, index) => {
      const type = classifyType(sentence);
      const priority = classifyPriority(sentence);
      const keywords = extractKeywords(sentence, type);
      const acceptanceCriteria = generateAcceptanceCriteria(sentence, type);

      return {
        id: makeId(index),
        text: sentence,
        type,
        priority,
        acceptanceCriteria,
        keywords,
      };
    });
  }
}

// ============================================================================
// Singleton factory
// ============================================================================

let _requirementsService: RequirementsService | undefined;

/**
 * Returns the process-scoped RequirementsService singleton.
 *
 * @doc.type function
 * @doc.purpose Singleton factory for RequirementsService
 * @doc.layer product
 * @doc.pattern Factory
 */
export function getRequirementsService(): RequirementsService {
  _requirementsService ??= new RequirementsService();
  return _requirementsService;
}
