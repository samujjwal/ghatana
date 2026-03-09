import React from "react";
import { Card, Button, Badge } from "../../components/ui";

/**
 * Backlog page for managing feature requests and tasks.
 *
 * @returns Backlog JSX
 */
export default function BacklogPage(): React.ReactElement {
    return (
        <div className="space-y-6 p-6">
            <h1 className="text-3xl font-bold text-slate-900 dark:text-neutral-100">Product Backlog</h1>
            <Card className="p-4">
                <div className="space-y-4">
                    <div className="flex items-center justify-between">
                        <h2 className="text-xl font-semibold text-slate-900 dark:text-neutral-100">Feature Requests</h2>
                        <Button>New Feature</Button>
                    </div>
                    <div className="space-y-2">
                        <div className="flex items-center justify-between border border-slate-200 dark:border-neutral-600 p-3 rounded bg-white dark:bg-neutral-800">
                            <div>
                                <h3 className="font-medium text-slate-900 dark:text-neutral-100">Implement user authentication</h3>
                                <p className="text-sm text-slate-600 dark:text-neutral-400">Priority: High</p>
                            </div>
                            <Badge color="blue">Ready</Badge>
                        </div>
                        <div className="flex items-center justify-between border border-slate-200 dark:border-neutral-600 p-3 rounded bg-white dark:bg-neutral-800">
                            <div>
                                <h3 className="font-medium text-slate-900 dark:text-neutral-100">Create reporting dashboard</h3>
                                <p className="text-sm text-slate-600 dark:text-neutral-400">Priority: Medium</p>
                            </div>
                            <Badge color="yellow">In Progress</Badge>
                        </div>
                    </div>
                </div>
            </Card>
        </div>
    );
}
