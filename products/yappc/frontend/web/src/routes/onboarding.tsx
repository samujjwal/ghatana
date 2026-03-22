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

/**
 * Check if user has completed onboarding
 */
function useOnboardingCheck() {
    const isComplete = localStorage.getItem('onboarding_complete') === 'true';
    return isComplete;
}

/**
 * Onboarding page component
 */
export function Component() {
    const navigate = useNavigate();
    const isOnboardingComplete = useOnboardingCheck();

    // Redirect if already onboarded
    useEffect(() => {
        if (isOnboardingComplete) {
            navigate('/app', { replace: true });
        }
    }, [isOnboardingComplete, navigate]);

    // Don't render if already complete (prevents flash)
    if (isOnboardingComplete) {
        return null;
    }

    return (
        <OnboardingFlow
            onComplete={() => {
                // Will be called after workspace is created
            }}
            redirectTo="/app"
        />
    );
}

export default Component;
