/**
 * Production gRPC client for content generation + validation agents.
 * 
 * @doc.type class
 * @doc.purpose gRPC client for content generation service with proper error handling and retry logic
 * @doc.layer backend-worker
 * @doc.pattern ServiceClient
 */

import * as grpc from '@grpc/grpc-js';
import * as protoLoader from '@grpc/proto-loader';
import type { Logger } from 'pino';
import path from 'path';
import { fileURLToPath } from 'url';
import fs from 'fs';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// =============================================================================
// Error Types
// =============================================================================

export class ContentGenerationError extends Error {
    constructor(
        message: string,
        public readonly code: string,
        public readonly details?: Record<string, unknown>
    ) {
        super(message);
        this.name = 'ContentGenerationError';
    }
}

export class ProtoLoadError extends Error {
    constructor(
        message: string,
        public readonly attemptedPaths: string[]
    ) {
        super(message);
        this.name = 'ProtoLoadError';
    }
}

export interface GrpcClientConfig {
    serverAddress: string;
    useTls: boolean;
    timeout: number;
    maxRetries: number;
    logger: Logger;
}

// =============================================================================
// Request Types
// =============================================================================

export interface RequestContext {
    requestId: string;
    tenantId: string;
    timestamp?: Date;
    metadata?: Record<string, string>;
}

export interface GenerateClaimsRequest {
    context: RequestContext;
    topic: string;
    gradeLevel: string;
    domain: string;
    maxClaims: number;
    contextParams?: Record<string, string>;
    language?: string;
}

export interface AnalyzeContentNeedsRequest {
    requestId: string;
    tenantId: string;
    claimText: string;
    bloomLevel: string;
    domain: string;
    gradeLevel: string;
    context: Record<string, string>;
}

export interface GenerateExamplesRequest {
    requestId: string;
    tenantId: string;
    claimText: string;
    claimRef: string;
    exampleTypes: string[];
    count: number;
    domain: string;
    gradeLevel: string;
    context?: Record<string, string>;
}

export interface GenerateSimulationRequest {
    requestId: string;
    tenantId: string;
    claimText: string;
    claimRef: string;
    gradeLevel: string;
    domain: string;
    interactionType: string;
    complexity: string;
    context?: Record<string, string>;
    // Canonical SimKit contract fields
    seed?: number;
    parameterBounds?: Array<{
        parameterId: string;
        label: string;
        min: number;
        max: number;
        defaultValue: number;
        unit?: string;
    }>;
    telemetryEvents?: Array<{
        eventType: "sim.start" | "sim.control.change" | "sim.snapshot" | "sim.capture" | "sim.failure" | "sim.complete";
        required: boolean;
    }>;
    failureStates?: Array<{
        id: string;
        condition: string;
        learnerMessage: string;
        recoverable: boolean;
    }>;
    stateSnapshots?: Array<{
        snapshotId: string;
        description: string;
        triggerCondition: string;
        includeParameters: boolean;
        includeTelemetry: boolean;
    }>;
    exportConfig?: {
        formats: Array<"json" | "csv" | "pdf">;
        includeTelemetry: boolean;
        includeSnapshots: boolean;
        maxExportSizeBytes: number;
        retentionPeriodDays: number;
    };
    claimLinks?: Array<{
        claimId: string;
        evidenceIds: string[];
        taskIds: string[];
    }>;
    accessibility?: {
        altText?: string;
        screenReaderNarration?: boolean;
        reducedMotion?: boolean;
        highContrast?: boolean;
    };
}

export interface GenerateAnimationRequest {
    requestId: string;
    tenantId: string;
    claimText: string;
    claimRef: string;
    animationType: string;
    durationSeconds: number;
    domain: string;
    gradeLevel: string;
    context?: Record<string, string>;
    // Animation enhancement fields
    pedagogicalPurpose?: string;
    claimIds?: string[];
    evidenceIds?: string[];
    transcriptRequired?: boolean;
    captionsRequired?: boolean;
    reducedMotionFallback?: boolean;
    visualDescription?: string;
    durationBounds?: {
        minSeconds: number;
        maxSeconds: number;
    };
    accessibility?: {
        altText?: string;
        screenReaderDescription?: boolean;
        highContrast?: boolean;
        colorblindFriendly?: boolean;
    };
}

