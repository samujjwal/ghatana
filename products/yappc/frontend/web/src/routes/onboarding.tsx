/**
 * Onboarding Route
 *
 * First-time user setup flow with AI-powered workspace and project creation.
 *
 * @doc.type route
 * @doc.purpose User onboarding
 * @doc.layer product
 * @doc.pattern Route Component
 */

import { useEffect } from 'react';
import { useNavigate } from "react-router";
import { OnboardingFlow } from '@/components/workspace';
import { useOnboardingStatus } from '../services/onboarding/OnboardingStatusService';

/**
 * Onboarding page component
 */
export function Component() {
    const navigate = useNavigate();
    const { status, isLoading } = useOnboardingStatus();
    const redirectTarget = '/workspaces';

    const isOnboardingComplete = status?.completed ?? false;

    // Redirect if already onboarded
    useEffect(() => {
        if (isOnboardingComplete) {
            navigate(redirectTarget, { replace: true });
        }
    }, [isOnboardingComplete, navigate]);

    // Loading state while checking server
    if (isLoading) {
        return (
            <div className="min-h-screen flex items-center justify-center">
                <div className="animate-pulse text-text-secondary text-sm">Checking onboarding status...</div>
            </div>
        );
    }

    // Don't render the flow if already complete to avoid flash
    if (isOnboardingComplete) {
        return null;
    }

    return (
        <OnboardingFlow
            onComplete={() => {
                navigate(redirectTarget, { replace: true });
            }}
            redirectTo={redirectTarget}
        />
    );
}

export default Component;
