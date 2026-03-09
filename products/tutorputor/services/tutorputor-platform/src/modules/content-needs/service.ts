/**
 * @doc.type module
 * @doc.purpose Content Needs Analyzer for automatic claim-to-content linkage
 * @doc.layer product
 * @doc.pattern Service
 */

import type { PrismaClient } from '@ghatana/tutorputor-db';
import type { ContentStudioService } from '@ghatana/tutorputor-contracts/v1';
import type { ContentNeeds, ExampleType } from '@ghatana/tutorputor-contracts/v1/learning-unit';

// Re-export types from contracts for backwards compatibility
export type { ContentNeeds, ExampleType };

// ============================================================================
// Local Types (specific to this module)
// ============================================================================

export interface ContentContext {
    domain: string;
    gradeRange: string;
    subject: string;
    topic: string;
    prerequisites: string[];
    learningObjectives: string[];
}

export interface ClaimAnalysis {
    claimId: string;
    claimText: string;
    bloomLevel: string;
    needs: ContentNeeds;
    confidence: number;
    reasoning: string;
    suggestions: string[];
}

// Type aliases for backwards compatibility - map to contracts types
export type InteractionType =
    | 'parameter_exploration'
    | 'guided_discovery'
    | 'open_ended'
    | 'structured_practice'
    | 'assessment'
    | 'demonstration';

export type AnimationType =
    | 'process_visualization'
    | 'timeline'
    | 'spatial_relationship'
    | 'cause_effect'
    | 'transformation'
    | 'comparison';

// ============================================================================
// Content Needs Analyzer Service
// ============================================================================

export class ContentNeedsAnalyzer {
    constructor(
        private readonly prisma: PrismaClient,
        private readonly contentStudio: ContentStudioService
    ) { }

    /**
     * Analyze content needs for a specific claim
     */
    async analyzeClaimNeeds(
        claim: { id: string; text: string; bloomLevel: string },
        context: ContentContext
    ): Promise<ContentNeeds> {
        const needs: ContentNeeds = {
            examples: { required: false, types: [], count: 0, complexity: 'simple', scaffolding: 'medium' },
            simulation: { required: false, interactionType: 'demonstration', complexity: 'basic', estimatedTimeMinutes: 5, entities: [] },
            animation: { required: false, type: 'process_visualization', durationSeconds: 30, complexity: 'simple' },
        };

        // Bloom's level heuristics
        this.applyBloomLevelHeuristics(claim.bloomLevel, needs);

        // Domain-specific heuristics
        this.applyDomainHeuristics(context.domain, claim.text, needs);

        // Grade-level heuristics
        this.applyGradeLevelHeuristics(context.gradeRange, needs);

        // Concept type analysis
        await this.analyzeConceptType(claim.text, needs);

        // Prerequisite analysis
        this.analyzePrerequisites(context.prerequisites, needs);

        return needs;
    }

    /**
     * Analyze content needs for all claims in a learning experience
     */
    async analyzeExperienceNeeds(experienceId: string): Promise<ClaimAnalysis[]> {
        const experience = await this.prisma.learningExperience.findUnique({
            where: { id: experienceId },
            include: { claims: true },
        });

        if (!experience) {
            throw new Error(`Experience not found: ${experienceId}`);
        }

        const context: ContentContext = {
            domain: experience.domain,
            gradeRange: this.extractGradeRange(experience),
            subject: experience.title,
            topic: experience.conceptId || experience.title,
            prerequisites: [],
            learningObjectives: [],
        };

        const analyses: ClaimAnalysis[] = [];

        for (const claim of experience.claims) {
            const needs = await this.analyzeClaimNeeds(
                { id: claim.id, text: claim.text, bloomLevel: claim.bloomLevel },
                context
            );

            const confidence = this.calculateConfidence(claim.text, needs, context);
            const reasoning = this.generateReasoning(claim.text, needs, context);
            const suggestions = this.generateSuggestions(needs);

            analyses.push({
                claimId: claim.id,
                claimText: claim.text,
                bloomLevel: claim.bloomLevel,
                needs,
                confidence,
                reasoning,
                suggestions,
            });

            await this.prisma.learningClaim.update({
                where: { id: claim.id },
                data: {
                    contentNeeds: needs as any,
                },
            });
        }

        return analyses;
    }