export interface ValidateContentRequest {
    requestId: string;
    tenantId: string;
    experienceId: string;
    title: string;
    description: string;
    claimTexts: string[];
    domain: string;
}

export interface GenerateAssessmentRequest {
    requestId: string;
    tenantId: string;
    claimText: string;
    claimRef: string;
    gradeLevel: string;
    domain: string;
    assessmentTypes: Array<"PREDICTION" | "MANIPULATION" | "EXPLANATION" | "CONSTRUCTED_RESPONSE">;
    itemCount: number;
    context?: Record<string, string>;
    // CBM and rubric configuration
    cbmEnabled: boolean;
    includeRubrics: boolean;
    includeDistractorRationales: boolean;
    // Evidence linkage
    claimIds?: string[];
    evidenceIds?: string[];
    // Simulation linkage for simulation-first modules
    simulationBased?: boolean;
}

// =============================================================================
// Response Types
// =============================================================================

export interface ContentNeeds {
    examples: {
        required: boolean;
        types: string[];
        count: number;
        necessity: number;
        rationale: string;
    } | undefined;
    simulation: {
        required: boolean;
        interactionType: string;
        complexity: string;
        necessity: number;
        rationale: string;
    } | undefined;
    animation: {
        required: boolean;
        animationType: string;
        durationSeconds: number;
        necessity: number;
        rationale: string;
    } | undefined;
}

export interface LearningClaim {
    claimRef: string;
    text: string;
    bloomLevel: string;
    orderIndex: number;
    contentNeeds?: ContentNeeds;
}

export interface GenerationMetadata {
    modelName: string;
    tokensUsed: number;
    generationTimeMs: number;
    temperature: number;
    promptHash: string;
    timestamp: string;
}

export interface ValidationResult {
    valid: boolean;
    confidenceScore: number;
    issues: string[];
    suggestions: string[];
}

export interface GenerateClaimsResponse {
    requestId: string;
    claims: LearningClaim[];
    validation: ValidationResult;
    metadata: GenerationMetadata;
}

export interface AnalyzeContentNeedsResponse {
    requestId: string;
    contentNeeds: ContentNeeds;
    metadata: GenerationMetadata;
}

export interface Example {
    exampleId: string;
    type: string;
    title: string;
    description: string;
    content: string;
    tags: string[];
    relevanceScore: number;
}

export interface GenerateExamplesResponse {
    requestId: string;
    examples: Example[];
    metadata: GenerationMetadata;
}

export interface AssessmentItem {
    itemId: string;
    type: string;
    prompt: string;
    guidance: string;
    solution: string;
    cbm: {
        confidenceRequired: boolean;
        confidenceLevels: Array<{
            level: number;
            label: string;
            score: number;
        }>;
        scoringPolicy: string;
    };
    rubric: {
        criteria: Array<{
            id: string;
            description: string;
            maxPoints: number;
            levels: Array<{
                points: number;
                description: string;
            }>;
        }>;
    };
    answerKey: {
        correctAnswer: string;
        alternativeAnswers: string[];
        partialCreditRules: string[];
    };
    distractorRationales: Array<{
        option: string;
        rationale: string;
    }> | undefined;
    claimIds: string[];
    evidenceIds: string[];
    simulationBased: boolean | undefined;
    simulationTaskId: string | undefined;
}

export interface GenerateAssessmentResponse {
    requestId: string;
    assessments: AssessmentItem[];
    metadata: GenerationMetadata;
}

export interface SimulationManifest {
    manifestId: string;
    version: string;
    domain: string;
    title: string;
    description: string;
    domainConfig: string;
}

export interface GenerateSimulationResponse {
    requestId: string;
    manifest: SimulationManifest;
    metadata: GenerationMetadata;
}

export interface AnimationManifest {
    manifestId: string;
    version: string;
    title: string;
    description: string;
    durationSeconds: number;
}

export interface GenerateAnimationResponse {
    requestId: string;
    manifest: AnimationManifest;
    metadata: GenerationMetadata;
}

export interface ValidationIssue {
    issueId: string;
    dimension: string;
    severity: string;
    message: string;
    suggestion: string;
}

export interface ValidateContentResponse {
    requestId: string;
    status: 'valid' | 'invalid' | 'warning';
    overallScore: number;
    canPublish: boolean;
    dimensionScores: Record<string, number>;
    issues: ValidationIssue[];
    issueCount: number;
    metadata: GenerationMetadata;
}

