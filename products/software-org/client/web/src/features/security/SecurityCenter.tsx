import React from "react";
import { AiHintBanner, ContextualHints } from "@/shared/components";

/**
 * @doc.type component
 * @doc.purpose Security center page
 * @doc.layer product
 * @doc.pattern Page
 */
export function SecurityCenter() {
    const [showSecurityHint, setShowSecurityHint] = React.useState(() => {
        if (typeof window === 'undefined') {
            return true;
        }
        const stored = window.localStorage.getItem('softwareOrg.securityCenter.aiHint.dismissed');
        return stored !== 'true';
    });

    return (
        <div className="min-h-screen bg-white dark:bg-slate-900 p-8">
            {/* Contextual Navigation Hints */}
            <div className="max-w-4xl mx-auto mb-6">
                <ContextualHints context="security" personaId="security" size="sm" />
            </div>

            <div className="max-w-4xl mx-auto text-center">
                <div className="mb-6">
                    <span className="text-6xl" role="img" aria-label="Security">🔐</span>
                </div>
                <h1 className="text-3xl font-bold text-slate-900 dark:text-neutral-100 mb-4">
                    Security Center
                </h1>
                <p className="text-lg text-slate-600 dark:text-neutral-400 mb-8">
                    Advanced security features and monitoring - Coming Soon
                </p>
                <div className="bg-slate-100 dark:bg-neutral-800 rounded-lg p-6 border border-slate-200 dark:border-neutral-600">
                    <p className="text-slate-700 dark:text-neutral-300">
                        This feature is currently under development. Check back for updates on security auditing,
                        threat monitoring, and compliance management tools.
                    </p>
                </div>

                <div className="mt-8 grid gap-4 text-left">
                    {showSecurityHint && (
                        <AiHintBanner
                            icon="🛡️"
                            title="Security DevSecOps hint"
                            body="Use the DevSecOps board with the Security persona filter to track findings, mitigations, and control rollouts by phase. Start with blocked or critical items, then verify posture in your compliance reports."
                            ctaLabel="Open security view in DevSecOps board"
                            ctaHref="/devsecops/board?persona=security&status=blocked"
                            secondaryCtaLabel="Review compliance reports"
                            secondaryCtaHref="/reports?type=compliance"
                            onDismiss={() => {
                                setShowSecurityHint(false);
                                if (typeof window !== 'undefined') {
                                    window.localStorage.setItem('softwareOrg.securityCenter.aiHint.dismissed', 'true');
                                }
                            }}
                        />
                    )}
                </div>
            </div>
        </div>
    );
}

export default SecurityCenter;