    /**
     * Generate content based on analyzed needs
     */
    async generateContentForClaim(
        claimId: string,
        needs: ContentNeeds
    ): Promise<{
        examples: any[];
        simulation?: any;
        animation?: any;
    }> {
        const claim = await this.prisma.learningClaim.findUnique({
            where: { id: claimId },
            include: { experience: true },
        });

        if (!claim) {
            throw new Error(`Claim not found: ${claimId}`);
        }

        const result: {
            examples: any[];
            simulation?: any;
            animation?: any;
        } = {
            examples: [],
        };

        // Generate examples if needed
        if (needs.examples.required && needs.examples.count > 0) {
            result.examples = await this.persistExamples(claim, needs);
        }

        // Generate simulation if needed
        if (needs.simulation.required) {
            result.simulation = await this.persistSimulation(claim, needs);
        }

        // Generate animation if needed
        if (needs.animation.required) {
            result.animation = await this.persistAnimation(claim, needs);
        }

        return result;
    }

    async getAnalysisHistory(experienceId: string): Promise<any[]> {
        const claims = await this.prisma.learningClaim.findMany({
            where: { experienceId },
            orderBy: { orderIndex: 'asc' },
            select: {
                id: true,
                claimRef: true,
                text: true,
                bloomLevel: true,
                contentNeeds: true,
                updatedAt: true,
            },
        });

        return claims
            .filter((claim: { contentNeeds: any }) => claim.contentNeeds !== null)
            .map((claim: { id: string; claimRef: string; text: string; bloomLevel: string; contentNeeds: any; updatedAt: Date }) => ({
                claimId: claim.id,
                claimRef: claim.claimRef,
                claimText: claim.text,
                bloomLevel: claim.bloomLevel,
                needs: claim.contentNeeds,
                updatedAt: claim.updatedAt,
            }));
    }

    // ===========================================================================
    // Private Helper Methods
    // ===========================================================================

    private applyBloomLevelHeuristics(bloomLevel: string, needs: ContentNeeds): void {
        const level = bloomLevel.toLowerCase();

        if (level.includes('remember') || level.includes('understand')) {
            // Lower levels need more examples and scaffolding
            needs.examples.required = true;
            needs.examples.count = 3;
            needs.examples.complexity = 'simple';
            needs.examples.scaffolding = 'high';
            needs.examples.types = ['visual_representation', 'step_by_step', 'real_world_application'];
        }

        if (level.includes('apply')) {
            // Application needs hands-on activities
            needs.examples.required = true;
            needs.examples.count = 2;
            needs.examples.complexity = 'moderate';
            needs.examples.scaffolding = 'medium';
            needs.examples.types = ['problem_solving', 'real_world_application'];

            needs.simulation.required = true;
            needs.simulation.interactionType = 'parameter_exploration';
            needs.simulation.complexity = 'intermediate';
        }

        if (level.includes('analyze') || level.includes('evaluate')) {
            // Analysis needs comparison and evaluation
            needs.examples.required = true;
            needs.examples.count = 2;
            needs.examples.complexity = 'moderate';
            needs.examples.scaffolding = 'low';
            needs.examples.types = ['comparison', 'case_study'];

            needs.simulation.required = true;
            needs.simulation.interactionType = 'guided_discovery';
            needs.simulation.complexity = 'advanced';
        }

        if (level.includes('create')) {
            // Creation needs open-ended exploration
            needs.simulation.required = true;
            needs.simulation.interactionType = 'open_ended';
            needs.simulation.complexity = 'advanced';
            needs.simulation.estimatedTimeMinutes = 15;

            needs.examples.required = true;
            needs.examples.count = 1;
            needs.examples.types = ['case_study'];
        }
    }