// =============================================================================
// Normalization Functions
// =============================================================================

function normalizeGradeLevel(input: string): string {
    const normalized = String(input || '').toUpperCase().replace(/\s+/g, '_');
    const mapping: Record<string, string> = {
        K_2: 'K_2',
        'K-2': 'K_2',
        GRADE_3_5: 'GRADE_3_5',
        '3-5': 'GRADE_3_5',
        GRADE_6_8: 'GRADE_6_8',
        '6-8': 'GRADE_6_8',
        GRADE_9_12: 'GRADE_9_12',
        '9-12': 'GRADE_9_12',
        UNDERGRADUATE: 'UNDERGRADUATE',
        GRADUATE: 'GRADUATE',
        PROFESSIONAL: 'GRADUATE',
    };
    return mapping[normalized] || 'GRADE_6_8';
}

function normalizeDomain(input: string): string {
    const normalized = String(input || '').toUpperCase().replace(/[-\s]/g, '_');
    const mapping: Record<string, string> = {
        // Full TutorPutor domain set
        MATH: 'MATHEMATICS',
        MATHEMATICS: 'MATHEMATICS',
        SCIENCE: 'SCIENCE',
        TECH: 'TECH',
        ENGINEERING: 'ENGINEERING',
        MEDICINE: 'MEDICINE',
        HEALTH: 'HEALTH',
        BUSINESS: 'BUSINESS',
        MANAGEMENT: 'MANAGEMENT',
        ECONOMICS: 'ECONOMICS',
        COMPUTER_SCIENCE: 'COMPUTER_SCIENCE',
        INTERDISCIPLINARY: 'INTERDISCIPLINARY',
        
        // Alternative mappings for backward compatibility
        CS: 'COMPUTER_SCIENCE',
        CS_DISCRETE: 'COMPUTER_SCIENCE',
        'CS-DISCRETE': 'COMPUTER_SCIENCE',
        PHYSICS: 'SCIENCE',
        CHEMISTRY: 'SCIENCE',
        BIOLOGY: 'SCIENCE',
        
        // Legacy fallbacks (prefer explicit domain selection)
        ARTS: 'INTERDISCIPLINARY',
        LANGUAGE: 'INTERDISCIPLINARY',
        GENERAL: 'INTERDISCIPLINARY',
    };
    return mapping[normalized] || 'INTERDISCIPLINARY';
}

function normalizeBloom(input: string): string {
    const normalized = String(input || '').toUpperCase();
    const allowed = ['REMEMBER', 'UNDERSTAND', 'APPLY', 'ANALYZE', 'EVALUATE', 'CREATE'];
    return allowed.includes(normalized) ? normalized : 'UNDERSTAND';
}

function normalizeExampleType(input: string): string {
    const normalized = String(input || '').toUpperCase();
    const mapping: Record<string, string> = {
        REAL_WORLD: 'REAL_WORLD',
        REAL_WORLD_APPLICATION: 'REAL_WORLD',
        PROBLEM_SOLVING: 'PROBLEM_SOLVING',
        ANALOGY: 'ANALOGY',
        CASE_STUDY: 'CASE_STUDY',
        VISUAL_REPRESENTATION: 'ANALOGY',
        STEP_BY_STEP: 'PROBLEM_SOLVING',
        COMPARISON: 'ANALOGY',
    };
    return mapping[normalized] || 'REAL_WORLD';
}

function normalizeInteractionType(input: string): string {
    const normalized = String(input || '').toUpperCase();
    const mapping: Record<string, string> = {
        PARAMETER_EXPLORATION: 'PARAMETER_EXPLORATION',
        PREDICTION: 'PREDICTION',
        CONSTRUCTION: 'CONSTRUCTION',
        GUIDED_DISCOVERY: 'PREDICTION',
        OPEN_ENDED: 'CONSTRUCTION',
        DEMONSTRATION: 'PARAMETER_EXPLORATION',
    };
    return mapping[normalized] || 'PARAMETER_EXPLORATION';
}

function normalizeComplexity(input: string): string {
    const normalized = String(input || '').toUpperCase();
    const mapping: Record<string, string> = {
        LOW: 'LOW',
        BASIC: 'LOW',
        MEDIUM: 'MEDIUM',
        INTERMEDIATE: 'MEDIUM',
        HIGH: 'HIGH',
        ADVANCED: 'HIGH',
    };
    return mapping[normalized] || 'MEDIUM';
}

