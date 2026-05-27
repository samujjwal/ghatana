/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type PhaseReadiness = {
    canAdvance: boolean;
    nextPhase: string;
    missingPrerequisites: Array<string>;
    completenessScore: number;
    isDegraded: boolean;
    estimatedReadyIn?: string | null;
    estimatedReadyInHours?: number | null;
    predictionConfidence?: number | null;
};