    private applyDomainHeuristics(domain: string, claimText: string, needs: ContentNeeds): void {
        const domainLower = domain.toLowerCase();
        const claimLower = claimText.toLowerCase();

        // Physics domain
        if (domainLower.includes('physics') || domainLower.includes('science')) {
            if (claimLower.includes('force') || claimLower.includes('motion') || claimLower.includes('energy')) {
                needs.simulation.required = true;
                needs.simulation.entities = ['BALL', 'PLATFORM', 'SPRING', 'RAMP'];
                needs.animation.required = true;
                needs.animation.type = 'process_visualization';
            }
        }

        // Mathematics domain
        if (domainLower.includes('math') || domainLower.includes('algebra') || domainLower.includes('calculus')) {
            needs.examples.required = true;
            needs.examples.types.push('step_by_step');
            needs.examples.types.push('problem_solving');

            if (claimLower.includes('graph') || claimLower.includes('function')) {
                needs.animation.required = true;
                needs.animation.type = 'transformation';
            }
        }

        // Chemistry domain
        if (domainLower.includes('chemistry') || claimLower.includes('molecule') || claimLower.includes('reaction')) {
            needs.simulation.required = true;
            needs.animation.required = true;
            needs.animation.type = 'process_visualization';
            needs.animation.durationSeconds = 60;
        }

        // Biology domain
        if (domainLower.includes('biology') || claimLower.includes('cell') || claimLower.includes('organism')) {
            needs.examples.required = true;
            needs.examples.types.push('visual_representation');
            needs.animation.required = true;
            needs.animation.type = 'spatial_relationship';
        }
    }

    private applyGradeLevelHeuristics(gradeRange: string, needs: ContentNeeds): void {
        const grade = gradeRange.toLowerCase();

        if (grade.includes('k_2') || grade.includes('grade_3_5')) {
            // Elementary grades need more scaffolding and simpler content
            needs.examples.scaffolding = 'high';
            needs.examples.complexity = 'simple';
            needs.simulation.complexity = 'basic';
            needs.animation.complexity = 'simple';
            needs.animation.durationSeconds = 20;
        }

        if (grade.includes('grade_6_8') || grade.includes('grade_9_12')) {
            // Middle and high school can handle moderate complexity
            needs.examples.scaffolding = 'medium';
            needs.examples.complexity = 'moderate';
            needs.simulation.complexity = 'intermediate';
            needs.animation.complexity = 'moderate';
            needs.animation.durationSeconds = 45;
        }

        if (grade.includes('undergraduate') || grade.includes('graduate')) {
            // Higher education can handle complex content
            needs.examples.scaffolding = 'low';
            needs.examples.complexity = 'complex';
            needs.simulation.complexity = 'advanced';
            needs.animation.complexity = 'complex';
            needs.animation.durationSeconds = 90;
        }
    }

    private async analyzeConceptType(claimText: string, needs: ContentNeeds): Promise<void> {
        const text = claimText.toLowerCase();

        // Temporal concepts need animations
        if (text.includes('process') || text.includes('cycle') || text.includes('sequence') ||
            text.includes('over time') || text.includes('step') || text.includes('phase')) {
            needs.animation.required = true;
            needs.animation.type = 'timeline';
        }

        // Spatial concepts need animations
        if (text.includes('relationship') || text.includes('position') || text.includes('structure') ||
            text.includes('arrangement') || text.includes('layout') || text.includes('space')) {
            needs.animation.required = true;
            needs.animation.type = 'spatial_relationship';
        }

        // Causal concepts need animations
        if (text.includes('cause') || text.includes('effect') || text.includes('because') ||
            text.includes('leads to') || text.includes('results in') || text.includes('influence')) {
            needs.animation.required = true;
            needs.animation.type = 'cause_effect';
        }

        // Comparative concepts need examples
        if (text.includes('compare') || text.includes('difference') || text.includes('similar') ||
            text.includes('versus') || text.includes('better') || text.includes('worse')) {
            needs.examples.required = true;
            needs.examples.types.push('comparison');
        }

        // Problem-solving concepts need examples
        if (text.includes('solve') || text.includes('calculate') || text.includes('determine') ||
            text.includes('find') || text.includes('compute')) {
            needs.examples.required = true;
            needs.examples.types.push('problem_solving');
        }
    }

    private analyzePrerequisites(prerequisites: string[], needs: ContentNeeds): void {
        if (prerequisites.length > 2) {
            // Complex prerequisite chains need more scaffolding
            needs.examples.scaffolding = 'high';
            needs.examples.count += 1;
        }

        if (prerequisites.length === 0) {
            // No prerequisites might indicate an introductory concept
            needs.examples.scaffolding = 'high';
            needs.examples.types.push('step_by_step');
        }
    }