function normalizeAnimationType(input: string): string {
    const normalized = String(input || '').toUpperCase();
    const mapping: Record<string, string> = {
        TWO_D: 'TWO_D',
        '2D': 'TWO_D',
        THREE_D: 'THREE_D',
        '3D': 'THREE_D',
        TIMELINE: 'TIMELINE',
        PROCESS_VISUALIZATION: 'TIMELINE',
    };
    return mapping[normalized] || 'TWO_D';
}

/**
 * Resolve the canonical proto file path.
 * Uses the authoritative contract location at contracts/proto/
 */
function resolveProtoPath(file: string): string {
    const candidates = [
        // Primary: contracts/proto (authoritative location)
        path.resolve(__dirname, '../../../../../contracts/proto', file),
        // Secondary: libs/tutorputor-ai (Java agent source)
        path.resolve(__dirname, '../../../../../libs/tutorputor-ai/src/agents/main/proto', file),
        // Fallback: process.cwd() variations
        path.resolve(process.cwd(), 'products/tutorputor/contracts/proto', file),
        path.resolve(process.cwd(), 'contracts/proto', file),
    ];

    const found = candidates.find((candidate) => fs.existsSync(candidate));
    if (!found) {
        throw new ProtoLoadError(
            `Proto file not found: ${file}. Checked paths:`,
            candidates
        );
    }

    return found;
}

export class RealContentGenerationClient {
    private contentClient: grpc.Client | null = null;
    private logger: Logger;
    private timeout: number;
    private maxRetries: number;
    private isReady = false;
    private protoPath: string | null = null;

    constructor(config: GrpcClientConfig) {
        this.logger = config.logger;
        this.timeout = config.timeout;
        this.maxRetries = config.maxRetries;
        this.initializeClient(config);
    }

    /**
     * Initialize the gRPC client with proper error handling and validation.
     */
    private initializeClient(config: GrpcClientConfig): void {
        try {
            this.protoPath = resolveProtoPath('content_generation.proto');
            const includeDir = path.dirname(this.protoPath);

            this.logger.info(
                { protoPath: this.protoPath, includeDir },
                'Loading proto definition from authoritative location'
            );

            const packageDefinition = protoLoader.loadSync(this.protoPath, {
                keepCase: true,
                longs: String,
                enums: String,
                defaults: true,
                oneofs: true,
                includeDirs: [includeDir],
            });

            // Verify proto loaded successfully
            const descriptor = grpc.loadPackageDefinition(packageDefinition);
            
            // Navigate to the correct package path
            const tutorputorPkg = descriptor.tutorputor as Record<string, unknown> | undefined;
            if (!tutorputorPkg) {
                throw new ProtoLoadError(
                    'Package "tutorputor" not found in proto definition',
                    [this.protoPath]
                );
            }

            const contentGenerationPkg = tutorputorPkg.content_generation as Record<string, unknown> | undefined;
            if (!contentGenerationPkg) {
                throw new ProtoLoadError(
                    'Package "tutorputor.content_generation" not found in proto definition',
                    [this.protoPath]
                );
            }

            const ContentGenerationService = contentGenerationPkg.ContentGenerationService as grpc.ServiceClientConstructor | undefined;
            if (!ContentGenerationService) {
                throw new ProtoLoadError(
                    'Service "ContentGenerationService" not found in proto definition',
                    [this.protoPath]
                );
            }

            const credentials = config.useTls
                ? grpc.credentials.createSsl()
                : grpc.credentials.createInsecure();

            this.contentClient = new ContentGenerationService(
                config.serverAddress,
                credentials,
            );

            this.isReady = true;
            
            this.logger.info(
                { serverAddress: config.serverAddress, useTls: config.useTls },
                'gRPC client initialized successfully'
            );
        } catch (e) {
            this.logger.error({ err: e }, 'Failed to initialize gRPC client');
            this.isReady = false;
            throw e;
        }
    }

    /**
     * Check if the client is ready to make requests.
     */
    get ready(): boolean {
        return this.isReady && this.contentClient !== null;
    }

    /**
     * Get the resolved proto file path for diagnostics.
     */
    getProtoPath(): string | null {
        return this.protoPath;
    }

