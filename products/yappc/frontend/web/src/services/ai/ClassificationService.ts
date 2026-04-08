/**
 * Classification Service
 *
 * Provides AI-powered content classification and automatic tag suggestions.
 * Analyzes content to suggest relevant categories and tags.
 *
 * @doc.type service
 * @doc.purpose AI-powered classification and tagging
 * @doc.layer product
 * @doc.pattern Service Layer
 */

// ============================================================================
// Types
// ============================================================================

export interface ClassificationRequest {
  content: string;
  contentType: 'task' | 'document' | 'artifact' | 'comment' | 'message';
  existingTags?: string[];
  context?: {
    projectId?: string;
    phase?: string;
    userId?: string;
  };
}

export interface ClassificationResult {
  category: string;
  confidence: number;
  suggestedTags: TagSuggestion[];
  reasoning: string;
}

export interface TagSuggestion {
  tag: string;
  confidence: number;
  category: 'domain' | 'technology' | 'priority' | 'status' | 'custom';
  reason: string;
}

export interface ClassificationResponse {
  result: ClassificationResult;
  metadata: {
    timestamp: string;
    modelVersion: string;
    processingTime: number;
  };
}

// ============================================================================
// Domain Knowledge Base
// ============================================================================

const DOMAIN_KEYWORDS: Record<string, string[]> = {
  'authentication': ['auth', 'login', 'password', 'oauth', 'jwt', 'session', 'security'],
  'database': ['database', 'db', 'sql', 'nosql', 'query', 'schema', 'migration'],
  'ui': ['ui', 'ux', 'interface', 'design', 'component', 'layout', 'responsive'],
  'api': ['api', 'endpoint', 'rest', 'graphql', 'http', 'request', 'response'],
  'testing': ['test', 'spec', 'unit', 'integration', 'e2e', 'coverage', 'mock'],
  'deployment': ['deploy', 'release', 'ci', 'cd', 'pipeline', 'build', 'production'],
  'performance': ['performance', 'optimize', 'cache', 'latency', 'throughput', 'scale'],
  'security': ['security', 'vulnerability', 'encryption', 'auth', 'permission', 'access'],
};

const TECHNOLOGY_KEYWORDS: Record<string, string[]> = {
  'react': ['react', 'jsx', 'tsx', 'component', 'hook', 'state'],
  'typescript': ['typescript', 'ts', 'type', 'interface', 'generic'],
  'python': ['python', 'py', 'django', 'flask', 'pandas'],
  'java': ['java', 'spring', 'maven', 'gradle'],
  'go': ['go', 'golang', 'goroutine', 'channel'],
  'docker': ['docker', 'container', 'image', 'dockerfile'],
  'kubernetes': ['kubernetes', 'k8s', 'pod', 'deployment', 'service'],
  'aws': ['aws', 'ec2', 's3', 'lambda', 'iam'],
};

const PRIORITY_KEYWORDS: Record<string, string[]> = {
  'critical': ['critical', 'urgent', 'blocker', 'security', 'production'],
  'high': ['high', 'important', 'priority', 'soonest'],
  'medium': ['medium', 'normal', 'standard'],
  'low': ['low', 'minor', 'nice-to-have', 'enhancement'],
};

// ============================================================================
// Classification Algorithms
// ============================================================================

/**
 * Extract keywords from content
 */
function extractKeywords(content: string): string[] {
  const words = content.toLowerCase().split(/\s+/);
  const stopWords = new Set(['the', 'a', 'an', 'is', 'are', 'was', 'were', 'be', 'been', 'being', 'have', 'has', 'had', 'do', 'does', 'did', 'will', 'would', 'could', 'should', 'may', 'might', 'must', 'shall', 'can', 'need', 'dare', 'ought', 'used', 'to', 'of', 'in', 'for', 'on', 'with', 'at', 'by', 'from', 'as', 'into', 'through', 'during', 'before', 'after', 'above', 'below', 'between', 'under', 'again', 'further', 'then', 'once']);
  
  return words.filter(word => word.length > 2 && !stopWords.has(word));
}

/**
 * Calculate match score for keywords
 */
function calculateMatchScore(contentKeywords: string[], targetKeywords: string[]): number {
  const matches = contentKeywords.filter(keyword => 
    targetKeywords.some(target => target.includes(keyword) || keyword.includes(target))
  );
  
  if (matches.length === 0) return 0;
  
  return Math.min(matches.length / Math.max(contentKeywords.length, 1), 1.0);
}

/**
 * Classify content into domain
 */
