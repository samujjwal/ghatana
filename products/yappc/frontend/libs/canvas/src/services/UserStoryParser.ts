/**
 * @doc.type service
 * @doc.purpose User story parser service for Journey 21.1 (Product Designer - Requirement to Wireframe)
 * @doc.layer product
 * @doc.pattern Service
 */

/**
 * Parsed wireframe element
 */
export interface WireframeElement {
    id: string;
    type: 'screen' | 'component' | 'data' | 'action';
    label: string;
    description?: string;
    props?: Record<string, unknown>;
}

/**
 * Business rule definition
 */
export interface BusinessRule {
    id: string;
    description: string;
    condition: string;
    action: string;
    appliesTo: string[]; // Element IDs
}

/**
 * Flow step definition
 */
export interface FlowStep {
    id: string;
    elementId: string;
    description: string;
    order: number;
}

/**
 * Parsed user story result
 */
export interface ParsedUserStory {
    title: string;
    description: string;
    actor: string;
    goal: string;
    elements: WireframeElement[];
    rules: BusinessRule[];
    flow: FlowStep[];
}

/**
 * User Story Parser Service
 * 
 * Parses user stories in the format:
 * "As a [actor], I want to [goal], so that [benefit]"
 * 
 * Extracts UI elements, business rules, and workflow steps.
 */
export class UserStoryParser {
    /**
     * Parse a user story into wireframe elements
     */
    static parseUserStory(userStory: string): ParsedUserStory {
        const lines = userStory.trim().split('\n').map(l => l.trim()).filter(Boolean);

        // Parse actor and goal from first line
        const { actor, goal, benefit } = this.extractActorGoalBenefit(lines[0]);

        // Extract elements, rules, and flow from remaining lines
        const elements = this.extractElements(userStory);
        const rules = this.extractBusinessRules(userStory);
        const flow = this.generateFlow(elements);

        return {
            title: this.generateTitle(actor, goal),
            description: userStory,
            actor,
            goal,
            elements,
            rules,
            flow,
        };
    }

    /**
     * Extract actor, goal, and benefit from user story format
     */
    private static extractActorGoalBenefit(line: string): {
        actor: string;
        goal: string;
        benefit: string;
    } {
        // Match "As a [actor], I want to [goal], so that [benefit]"
        const asAMatch = line.match(/As\s+a\s+([^,]+),\s+I\s+want\s+to\s+([^,]+)(?:,\s+so\s+that\s+(.+))?/i);

        if (asAMatch) {
            return {
                actor: asAMatch[1].trim(),
                goal: asAMatch[2].trim(),
                benefit: asAMatch[3]?.trim() || '',
            };
        }

        // Fallback: try to extract any meaningful parts
        return {
            actor: 'User',
            goal: line,
            benefit: '',
        };
    }

    /**
     * Extract UI elements from user story
     */
    private static extractElements(story: string): WireframeElement[] {
        const elements: WireframeElement[] = [];
        const lowerStory = story.toLowerCase();

        // Common UI patterns
        const patterns: Array<{
            regex: RegExp;
            type: 'screen' | 'component';
            labelExtractor: (match: RegExpMatchArray) => string;
        }> = [
                {
                    regex: /\b(view|screen|page|form|modal|dialog)\s+(?:for\s+)?([a-z\s]+)/gi,
                    type: 'screen',
                    labelExtractor: (m) => m[2].trim(),
                },
                {
                    regex: /\b(search|filter|sort|select|choose)\s+([a-z\s]+)/gi,
                    type: 'component',
                    labelExtractor: (m) => `${m[1]} ${m[2]}`.trim(),
                },
                {
                    regex: /\b(button|input|field|dropdown|checkbox|radio|slider|toggle)\s*(?:for\s+)?([a-z\s]*)/gi,
                    type: 'component',
                    labelExtractor: (m) => m[2] ? `${m[2]} ${m[1]}` : m[1],
                },
                {
                    regex: /\b(list|grid|table|chart|graph)\s+of\s+([a-z\s]+)/gi,
                    type: 'component',
                    labelExtractor: (m) => `${m[2]} ${m[1]}`.trim(),
                },
            ];

        let idCounter = 1;
        patterns.forEach(({ regex, type, labelExtractor }) => {
            const matches = [...story.matchAll(regex)];
            matches.forEach(match => {
                const label = this.capitalize(labelExtractor(match));
                if (label && !elements.some(e => e.label === label)) {
                    elements.push({
                        id: `element-${idCounter++}`,
                        type,
                        label,
                        description: match[0],
                    });
                }
            });
        });

        // If no elements found, create a default screen
        if (elements.length === 0) {
            elements.push({
                id: 'element-1',
                type: 'screen',
                label: 'Main Screen',
                description: 'Primary user interface',
            });
        }

        return elements;
    }