    async generateClaims(request: GenerateClaimsRequest): Promise<any> {
        if (!this.isReady) throw new Error('gRPC client not initialized');

        const grpcRequest = {
            context: {
                request_id: request.context.requestId,
                tenant_id: request.context.tenantId,
                timestamp: request.context.timestamp ? {
                    seconds: Math.floor(request.context.timestamp.getTime() / 1000),
                    nanos: (request.context.timestamp.getTime() % 1000) * 1_000_000,
                } : undefined,
                metadata: request.context.metadata || {},
            },
            topic: request.topic,
            grade_level: normalizeGradeLevel(request.gradeLevel),
            domain: normalizeDomain(request.domain),
            max_claims: request.maxClaims,
            context_params: request.contextParams || {},
            language: request.language || 'en',
        };

        if (!this.contentClient) {
            throw new ContentGenerationError('Client not initialized', 'CLIENT_NOT_READY');
        }

        return this.makeRequest(
            this.contentClient,
            ['generateClaims', 'GenerateClaims'],
            grpcRequest,
        );
    }

    async generateExamples(request: GenerateExamplesRequest): Promise<any> {
        if (!this.isReady) throw new Error('gRPC client not initialized');

        const grpcRequest = {
            request_id: request.requestId,
            tenant_id: request.tenantId,
            claim_text: request.claimText,
            claim_ref: request.claimRef,
            example_types: (request.exampleTypes || []).map(normalizeExampleType),
            count: request.count,
            domain: normalizeDomain(request.domain),
            grade_level: normalizeGradeLevel(request.gradeLevel),
            context: request.context || {},
        };

        if (!this.contentClient) {
            throw new ContentGenerationError('Client not initialized', 'CLIENT_NOT_READY');
        }

        return this.makeRequest(
            this.contentClient,
            ['generateExamples', 'GenerateExamples'],
            grpcRequest,
        );
    }

    async analyzeContentNeeds(request: AnalyzeContentNeedsRequest): Promise<AnalyzeContentNeedsResponse> {
        if (!this.isReady) throw new Error('gRPC client not initialized');

        const grpcRequest = {
            request_id: request.requestId,
            tenant_id: request.tenantId,
            claim_text: request.claimText,
            bloom_level: request.bloomLevel,
            domain: normalizeDomain(request.domain),
            grade_level: normalizeGradeLevel(request.gradeLevel),
            context: request.context || {},
        };

        if (!this.contentClient) {
            throw new ContentGenerationError('Client not initialized', 'CLIENT_NOT_READY');
        }

        const response = await this.makeRequest(
            this.contentClient,
            ['analyzeContentNeeds', 'AnalyzeContentNeeds'],
            grpcRequest,
        );

        return this.transformAnalyzeContentNeedsResponse(response);
    }

    private transformAnalyzeContentNeedsResponse(
        response: Record<string, unknown>
    ): AnalyzeContentNeedsResponse {
        const metadata = response.metadata as Record<string, unknown> | undefined;
        const contentNeeds = response.content_needs as Record<string, unknown> | undefined;

        return {
            requestId: String(response.request_id ?? ''),
            contentNeeds: {
                examples: contentNeeds?.examples ? {
                    required: Boolean((contentNeeds.examples as Record<string, unknown>).required),
                    types: Array.isArray((contentNeeds.examples as Record<string, unknown>).types)
                        ? (contentNeeds.examples as Record<string, unknown>).types as string[]
                        : [],
                    count: Number((contentNeeds.examples as Record<string, unknown>).count ?? 2),
                    necessity: Number((contentNeeds.examples as Record<string, unknown>).necessity ?? 0.5),
                    rationale: String((contentNeeds.examples as Record<string, unknown>).rationale ?? ''),
                } : undefined,
                simulation: contentNeeds?.simulation ? {
                    required: Boolean((contentNeeds.simulation as Record<string, unknown>).required),
                    interactionType: String((contentNeeds.simulation as Record<string, unknown>).interaction_type ?? 'PARAMETER_EXPLORATION'),
                    complexity: String((contentNeeds.simulation as Record<string, unknown>).complexity ?? 'MEDIUM'),
                    necessity: Number((contentNeeds.simulation as Record<string, unknown>).necessity ?? 0.5),
                    rationale: String((contentNeeds.simulation as Record<string, unknown>).rationale ?? ''),
                } : undefined,
                animation: contentNeeds?.animation ? {
                    required: Boolean((contentNeeds.animation as Record<string, unknown>).required),
                    animationType: String((contentNeeds.animation as Record<string, unknown>).animation_type ?? 'TWO_D'),
                    durationSeconds: Number((contentNeeds.animation as Record<string, unknown>).duration_seconds ?? 30),
                    necessity: Number((contentNeeds.animation as Record<string, unknown>).necessity ?? 0.5),
                    rationale: String((contentNeeds.animation as Record<string, unknown>).rationale ?? ''),
                } : undefined,
            },
            metadata: {
                modelName: String(metadata?.model_name ?? ''),
                tokensUsed: Number(metadata?.tokens_used ?? 0),
                generationTimeMs: Number(metadata?.generation_time_ms ?? 0),
                temperature: Number(metadata?.temperature ?? 0),
                promptHash: String(metadata?.prompt_hash ?? ''),
                timestamp: String(metadata?.timestamp ?? ''),
            },
        };
    }

