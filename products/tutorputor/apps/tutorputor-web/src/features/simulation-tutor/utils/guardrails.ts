/**
 * Tutor Guardrails
 *
 * Safety and content guardrails for AI tutor responses.
 * Ensures appropriate, pedagogically sound, and safe interactions.
 *
 * @doc.type module
 * @doc.purpose Content safety and pedagogical guardrails for AI tutor
 * @doc.layer product
 * @doc.pattern Policy
 */

// =============================================================================
// Types
// =============================================================================

export interface GuardrailResult {
  passed: boolean;
  violations: GuardrailViolation[];
  sanitizedContent?: string;
  blocked: boolean;
  blockReason?: string;
}

export interface GuardrailViolation {
  type: GuardrailViolationType;
  severity: "low" | "medium" | "high" | "critical";
  message: string;
  location?: string;
}

export type GuardrailViolationType =
  | "inappropriate_content"
  | "off_topic"
  | "answer_spoiler"
  | "harmful_advice"
  | "personal_information"
  | "external_links"
  | "prompt_injection"
  | "excessive_length"
  | "low_confidence";

export interface GuardrailConfig {
  /** Maximum response length in characters */
  maxResponseLength: number;
  /** Minimum confidence threshold for responses */
  minConfidenceThreshold: number;
  /** Whether to block off-topic responses */
  blockOffTopic: boolean;
  /** Whether to allow direct answer reveals */
  allowAnswerReveals: boolean;
  /** List of blocked phrases/patterns */
  blockedPatterns: RegExp[];
  /** List of sensitive topics */
  sensitiveTopics: string[];
  /** Age group for content filtering */
  ageGroup: "elementary" | "middle" | "high" | "college" | "adult";
}

// =============================================================================
// Default Configuration
// =============================================================================

export const DEFAULT_GUARDRAIL_CONFIG: GuardrailConfig = {
  maxResponseLength: 2000,
  minConfidenceThreshold: 0.5,
  blockOffTopic: true,
  allowAnswerReveals: false,
  blockedPatterns: [
    /ignore.*previous.*instructions/i,
    /disregard.*system.*prompt/i,
    /pretend.*you.*are/i,
    /act.*as.*if/i,
    /write.*harmful/i,
    /generate.*inappropriate/i,
  ],
  sensitiveTopics: [
    "violence",
    "weapons",
    "drugs",
    "alcohol",
    "self-harm",
    "eating disorders",
    "gambling",
  ],
  ageGroup: "high",
};

// =============================================================================
// Input Guardrails
// =============================================================================

/**
 * Check user input for prompt injection attempts.
 */
export function checkPromptInjection(input: string): GuardrailViolation[] {
  const violations: GuardrailViolation[] = [];

  const injectionPatterns = [
    { pattern: /ignore.*previous.*instructions/i, message: "Prompt injection attempt detected" },
    { pattern: /disregard.*system.*prompt/i, message: "System prompt override attempt" },
    { pattern: /you.*are.*now/i, message: "Role switching attempt" },
    { pattern: /pretend.*you.*are/i, message: "Role impersonation attempt" },
    { pattern: /act.*as.*if/i, message: "Behavior modification attempt" },
    { pattern: /bypass.*safety/i, message: "Safety bypass attempt" },
    { pattern: /jailbreak/i, message: "Jailbreak attempt" },
    { pattern: /DAN.*mode/i, message: "Known jailbreak pattern" },
  ];

  for (const { pattern, message } of injectionPatterns) {
    if (pattern.test(input)) {
      violations.push({
        type: "prompt_injection",
        severity: "critical",
        message,
      });
    }
  }

  return violations;
}

/**
 * Check for personal information in input.
 */
export function checkPersonalInformation(input: string): GuardrailViolation[] {
  const violations: GuardrailViolation[] = [];

  // Email pattern
  if (/\b[\w.-]+@[\w.-]+\.\w+\b/.test(input)) {
    violations.push({
      type: "personal_information",
      severity: "medium",
      message: "Email address detected - avoid sharing personal information",
    });
  }

  // Phone number pattern
  if (/\b\d{3}[-.]?\d{3}[-.]?\d{4}\b/.test(input)) {
    violations.push({
      type: "personal_information",
      severity: "medium",
      message: "Phone number detected - avoid sharing personal information",
    });
  }

  // Social security pattern (US)
  if (/\b\d{3}[-]?\d{2}[-]?\d{4}\b/.test(input)) {
    violations.push({
      type: "personal_information",
      severity: "high",
      message: "Potential SSN detected - never share sensitive personal data",
    });
  }

  return violations;
}

