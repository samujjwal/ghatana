import React from "react";
import { Card } from "../../components/ui";

/**
 * Release overview page showing deployment status and quality gates.
 *
 * @returns Release Overview JSX
 */
export default function ReleaseOverviewPage(): React.ReactElement {
    return (
        <div className="space-y-6 p-6">
            <h1 className="text-3xl font-bold">Release Overview</h1>
            <div className="grid grid-cols-2 gap-6">
                <Card className="p-4">
                    <h2 className="text-xl font-semibold mb-4">Deployment Status</h2>
                    <div className="space-y-2">
                        <p>✅ Code Review: Passed</p>
                        <p>✅ Build: Succeeded</p>
                        <p>🔄 QA: In Progress</p>
                        <p>⏳ Deployment: Pending</p>
                    </div>
                </Card>
                <Card className="p-4">
                    <h2 className="text-xl font-semibold mb-4">Quality Gates</h2>
                    <div className="space-y-2">
                        <p>✅ Code Coverage: 85%</p>
                        <p>✅ Security: Pass</p>
                        <p>✅ Performance: Pass</p>
                        <p>✅ Compliance: Pass</p>
                    </div>
                </Card>
            </div>
        </div>
    );
}
