import React from "react";
import { Card, Button } from "../../components/ui";

/**
 * Sprint planning page for organizing sprints and tasks.
 *
 * @returns Sprint Planning JSX
 */
export default function SprintPlanningPage(): React.ReactElement {
    return (
        <div className="space-y-6 p-6">
            <h1 className="text-3xl font-bold text-slate-900 dark:text-neutral-100">Sprint Planning</h1>
            <Card className="p-4">
                <div className="space-y-4">
                    <div className="flex items-center justify-between">
                        <h2 className="text-xl font-semibold text-slate-900 dark:text-neutral-100">Current Sprint</h2>
                        <Button>Start New Sprint</Button>
                    </div>
                    <div className="grid grid-cols-3 gap-4">
                        <div className="border border-slate-200 dark:border-neutral-600 p-4 rounded bg-white dark:bg-neutral-800">
                            <h3 className="font-semibold text-slate-900 dark:text-neutral-100">Sprint 24</h3>
                            <p className="text-sm text-slate-600 dark:text-neutral-400">Duration: 2 weeks</p>
                            <p className="text-sm text-slate-600 dark:text-neutral-400">Capacity: 40 points</p>
                        </div>
                    </div>
                </div>
            </Card>
        </div>
    );
}