    async generateSimulation(request: GenerateSimulationRequest): Promise<any> {
        if (!this.isReady) throw new Error('gRPC client not initialized');

        const grpcRequest: Record<string, unknown> = {
            request_id: request.requestId,
            tenant_id: request.tenantId,
            claim_text: request.claimText,
            claim_ref: request.claimRef,
            grade_level: normalizeGradeLevel(request.gradeLevel),
            domain: normalizeDomain(request.domain),
            interaction_type: normalizeInteractionType(request.interactionType),
            complexity: normalizeComplexity(request.complexity),
            context: request.context || {},
        };

        // Add canonical SimKit contract fields if provided
        if (request.seed !== undefined) {
            grpcRequest.seed = request.seed;
        }
        if (request.parameterBounds && request.parameterBounds.length > 0) {
            grpcRequest.parameter_bounds = request.parameterBounds;
        }
        if (request.telemetryEvents && request.telemetryEvents.length > 0) {
            grpcRequest.telemetry_events = request.telemetryEvents;
        }
        if (request.failureStates && request.failureStates.length > 0) {
            grpcRequest.failure_states = request.failureStates;
        }
        if (request.stateSnapshots && request.stateSnapshots.length > 0) {
            grpcRequest.state_snapshots = request.stateSnapshots;
        }
        if (request.exportConfig) {
            grpcRequest.export_config = request.exportConfig;
        }
        if (request.claimLinks && request.claimLinks.length > 0) {
            grpcRequest.claim_links = request.claimLinks;
        }
        if (request.accessibility) {
            grpcRequest.accessibility = request.accessibility;
        }

        if (!this.contentClient) {
            throw new ContentGenerationError('Client not initialized', 'CLIENT_NOT_READY');
        }

        return this.makeRequest(
            this.contentClient,
            ['generateSimulation', 'GenerateSimulation'],
            grpcRequest,
        );
    }

    async generateAnimation(request: GenerateAnimationRequest): Promise<any> {
        if (!this.isReady) throw new Error('gRPC client not initialized');

        const grpcRequest = {
            request_id: request.requestId,
            tenant_id: request.tenantId,
            claim_text: request.claimText,
            claim_ref: request.claimRef,
            animation_type: normalizeAnimationType(request.animationType),
            duration_seconds: request.durationSeconds,
            domain: normalizeDomain(request.domain),
            grade_level: normalizeGradeLevel(request.gradeLevel),
            context: request.context || {},
        };

        if (!this.contentClient) {
            throw new ContentGenerationError('Client not initialized', 'CLIENT_NOT_READY');
        }

        return this.makeRequest(
            this.contentClient,
            ['generateAnimation', 'GenerateAnimation'],
            grpcRequest,
        );
    }

