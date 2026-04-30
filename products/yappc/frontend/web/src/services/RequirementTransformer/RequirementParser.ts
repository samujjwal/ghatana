/**
 * Requirement Parser
 *
 * Parses natural language requirements into structured requirement data.
 *
 * @packageDocumentation
 */

import type { RequirementConfig } from 'yappc-config-schema';

/**
 * @doc.type service
 * @doc.purpose Parse natural language requirements into structured data
 * @doc.layer product
 * @doc.pattern Service
 */
export class RequirementParser {
  /**
   * Parse a natural language requirement description into structured data.
   *
   * @param description - Natural language description
   * @returns Parsed requirement data
   */
  parseDescription(description: string): Partial<RequirementConfig> {
    const result: Partial<RequirementConfig> = {
      title: this.extractTitle(description),
      description,
      type: this.inferType(description),
      priority: this.inferPriority(description),
      acceptanceCriteria: this.extractAcceptanceCriteria(description),
    };

    return result;
  }

  /**
   * Extract a title from the description.
   */
  private extractTitle(description: string): string {
    const sentences = description.split(/[.!?]/);
    const firstSentence = sentences[0]?.trim() || description;
    return firstSentence.length > 60 ? firstSentence.substring(0, 60) + '...' : firstSentence;
  }

  /**
   * Infer the requirement type from the description.
   */
  private inferType(description: string): RequirementConfig['type'] {
    const lowerDesc = description.toLowerCase();

    if (lowerDesc.includes('user') && (lowerDesc.includes('interface') || lowerDesc.includes('ui'))) {
      return 'user-interface';
    }
    if (lowerDesc.includes('api') || lowerDesc.includes('endpoint') || lowerDesc.includes('service')) {
      return 'api';
    }
    if (lowerDesc.includes('data') || lowerDesc.includes('database') || lowerDesc.includes('storage')) {
      return 'data';
    }
    if (lowerDesc.includes('security') || lowerDesc.includes('auth') || lowerDesc.includes('permission')) {
      return 'security';
    }
    if (lowerDesc.includes('performance') || lowerDesc.includes('speed') || lowerDesc.includes('latency')) {
      return 'performance';
    }

    return 'functional';
  }

  /**
   * Infer the priority from the description.
   */
  private inferPriority(description: string): RequirementConfig['priority'] {
    const lowerDesc = description.toLowerCase();

    if (lowerDesc.includes('critical') || lowerDesc.includes('urgent') || lowerDesc.includes('must')) {
      return 'critical';
    }
    if (lowerDesc.includes('important') || lowerDesc.includes('should') || lowerDesc.includes('high')) {
      return 'high';
    }
    if (lowerDesc.includes('nice to have') || lowerDesc.includes('low') || lowerDesc.includes('optional')) {
      return 'low';
    }

    return 'medium';
  }

  /**
   * Extract acceptance criteria from the description.
   */
  private extractAcceptanceCriteria(description: string): string[] {
    const criteria: string[] = [];
    const patterns = [
      /(?:given|when|then|and)\s+(.+?)(?:\n|$)/gi,
      /(?:should|must|shall)\s+(.+?)(?:\n|\.|$)/gi,
      /(?:verify|ensure|confirm)\s+(.+?)(?:\n|\.|$)/gi,
    ];

    for (const pattern of patterns) {
      const matches = description.match(pattern);
      if (matches) {
        criteria.push(...matches.map((m) => m.trim()));
      }
    }

    return criteria.length > 0 ? criteria : ['Acceptance criteria to be defined'];
  }
}
