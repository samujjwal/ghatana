/**
 * Event Pattern type definition
 * Represents a pattern that can match against events
 */
export interface EventPattern {
    id: string;
    name: string;
    description?: string;
    pattern: string;
    conditions: PatternCondition[];
    enabled: boolean;
    createdAt: Date;
    updatedAt: Date;
}
export interface PatternCondition {
    field: string;
    operator: 'equals' | 'contains' | 'startsWith' | 'endsWith' | 'regex' | 'greaterThan' | 'lessThan';
    value: string | number | boolean;
    caseSensitive?: boolean;
}
export interface PatternMatcher {
    matches(pattern: EventPattern, data: Record<string, unknown>): boolean;
    matchesAsync(pattern: EventPattern, data: Record<string, unknown>): Promise<boolean>;
}
//# sourceMappingURL=EventPattern.d.ts.map