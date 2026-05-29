import { useMemo } from 'react';
import { translate } from '../i18n/messages';
import type { ProjectDashboardAction } from '../lib/api';

export interface DashboardDecisionBrief {
    readonly headline: string;
    readonly description: string;
    readonly action: ProjectDashboardAction | null;
    readonly ctaLabel: string | null;
    readonly isDegraded: boolean;
    readonly correlationId?: string;
    readonly retryAvailable: boolean;
}

export function useDashboardDecision(
    blockedWork: readonly ProjectDashboardAction[],
    reviewRequired: readonly ProjectDashboardAction[],
    safeToContinue: readonly ProjectDashboardAction[],
    loading: boolean,
    error: unknown,
): DashboardDecisionBrief {
    return useMemo(() => {
        if (loading) {
            return {
                headline: translate('dashboard.checkingActionStatus'),
                description: translate('dashboard.loadingActionStatusDescription'),
                action: null,
                ctaLabel: null,
                isDegraded: false,
                retryAvailable: false,
            };
        }

        if (error) {
            const correlationId = typeof error === 'object' && error !== null && 'correlationId' in error 
                ? String(error.correlationId) 
                : undefined;
            const errorMessage = typeof error === 'object' && error !== null && 'message' in error 
                ? String(error.message) 
                : 'Unknown error';
            
            return {
                headline: translate('dashboard.refreshActionStatus'),
                description: `${translate('dashboard.couldNotLoadStatus')} ${errorMessage}. ${translate('dashboard.retryToRefresh')}`,
                action: null,
                ctaLabel: null,
                isDegraded: true,
                correlationId,
                retryAvailable: true,
            };
        }

        const [firstBlocked] = blockedWork;
        if (firstBlocked) {
            return {
                headline: `${translate('dashboard.doThisFirst')} ${firstBlocked.title}`,
                description: `${firstBlocked.projectName} ${translate('dashboard.blockedDescription')}`,
                action: firstBlocked,
                ctaLabel: translate('dashboard.openBlocker'),
                isDegraded: firstBlocked.isDegraded || false,
                retryAvailable: false,
            };
        }

        const [firstReview] = reviewRequired;
        if (firstReview) {
            return {
                headline: `${translate('dashboard.reviewNext')} ${firstReview.title}`,
                description: `${firstReview.projectName} ${translate('dashboard.reviewDescription')}`,
                action: firstReview,
                ctaLabel: translate('dashboard.openReview'),
                isDegraded: firstReview.isDegraded || false,
                retryAvailable: false,
            };
        }

        const [firstSafe] = safeToContinue;
        if (firstSafe) {
            return {
                headline: `${translate('dashboard.continueAction')} ${firstSafe.title}`,
                description: `${firstSafe.projectName} ${translate('dashboard.continueDescription')}`,
                action: firstSafe,
                ctaLabel: translate('dashboard.continue'),
                isDegraded: firstSafe.isDegraded || false,
                retryAvailable: false,
            };
        }

        return {
            headline: translate('dashboard.noImmediateActions'),
            description: translate('dashboard.noImmediateActionsDescription'),
            action: null,
            ctaLabel: null,
            isDegraded: false,
            retryAvailable: false,
        };
    }, [blockedWork, reviewRequired, safeToContinue, loading, error]);
}
