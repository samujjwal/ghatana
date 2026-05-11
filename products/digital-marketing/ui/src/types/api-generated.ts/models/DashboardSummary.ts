/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { DashboardApprovalMetrics } from './DashboardApprovalMetrics';
import type { DashboardBudgetMetrics } from './DashboardBudgetMetrics';
import type { DashboardCampaignMetrics } from './DashboardCampaignMetrics';
import type { DashboardFreshness } from './DashboardFreshness';
import type { DashboardLeadMetrics } from './DashboardLeadMetrics';
export type DashboardSummary = {
    workspaceId: string;
    campaignMetrics: DashboardCampaignMetrics;
    approvalMetrics: DashboardApprovalMetrics;
    budgetMetrics: DashboardBudgetMetrics;
    leadMetrics: DashboardLeadMetrics;
    freshness: DashboardFreshness;
    confidence: DashboardSummary.confidence;
    /**
     * Backend source used for dashboard facts
     */
    metricSource: string;
    /**
     * Versioned formula contract for dashboard calculations
     */
    formulaVersion: string;
    /**
     * Authorization scope applied to the summary
     */
    authorizationScope: string;
    /**
     * True when one or more source domains are stale, unavailable, or incomplete
     */
    partialData: boolean;
};
export namespace DashboardSummary {
    export enum confidence {
        HIGH = 'HIGH',
        MEDIUM = 'MEDIUM',
        LOW = 'LOW',
    }
}