/**
 * Check input for relevance to simulation context.
 */
export function checkTopicRelevance(
  input: string,
  domain: string,
  simulationTitle: string
): GuardrailViolation[] {
  const violations: GuardrailViolation[] = [];

  // Keywords that suggest off-topic queries
  const offTopicPatterns = [
    /tell.*joke/i,
    /write.*story/i,
    /play.*game/i,
    /chat.*about/i,
    /what.*weather/i,
    /who.*president/i,
    /what.*news/i,
    /generate.*code.*for/i,
    /write.*essay/i,
    /do.*homework/i,
  ];

  for (const pattern of offTopicPatterns) {
    if (pattern.test(input)) {
      violations.push({
        type: "off_topic",
        severity: "low",
        message: `This question doesn't seem related to the ${domain} simulation. Let's focus on "${simulationTitle}"!`,
      });
      break;
    }
  }

  return violations;
}

/**
 * Validate user input against all guardrails.
 */
export function validateUserInput(
  input: string,
  domain: string,
  simulationTitle: string,
  config: GuardrailConfig = DEFAULT_GUARDRAIL_CONFIG
): GuardrailResult {
  const violations: GuardrailViolation[] = [];

  // Check for prompt injection
  violations.push(...checkPromptInjection(input));

  // Check for personal information
  violations.push(...checkPersonalInformation(input));

  // Check topic relevance (if enabled)
  if (config.blockOffTopic) {
    violations.push(...checkTopicRelevance(input, domain, simulationTitle));
  }

  // Check length
  if (input.length > 1000) {
    violations.push({
      type: "excessive_length",
      severity: "low",
      message: "Please keep your question concise for better responses",
    });
  }

  // Determine if blocked
  const criticalViolations = violations.filter((v) => v.severity === "critical");
  const blocked = criticalViolations.length > 0;

  return {
    passed: violations.length === 0,
    violations,
    blocked,
    blockReason: blocked ? criticalViolations[0].message : undefined,
  };
}

// =============================================================================
// Output Guardrails
// =============================================================================

/**
 * Check response for inappropriate content.
 */
export function checkInappropriateContent(
  content: string,
  ageGroup: GuardrailConfig["ageGroup"]
): GuardrailViolation[] {
  const violations: GuardrailViolation[] = [];

  // Basic profanity filter (simplified - use a proper library in production)
  const basicProfanity = /\b(damn|hell|crap)\b/gi;
  const moderateProfanity = /\b(ass|bastard)\b/gi;

  if (ageGroup === "elementary" || ageGroup === "middle") {
    if (basicProfanity.test(content)) {
      violations.push({
        type: "inappropriate_content",
        severity: "medium",
        message: "Response contains inappropriate language for target audience",
      });
    }
  }

  if (moderateProfanity.test(content)) {
    violations.push({
      type: "inappropriate_content",
      severity: "high",
      message: "Response contains inappropriate language",
    });
  }

  return violations;
}

/**
 * Check if response reveals answers directly (for assessment contexts).
 */
export function checkAnswerSpoilers(
  content: string,
  allowReveals: boolean
): GuardrailViolation[] {
  const violations: GuardrailViolation[] = [];

  if (allowReveals) return violations;

  // Patterns that suggest direct answer reveals
  const answerPatterns = [
    /the.*answer.*is/i,
    /the.*correct.*value.*is/i,
    /you.*should.*enter/i,
    /the.*solution.*equals/i,
    /simply.*calculate.*=.*\d/i,
  ];

  for (const pattern of answerPatterns) {
    if (pattern.test(content)) {
      violations.push({
        type: "answer_spoiler",
        severity: "medium",
        message: "Response may reveal answers directly - encourage exploration instead",
      });
      break;
    }
  }

  return violations;
}

/**
 * Check for potentially harmful advice.
 */
export function checkHarmfulAdvice(content: string): GuardrailViolation[] {
  const violations: GuardrailViolation[] = [];

  // Safety-critical domains (chemistry, medicine, etc.)
  const dangerousPatterns = [
    /mix.*chemicals/i,
    /take.*medication.*without/i,
    /increase.*dosage/i,
    /ignore.*safety/i,
    /bypass.*warning/i,
  ];

  for (const pattern of dangerousPatterns) {
    if (pattern.test(content)) {
      violations.push({
        type: "harmful_advice",
        severity: "high",
        message: "Response may contain potentially harmful advice",
      });
    }
  }

  return violations;
}