    async generateAssessment(request: GenerateAssessmentRequest): Promise<GenerateAssessmentResponse> {
        if (!this.isReady) throw new Error('gRPC client not initialized');

        const grpcRequest: Record<string, unknown> = {
            request_id: request.requestId,
            tenant_id: request.tenantId,
            claim_text: request.claimText,
            claim_ref: request.claimRef,
            grade_level: normalizeGradeLevel(request.gradeLevel),
            domain: normalizeDomain(request.domain),
            assessment_types: request.assessmentTypes,
            item_count: request.itemCount,
            context: request.context || {},
            cbm_enabled: request.cbmEnabled,
            include_rubrics: request.includeRubrics,
            include_distractor_rationales: request.includeDistractorRationales,
        };

        if (request.claimIds && request.claimIds.length > 0) {
            grpcRequest.claim_ids = request.claimIds;
        }
        if (request.evidenceIds && request.evidenceIds.length > 0) {
            grpcRequest.evidence_ids = request.evidenceIds;
        }
        if (request.simulationBased !== undefined) {
            grpcRequest.simulation_based = request.simulationBased;
        }

        if (!this.contentClient) {
            throw new ContentGenerationError('Client not initialized', 'CLIENT_NOT_READY');
        }

        const response = await this.makeRequest(
            this.contentClient,
            ['generateAssessment', 'GenerateAssessment'],
            grpcRequest,
        );

        return this.transformGenerateAssessmentResponse(response);
    }

    private transformGenerateAssessmentResponse(
        response: Record<string, unknown>
    ): GenerateAssessmentResponse {
        const metadata = response.metadata as Record<string, unknown> | undefined;
        const assessments = response.assessments as Array<Record<string, unknown>> | undefined;

        return {
            requestId: String(response.request_id ?? ''),
            assessments: assessments?.map((item): AssessmentItem => {
                const cbmData = item.cbm as Record<string, unknown> | undefined;
                const rubricData = item.rubric as Record<string, unknown> | undefined;
                const answerKeyData = item.answerKey as Record<string, unknown> | undefined;
                const distractorData = item.distractor_rationales as Array<Record<string, unknown>> | undefined;

                return {
                    itemId: String(item.item_id ?? ''),
                    type: String(item.type ?? ''),
                    prompt: String(item.prompt ?? ''),
                    guidance: String(item.guidance ?? ''),
                    solution: String(item.solution ?? ''),
                    cbm: {
                        confidenceRequired: Boolean(cbmData?.confidence_required ?? true),
                        confidenceLevels: Array.isArray(cbmData?.confidence_levels)
                            ? cbmData.confidence_levels.map((cl: Record<string, unknown>) => ({
                                level: Number(cl.level ?? 1),
                                label: String(cl.label ?? ''),
                                score: Number(cl.score ?? 0),
                            }))
                            : [],
                        scoringPolicy: String(cbmData?.scoring_policy ?? 'confidence_weighted'),
                    },
                    rubric: {
                        criteria: Array.isArray(rubricData?.criteria)
                            ? rubricData.criteria.map((crit: Record<string, unknown>) => ({
                                id: String(crit.id ?? ''),
                                description: String(crit.description ?? ''),
                                maxPoints: Number(crit.max_points ?? 0),
                                levels: Array.isArray(crit.levels)
                                    ? crit.levels.map((lvl: Record<string, unknown>) => ({
                                        points: Number(lvl.points ?? 0),
                                        description: String(lvl.description ?? ''),
                                    }))
                                    : [],
                            }))
                            : [],
                    },
                    answerKey: {
                        correctAnswer: String(answerKeyData?.correct_answer ?? ''),
                        alternativeAnswers: Array.isArray(answerKeyData?.alternative_answers)
                            ? answerKeyData.alternative_answers.map(String)
                            : [],
                        partialCreditRules: Array.isArray(answerKeyData?.partial_credit_rules)
                            ? answerKeyData.partial_credit_rules.map(String)
                            : [],
                    },
                    distractorRationales: Array.isArray(distractorData)
                        ? distractorData.map((dr: Record<string, unknown>) => ({
                            option: String(dr.option ?? ''),
                            rationale: String(dr.rationale ?? ''),
                        }))
                        : undefined,
                    claimIds: Array.isArray(item.claim_ids) ? item.claim_ids.map(String) : [],
                    evidenceIds: Array.isArray(item.evidence_ids) ? item.evidence_ids.map(String) : [],
                    simulationBased: item.simulation_based !== undefined ? Boolean(item.simulation_based) : undefined,
                    simulationTaskId: item.simulation_task_id !== undefined ? String(item.simulation_task_id) : undefined,
                };
            }) ?? [],
            metadata: {
                modelName: String(metadata?.model_name ?? ''),
                tokensUsed: Number(metadata?.tokens_used ?? 0),
                generationTimeMs: Number(metadata?.generation_time_ms ?? 0),
                temperature: Number(metadata?.temperature ?? 0),
                promptHash: String(metadata?.prompt_hash ?? ''),
                timestamp: String(metadata?.timestamp ?? ''),
            },
        };
    }