    private calculateConfidence(claimText: string, needs: ContentNeeds, context: ContentContext): number {
        let confidence = 0.7; // Base confidence

        // Increase confidence for clear domain matches
        if (context.domain && this.hasDomainKeywords(claimText, context.domain)) {
            confidence += 0.1;
        }

        // Increase confidence for clear Bloom's level indicators
        if (this.hasBloomKeywords(claimText)) {
            confidence += 0.1;
        }

        // Increase confidence for concept type indicators
        if (this.hasConceptTypeKeywords(claimText)) {
            confidence += 0.1;
        }

        return Math.min(confidence, 1.0);
    }

    private generateReasoning(claimText: string, needs: ContentNeeds, context: ContentContext): string {
        const reasons: string[] = [];

        if (needs.examples.required) {
            reasons.push(`Examples required (${needs.examples.count}x) for ${needs.examples.complexity} complexity with ${needs.examples.scaffolding} scaffolding`);
        }

        if (needs.simulation.required) {
            reasons.push(`Simulation required for ${needs.simulation.interactionType} interaction at ${needs.simulation.complexity} level`);
        }

        if (needs.animation.required) {
            reasons.push(`Animation required (${needs.animation.type}, ${needs.animation.durationSeconds}s) for visualization`);
        }

        if (context.domain) {
            reasons.push(`Domain-specific analysis for ${context.domain}`);
        }

        return reasons.join('. ');
    }

    private generateSuggestions(needs: ContentNeeds): string[] {
        const suggestions: string[] = [];

        if (needs.examples.required) {
            suggestions.push(`Create ${needs.examples.count} ${needs.examples.types.join(', ')} examples`);
        }

        if (needs.simulation.required) {
            suggestions.push(`Design ${needs.simulation.complexity} simulation with ${needs.simulation.interactionType} interaction`);
        }

        if (needs.animation.required) {
            suggestions.push(`Produce ${needs.animation.complexity} ${needs.animation.type} animation`);
        }

        return suggestions;
    }

    // ===========================================================================
    // Content Generation Methods
    // ===========================================================================

    private async persistExamples(claim: any, needs: ContentNeeds): Promise<any[]> {
        const generated = await this.generateExamples(claim, needs);

        await this.prisma.claimExample.deleteMany({
            where: {
                experienceId: claim.experienceId,
                claimRef: claim.claimRef,
            },
        });

        const created: any[] = [];
        let orderIndex = 0;
        for (const example of generated) {
            const row = await this.prisma.claimExample.create({
                data: {
                    experienceId: claim.experienceId,
                    claimRef: claim.claimRef,
                    type: String(example.type).toUpperCase(),
                    title: example.title,
                    description: `${example.type} example for ${claim.text}`,
                    content: {
                        scaffolding: needs.examples.scaffolding,
                        complexity: needs.examples.complexity,
                        body: example.content,
                    },
                    difficulty: needs.examples.complexity === 'simple' ? 'BEGINNER' : needs.examples.complexity === 'complex' ? 'ADVANCED' : 'INTERMEDIATE',
                    orderIndex,
                },
            });
            created.push(row);
            orderIndex += 1;
        }

        return created;
    }

    private async persistSimulation(claim: any, needs: ContentNeeds): Promise<any> {
        const generated = await this.generateSimulation(claim, needs);
        const manifestId = generated.id;

        await this.prisma.simulationManifest.upsert({
            where: { id: manifestId },
            create: {
                id: manifestId,
                tenantId: claim.experience.tenantId,
                domain: claim.experience.domain as any,
                version: '1.0.0',
                title: generated.config.title,
                description: generated.config.description,
                manifest: generated,
            },
            update: {
                title: generated.config.title,
                description: generated.config.description,
                manifest: generated,
            },
        });

        return this.prisma.claimSimulation.upsert({
            where: {
                experienceId_claimRef: {
                    experienceId: claim.experienceId,
                    claimRef: claim.claimRef,
                },
            },
            create: {
                experienceId: claim.experienceId,
                claimRef: claim.claimRef,
                simulationManifestId: manifestId,
                interactionType: generated.interactionType,
                goal: generated.config.description,
                successCriteria: { estimatedTimeMinutes: generated.estimatedTimeMinutes },
                estimatedMinutes: generated.estimatedTimeMinutes,
            },
            update: {
                simulationManifestId: manifestId,
                interactionType: generated.interactionType,
                goal: generated.config.description,
                successCriteria: { estimatedTimeMinutes: generated.estimatedTimeMinutes },
                estimatedMinutes: generated.estimatedTimeMinutes,
            },
        });
    }