/**
 * Check for external links that could lead users away.
 */
export function checkExternalLinks(content: string): GuardrailViolation[] {
  const violations: GuardrailViolation[] = [];

  // URL pattern
  const urlPattern = /https?:\/\/[^\s]+/gi;
  const matches = content.match(urlPattern);

  if (matches) {
    // Allow certain trusted domains
    const trustedDomains = [
      "wikipedia.org",
      "khanacademy.org",
      "edu",
      "tutorputor.com",
    ];

    for (const url of matches) {
      const isTrusted = trustedDomains.some((domain) => url.includes(domain));
      if (!isTrusted) {
        violations.push({
          type: "external_links",
          severity: "low",
          message: `External link detected: ${url}`,
          location: url,
        });
      }
    }
  }

  return violations;
}

/**
 * Validate tutor response against all guardrails.
 */
export function validateTutorResponse(
  content: string,
  confidence: number,
  config: GuardrailConfig = DEFAULT_GUARDRAIL_CONFIG
): GuardrailResult {
  const violations: GuardrailViolation[] = [];

  // Check confidence threshold
  if (confidence < config.minConfidenceThreshold) {
    violations.push({
      type: "low_confidence",
      severity: "medium",
      message: `Response confidence (${(confidence * 100).toFixed(0)}%) is below threshold`,
    });
  }

  // Check length
  if (content.length > config.maxResponseLength) {
    violations.push({
      type: "excessive_length",
      severity: "low",
      message: `Response exceeds maximum length (${config.maxResponseLength} chars)`,
    });
  }

  // Check inappropriate content
  violations.push(...checkInappropriateContent(content, config.ageGroup));

  // Check answer spoilers
  violations.push(...checkAnswerSpoilers(content, config.allowAnswerReveals));

  // Check harmful advice
  violations.push(...checkHarmfulAdvice(content));

  // Check external links
  violations.push(...checkExternalLinks(content));

  // Sanitize if needed
  let sanitizedContent = content;
  if (content.length > config.maxResponseLength) {
    sanitizedContent = content.slice(0, config.maxResponseLength) + "...";
  }

  // Determine if blocked
  const criticalViolations = violations.filter((v) => v.severity === "critical" || v.severity === "high");
  const blocked = criticalViolations.length > 0;

  return {
    passed: violations.length === 0,
    violations,
    sanitizedContent,
    blocked,
    blockReason: blocked ? criticalViolations[0].message : undefined,
  };
}

// =============================================================================
// Pedagogical Guardrails
// =============================================================================

/**
 * Ensure response follows Socratic method (asks guiding questions).
 */
export function checkSocraticApproach(content: string): {
  hasSocraticQuestions: boolean;
  suggestions: string[];
} {
  const questionPatterns = [
    /what.*do.*you.*think/i,
    /why.*do.*you.*believe/i,
    /how.*would.*you/i,
    /can.*you.*explain/i,
    /what.*happens.*if/i,
    /have.*you.*considered/i,
    /what.*evidence/i,
  ];

  const hasSocraticQuestions = questionPatterns.some((p) => p.test(content));

  const suggestions: string[] = [];
  if (!hasSocraticQuestions) {
    suggestions.push("Consider adding guiding questions to encourage critical thinking");
    suggestions.push("Ask 'What do you think would happen if...' to promote exploration");
  }

  return { hasSocraticQuestions, suggestions };
}

/**
 * Check if response includes scaffolding elements.
 */
export function checkScaffolding(content: string): {
  hasScaffolding: boolean;
  scaffoldingTypes: string[];
} {
  const scaffoldingPatterns = [
    { pattern: /first.*then/i, type: "step-by-step" },
    { pattern: /let's.*break.*down/i, type: "decomposition" },
    { pattern: /for.*example/i, type: "example" },
    { pattern: /imagine.*that/i, type: "analogy" },
    { pattern: /think.*of.*it.*as/i, type: "metaphor" },
    { pattern: /hint:/i, type: "hint" },
    { pattern: /remember.*that/i, type: "recall" },
  ];

  const scaffoldingTypes = scaffoldingPatterns
    .filter(({ pattern }) => pattern.test(content))
    .map(({ type }) => type);

  return {
    hasScaffolding: scaffoldingTypes.length > 0,
    scaffoldingTypes,
  };
}

export default {
  validateUserInput,
  validateTutorResponse,
  checkSocraticApproach,
  checkScaffolding,
  DEFAULT_GUARDRAIL_CONFIG,
};