function classifyDomain(content: string): { category: string; confidence: number } {
  const keywords = extractKeywords(content);
  let bestMatch = 'general';
  let bestScore = 0;

  for (const [domain, domainKeywords] of Object.entries(DOMAIN_KEYWORDS)) {
    const score = calculateMatchScore(keywords, domainKeywords);
    if (score > bestScore) {
      bestMatch = domain;
      bestScore = score;
    }
  }

  return { category: bestMatch, confidence: bestScore };
}

/**
 * Suggest technology tags
 */
function suggestTechnologyTags(content: string, existingTags: string[] = []): TagSuggestion[] {
  const keywords = extractKeywords(content);
  const suggestions: TagSuggestion[] = [];

  for (const [tech, techKeywords] of Object.entries(TECHNOLOGY_KEYWORDS)) {
    if (existingTags.includes(tech)) continue;
    
    const score = calculateMatchScore(keywords, techKeywords);
    if (score > 0.3) {
      suggestions.push({
        tag: tech,
        confidence: score,
        category: 'technology',
        reason: `Content mentions ${techKeywords.slice(0, 2).join(', ')}`,
      });
    }
  }

  return suggestions.sort((a, b) => b.confidence - a.confidence).slice(0, 5);
}

/**
 * Suggest priority tags
 */
function suggestPriorityTags(content: string, existingTags: string[] = []): TagSuggestion[] {
  const keywords = extractKeywords(content);
  const suggestions: TagSuggestion[] = [];

  for (const [priority, priorityKeywords] of Object.entries(PRIORITY_KEYWORDS)) {
    if (existingTags.includes(priority)) continue;
    
    const score = calculateMatchScore(keywords, priorityKeywords);
    if (score > 0.3) {
      suggestions.push({
        tag: priority,
        confidence: score,
        category: 'priority',
        reason: `Content indicates ${priority} priority`,
      });
    }
  }

  return suggestions.sort((a, b) => b.confidence - a.confidence).slice(0, 2);
}

/**
 * Suggest domain tags
 */
function suggestDomainTags(content: string, existingTags: string[] = []): TagSuggestion[] {
  const keywords = extractKeywords(content);
  const suggestions: TagSuggestion[] = [];

  for (const [domain, domainKeywords] of Object.entries(DOMAIN_KEYWORDS)) {
    if (existingTags.includes(domain)) continue;
    
    const score = calculateMatchScore(keywords, domainKeywords);
    if (score > 0.4) {
      suggestions.push({
        tag: domain,
        confidence: score,
        category: 'domain',
        reason: `Content relates to ${domain}`,
      });
    }
  }

  return suggestions.sort((a, b) => b.confidence - a.confidence).slice(0, 3);
}

/**
 * Generate classification reasoning
 */
function generateReasoning(category: string, confidence: number, tagCount: number): string {
  const parts: string[] = [];

  if (confidence > 0.7) {
    parts.push(`Strong match for ${category} domain`);
  } else if (confidence > 0.4) {
    parts.push(`Moderate match for ${category} domain`);
  } else {
    parts.push(`General classification with ${category} as best match`);
  }

  parts.push(`Suggested ${tagCount} tags based on content analysis`);

  return parts.join('. ');
}

// ============================================================================
// Service Implementation
// ============================================================================

/**
 * Classify content and suggest tags
 */
export async function classifyContent(
  request: ClassificationRequest
): Promise<ClassificationResponse> {
  const startTime = performance.now();
  const { content, existingTags = [] } = request;

  // Classify domain
  const domainClassification = classifyDomain(content);

  // Suggest tags
  const technologySuggestions = suggestTechnologyTags(content, existingTags);
  const prioritySuggestions = suggestPriorityTags(content, existingTags);
  const domainSuggestions = suggestDomainTags(content, existingTags);

  // Combine and sort all suggestions
  const allSuggestions = [
    ...technologySuggestions,
    ...prioritySuggestions,
    ...domainSuggestions,
  ].sort((a, b) => b.confidence - a.confidence).slice(0, 8);

  // Generate reasoning
  const reasoning = generateReasoning(
    domainClassification.category,
    domainClassification.confidence,
    allSuggestions.length
  );

  const result: ClassificationResult = {
    category: domainClassification.category,
    confidence: domainClassification.confidence,
    suggestedTags: allSuggestions,
    reasoning,
  };

  const processingTime = performance.now() - startTime;

  return {
    result,
    metadata: {
      timestamp: new Date().toISOString(),
      modelVersion: '1.0.0',
      processingTime,
    },
  };
}

/**
 * Extract tags from existing classification
 */
export function extractTagsFromClassification(
  classification: ClassificationResult,
  threshold: number = 0.5
): string[] {
  return classification.suggestedTags
    .filter(s => s.confidence >= threshold)
    .map(s => s.tag);
}
