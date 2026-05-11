/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { ProjectType } from './ProjectType';
import type { RelatedProjectRecommendation } from './RelatedProjectRecommendation';
export type ProjectSetupSuggestion = {
    suggestion: string;
    inferredType: ProjectType;
    rationale: string;
    summary: string;
    recommendations: Array<string>;
    relatedProjects: Array<RelatedProjectRecommendation>;
};