    private async persistAnimation(claim: any, needs: ContentNeeds): Promise<any> {
        const generated = await this.generateAnimation(claim, needs);

        return this.prisma.claimAnimation.upsert({
            where: {
                experienceId_claimRef: {
                    experienceId: claim.experienceId,
                    claimRef: claim.claimRef,
                },
            },
            create: {
                experienceId: claim.experienceId,
                claimRef: claim.claimRef,
                title: generated.config.title,
                description: generated.config.description,
                type: generated.type,
                duration: generated.durationSeconds,
                config: generated,
            },
            update: {
                title: generated.config.title,
                description: generated.config.description,
                type: generated.type,
                duration: generated.durationSeconds,
                config: generated,
            },
        });
    }

    private async generateExamples(claim: any, needs: ContentNeeds): Promise<any[]> {
        const examples: any[] = [];

        for (let i = 0; i < needs.examples.count; i++) {
            const type = needs.examples.types[i % needs.examples.types.length];

            // This would integrate with AI service to generate actual examples
            examples.push({
                id: `example-${claim.id}-${i + 1}`,
                type,
                title: `Example ${i + 1} for ${claim.text.substring(0, 50)}...`,
                content: `Generated ${type} example content`,
                scaffolding: needs.examples.scaffolding,
                complexity: needs.examples.complexity,
            });
        }

        return examples;
    }

    private async generateSimulation(claim: any, needs: ContentNeeds): Promise<any> {
        return {
            id: `simulation-${claim.id}`,
            type: 'physics_simulation',
            interactionType: needs.simulation.interactionType,
            complexity: needs.simulation.complexity,
            estimatedTimeMinutes: needs.simulation.estimatedTimeMinutes,
            entities: needs.simulation.entities,
            config: {
                title: `Simulation for ${claim.text}`,
                description: `Interactive simulation to explore ${claim.text}`,
            },
        };
    }

    private async generateAnimation(claim: any, needs: ContentNeeds): Promise<any> {
        return {
            id: `animation-${claim.id}`,
            type: needs.animation.type,
            durationSeconds: needs.animation.durationSeconds,
            complexity: needs.animation.complexity,
            keyframes: [
                { time_ms: 0, description: 'Initial state', properties: {} },
                { time_ms: needs.animation.durationSeconds * 1000, description: 'Final state', properties: {} },
            ],
            config: {
                title: `Animation for ${claim.text}`,
                description: `Visual representation of ${claim.text}`,
            },
        };
    }

    // ===========================================================================
    // Utility Methods
    // ===========================================================================

    private extractGradeRange(experience: any): string {
        // Extract grade range from experience data
        return experience.gradeAdaptations?.[0]?.gradeRange || 'grade_9_12';
    }

    private hasDomainKeywords(claimText: string, domain: string): boolean {
        const domainKeywords: Record<string, string[]> = {
            physics: ['force', 'motion', 'energy', 'velocity', 'acceleration'],
            math: ['calculate', 'solve', 'equation', 'function', 'graph'],
            chemistry: ['molecule', 'reaction', 'bond', 'atom', 'compound'],
            biology: ['cell', 'organism', 'gene', 'protein', 'ecosystem'],
        };

        const keywords = domainKeywords[domain.toLowerCase()] || [];
        const claimLower = claimText.toLowerCase();

        return keywords.some(keyword => claimLower.includes(keyword));
    }

    private hasBloomKeywords(claimText: string): boolean {
        const bloomKeywords = [
            'remember', 'understand', 'apply', 'analyze', 'evaluate', 'create',
            'define', 'explain', 'use', 'compare', 'assess', 'design',
            'list', 'describe', 'implement', 'examine', 'judge', 'construct',
        ];

        const claimLower = claimText.toLowerCase();
        return bloomKeywords.some(keyword => claimLower.includes(keyword));
    }

    private hasConceptTypeKeywords(claimText: string): boolean {
        const conceptKeywords = [
            'process', 'cycle', 'sequence', 'timeline', 'step', 'phase',
            'relationship', 'position', 'structure', 'arrangement', 'space',
            'cause', 'effect', 'because', 'leads to', 'results in',
            'compare', 'difference', 'similar', 'versus',
            'solve', 'calculate', 'determine', 'find', 'compute',
        ];

        const claimLower = claimText.toLowerCase();
        return conceptKeywords.some(keyword => claimLower.includes(keyword));
    }
}