    async validateContent(request: ValidateContentRequest): Promise<ValidateContentResponse> {
        if (!this.isReady || !this.contentClient) {
            throw new ContentGenerationError(
                'gRPC client not initialized',
                'CLIENT_NOT_READY'
            );
        }

        const grpcRequest = {
            request_id: request.requestId,
            tenant_id: request.tenantId,
            experience_id: request.experienceId,
            title: request.title,
            description: request.description,
            claim_texts: request.claimTexts,
            domain: normalizeDomain(request.domain),
        };

        if (!this.contentClient) {
            throw new ContentGenerationError('Client not initialized', 'CLIENT_NOT_READY');
        }

        const response = await this.makeRequest(
            this.contentClient,
            ['validateContent', 'ValidateContent'],
            grpcRequest,
        );

        return this.transformValidateResponse(response);
    }

    private transformValidateResponse(response: Record<string, unknown>): ValidateContentResponse {
        const metadata = response.metadata as Record<string, unknown> | undefined;
        const issues = response.issues as Array<Record<string, unknown>> | undefined;

        return {
            requestId: String(response.request_id ?? ''),
            status: String(response.status ?? 'warning') as 'valid' | 'invalid' | 'warning',
            overallScore: Number(response.overall_score ?? 0),
            canPublish: Boolean(response.can_publish ?? false),
            dimensionScores: (response.dimension_scores as Record<string, number>) ?? {},
            issues: issues?.map((issue) => ({
                issueId: String(issue.issue_id ?? ''),
                dimension: String(issue.dimension ?? ''),
                severity: String(issue.severity ?? 'warning'),
                message: String(issue.message ?? ''),
                suggestion: String(issue.suggestion ?? ''),
            })) ?? [],
            issueCount: Number(response.issue_count ?? 0),
            metadata: {
                modelName: String(metadata?.model_name ?? ''),
                tokensUsed: Number(metadata?.tokens_used ?? 0),
                generationTimeMs: Number(metadata?.generation_time_ms ?? 0),
                temperature: Number(metadata?.temperature ?? 0),
                promptHash: String(metadata?.prompt_hash ?? ''),
                timestamp: String(metadata?.timestamp ?? ''),
            },
        };
    }

    private resolveMethod(
        client: grpc.Client,
        methodNames: string[]
    ): (request: unknown, metadata: object, callback: (err: Error | null, response: unknown) => void) => void {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const clientAny = client as unknown as Record<string, (request: unknown, metadata: object, callback: (err: Error | null, response: unknown) => void) => void>;
        
        for (const methodName of methodNames) {
            const method = clientAny[methodName];
            if (typeof method === 'function') {
                return method.bind(client);
            }
        }

        throw new ContentGenerationError(
            `No matching gRPC method found. Tried: ${methodNames.join(', ')}`,
            'METHOD_NOT_FOUND'
        );
    }

    private async makeRequest<R>(
        client: grpc.Client,
        methodNames: string[],
        request: Record<string, unknown>
    ): Promise<Record<string, unknown>> {
        const method = this.resolveMethod(client, methodNames);

        let attempt = 0;
        while (attempt <= this.maxRetries) {
            try {
                return await new Promise((resolve, reject) => {
                    method(
                        request,
                        { deadline: Date.now() + this.timeout },
                        (err: Error | null, response: unknown) => {
                            if (err) {
                                reject(err);
                            } else {
                                resolve(response as Record<string, unknown>);
                            }
                        },
                    );
                });
            } catch (err) {
                attempt += 1;
                if (attempt > this.maxRetries) {
                    this.logger.error({ err, attempt }, 'gRPC request failed after retries');
                    throw new ContentGenerationError(
                        `Request failed after ${attempt} attempts: ${err instanceof Error ? err.message : String(err)}`,
                        'REQUEST_FAILED',
                        { attempts: attempt, originalError: err }
                    );
                }
                this.logger.warn({ err, attempt }, 'gRPC request failed, retrying');
            }
        }

        throw new ContentGenerationError(
            'Unreachable gRPC retry state',
            'UNREACHABLE_STATE'
        );
    }
}
