/**
 * Content Studio Types for Admin UI
 * 
 * TypeScript types for Content Studio integration
 */

export interface LearningClaim {
    id: string;
    topic: string;
    gradeLevel: string;
    subject: string;
    claimText: string;
    confidenceScore: string;
    evidenceLevel: string;
    bloomLevel: string;
    metadata?: Record<string, any>;
    createdAt: string;
    updatedAt: string;
}

export interface ClaimExample {
    id: string;
    claimId: string;
    exampleType: string;
    title: string;
    description: string;
    difficultyLevel: number;
    educationalValue: string;
    metadata?: Record<string, any>;
    createdAt: string;
    updatedAt: string;
}

export interface RealWorldUseCase {
    id: string;
    claimId: string;
    title: string;
    description: string;
    industry: string;
    companyCaseStudy: string;
    applicationScenario: string;
    skillsApplied: string[];
    learningOutcomes: string[];
    difficultyLevel: number;
    educationalValue: string;
    metadata?: Record<string, any>;
    createdAt: string;
    updatedAt: string;
}

export interface PracticeWorksheet {
    id: string;
    claimId: string;
    title: string;
    description: string;
    worksheetType: string;
    content: {
        problems: Array<{
            type: string;
            question: string;
            options?: string[];
            answer?: string;
        }>;
    };
    instructions: string;
    difficultyLevel: number;
    estimatedTimeMinutes: number;
    educationalValue: string;
    answerKey?: Record<string, any>;
    gradingRubric?: Record<string, any>;
    metadata?: Record<string, any>;
    createdAt: string;
    updatedAt: string;
}

export interface Quiz {
    id: string;
    claimId: string;
    title: string;
    description: string;
    quizType: string;
    questions: Array<{
        type: string;
        question: string;
        options?: string[];
        correct?: number;
        answer?: string;
    }>;
    timeLimitMinutes: number;
    passingScore: string;
    difficultyLevel: number;
    questionTypes: string[];
    educationalValue: string;
    metadata?: Record<string, any>;
    createdAt: string;
    updatedAt: string;
}

export interface ComprehensiveContent {
    claim: LearningClaim;
    examples: ClaimExample[];
    simulations: any[];
    animations: any[];
    realWorldUseCases: RealWorldUseCase[];
    practiceWorksheets: PracticeWorksheet[];
    realWorldProjects: any[];
    quizzes: Quiz[];
}

export interface ContentGenerationRequest {
    topic: string;
    gradeLevel: string;
    subject: string;
    includeRealWorldUseCases?: boolean;
    includePracticeWorksheets?: boolean;
    includeQuizzes?: boolean;
    includeSimulations?: boolean;
    includeAnimations?: boolean;
    includeProjects?: boolean;
}