    /**
     * Extract business rules from user story
     */
    private static extractBusinessRules(story: string): BusinessRule[] {
        const rules: BusinessRule[] = [];
        const lines = story.split('\n');

        let ruleId = 1;

        // Look for conditional statements
        const conditionalPatterns = [
            /if\s+(.+?)\s+then\s+(.+?)(?:\.|$)/gi,
            /when\s+(.+?),\s+(.+?)(?:\.|$)/gi,
            /unless\s+(.+?),\s+(.+?)(?:\.|$)/gi,
            /only\s+if\s+(.+?)(?:\.|$)/gi,
        ];

        conditionalPatterns.forEach(pattern => {
            const matches = [...story.matchAll(pattern)];
            matches.forEach(match => {
                const condition = match[1]?.trim() || match[0];
                const action = match[2]?.trim() || 'perform action';

                rules.push({
                    id: `rule-${ruleId++}`,
                    description: match[0],
                    condition,
                    action,
                    appliesTo: [], // Will be linked during flow generation
                });
            });
        });

        // Look for constraint keywords
        const constraintKeywords = ['must', 'should', 'cannot', 'must not', 'required', 'mandatory'];
        lines.forEach(line => {
            const lowerLine = line.toLowerCase();
            if (constraintKeywords.some(keyword => lowerLine.includes(keyword))) {
                rules.push({
                    id: `rule-${ruleId++}`,
                    description: line.trim(),
                    condition: 'always',
                    action: line.trim(),
                    appliesTo: [],
                });
            }
        });

        return rules;
    }

    /**
     * Generate flow steps from elements
     */
    private static generateFlow(elements: WireframeElement[]): FlowStep[] {
        const flow: FlowStep[] = [];

        elements.forEach((element, index) => {
            flow.push({
                id: `step-${index + 1}`,
                elementId: element.id,
                description: `User interacts with ${element.label}`,
                order: index + 1,
            });
        });

        return flow;
    }

    /**
     * Generate title from actor and goal
     */
    private static generateTitle(actor: string, goal: string): string {
        const shortGoal = goal.split(' ').slice(0, 5).join(' ');
        return `${this.capitalize(actor)} - ${this.capitalize(shortGoal)}`;
    }

    /**
     * Capitalize first letter of each word
     */
    private static capitalize(text: string): string {
        return text
            .split(' ')
            .map(word => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
            .join(' ');
    }

    /**
     * Validate user story format
     */
    static validateUserStory(story: string): {
        valid: boolean;
        errors: string[];
        warnings: string[];
    } {
        const errors: string[] = [];
        const warnings: string[] = [];

        if (!story || story.trim().length === 0) {
            errors.push('User story cannot be empty');
        }

        const hasAsA = /as\s+a/i.test(story);
        const hasIWant = /i\s+want/i.test(story);

        if (!hasAsA) {
            warnings.push('User story should start with "As a [role]"');
        }

        if (!hasIWant) {
            warnings.push('User story should include "I want to [goal]"');
        }

        if (story.length < 20) {
            warnings.push('User story seems too short. Consider adding more details.');
        }

        if (story.length > 500) {
            warnings.push('User story is quite long. Consider breaking it into multiple stories.');
        }

        return {
            valid: errors.length === 0,
            errors,
            warnings,
        };
    }

    /**
     * Generate acceptance criteria from user story
     */
    static generateAcceptanceCriteria(parsed: ParsedUserStory): string[] {
        const criteria: string[] = [];

        // Criteria from elements
        parsed.elements.forEach(element => {
            if (element.type === 'screen') {
                criteria.push(`GIVEN the ${element.label} is displayed`);
            } else if (element.type === 'component') {
                criteria.push(`WHEN user interacts with ${element.label}`);
            }
        });

        // Criteria from rules
        parsed.rules.forEach(rule => {
            criteria.push(`THEN ${rule.action}`);
        });

        // Default criteria if none generated
        if (criteria.length === 0) {
            criteria.push(`GIVEN the ${parsed.actor} is authenticated`);
            criteria.push(`WHEN they ${parsed.goal}`);
            criteria.push(`THEN the action completes successfully`);
        }

        return criteria;
    }

    /**
     * Estimate complexity from parsed story
     */
    static estimateComplexity(parsed: ParsedUserStory): {
        score: number;
        level: 'low' | 'medium' | 'high' | 'very-high';
        factors: string[];
    } {
        const factors: string[] = [];
        let score = 0;

        // Factor 1: Number of elements
        const elementCount = parsed.elements.length;
        score += elementCount * 2;
        if (elementCount > 5) {
            factors.push(`${elementCount} UI elements (high)`);
        } else if (elementCount > 3) {
            factors.push(`${elementCount} UI elements (medium)`);
        }

        // Factor 2: Number of business rules
        const ruleCount = parsed.rules.length;
        score += ruleCount * 3;
        if (ruleCount > 3) {
            factors.push(`${ruleCount} business rules (complex)`);
        } else if (ruleCount > 0) {
            factors.push(`${ruleCount} business rules`);
        }

        // Factor 3: Flow complexity
        const flowCount = parsed.flow.length;
        score += flowCount;
        if (flowCount > 5) {
            factors.push(`${flowCount} flow steps (complex workflow)`);
        }

        // Determine level
        let level: 'low' | 'medium' | 'high' | 'very-high';
        if (score < 10) level = 'low';
        else if (score < 20) level = 'medium';
        else if (score < 30) level = 'high';
        else level = 'very-high';

        return {
            score,
            level,
            factors,
        };
    }
}
