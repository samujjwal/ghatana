/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { EvolutionPlanPanel } from './EvolutionPlanPanel';
import type { LearningInsightPanel } from './LearningInsightPanel';
import type { PhasePanelCard } from './PhasePanelCard';
export type PhasePanelView = {
    phase: string;
    status: string;
    summary: string;
    recommendation: string;
    owner: string;
    confidence: number;
    supportTrace: string;
    cards: Array<PhasePanelCard>;
    learningInsight?: LearningInsightPanel;
    evolutionPlan?: EvolutionPlanPanel;
};

