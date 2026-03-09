/**
 * Playbook Drawer Component - Configuration for automated workflows
 *
 * <p><b>Purpose</b><br>
 * Drawer interface for configuring automated response playbooks.
 * Allows setting conditions, triggers, and actions for automation.
 *
 * <p><b>Features</b><br>
 * - Trigger configuration (event type, threshold)
 * - Action selection (notify, auto-fix, escalate)
 * - Condition builder for complex rules
 * - Preview of automation effects
 * - HITL approval settings
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * {showDrawer && (
 *   <PlaybookDrawer
 *     departmentName="Backend"
 *     onClose={() => setShowDrawer(false)}
 *   />
 * )}
 * }</pre>
 *
 * @doc.type component
 * @doc.purpose Automation playbook configuration drawer
 * @doc.layer product
 * @doc.pattern Molecule
 */
import { useState } from "react";

interface PlaybookDrawerProps {
    departmentName: string;
    onClose: () => void;
}

export function PlaybookDrawer({
    departmentName,
    onClose,
}: PlaybookDrawerProps) {
    const [triggerType, setTriggerType] = useState("threshold");
    const [actionType, setActionType] = useState("notify");
    const [requiresApproval, setRequiresApproval] = useState(true);

    const handleSave = () => {
        console.log("Playbook saved:", {
            department: departmentName,
            trigger: triggerType,
            action: actionType,
            requiresApproval,
        });
        onClose();
    };

    return (
        <div className="fixed inset-0 z-50 flex">
            {/* Backdrop */}
            <div
                className="absolute inset-0 bg-black/50 dark:bg-black/70"
                onClick={onClose}
            />

            {/* Drawer */}
            <div className="relative ml-auto w-full max-w-md bg-white dark:bg-slate-950 border-l border-slate-200 dark:border-slate-800 shadow-lg flex flex-col max-h-screen">
                {/* Header */}
                <div className="flex items-center justify-between border-b border-slate-200 dark:border-slate-800 p-6">
                    <h2 className="text-xl font-semibold text-slate-900 dark:text-neutral-100">
                        Configure Playbook
                    </h2>
                    <button
                        onClick={onClose}
                        className="text-slate-600 dark:text-neutral-400 hover:text-slate-900 dark:hover:text-white text-xl"
                    >
                        ✕
                    </button>
                </div>

                {/* Content */}
                <div className="flex-1 overflow-y-auto p-6 space-y-6">
                    {/* Department Info */}
                    <div>
                        <p className="text-xs text-slate-600 dark:text-neutral-400 uppercase font-semibold">
                            Department
                        </p>
                        <p className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mt-1">
                            {departmentName}
                        </p>
                    </div>

                    {/* Trigger Configuration */}
                    <div>
                        <label className="text-sm font-medium text-slate-700 dark:text-neutral-300">
                            Trigger Event
                        </label>
                        <select
                            value={triggerType}
                            onChange={(e) => setTriggerType(e.target.value)}
                            className="mt-2 w-full px-3 py-2 border border-slate-300 dark:border-neutral-600 rounded-lg bg-white dark:bg-slate-900 text-slate-900 dark:text-neutral-100"
                        >
                            <option value="threshold">Metric Threshold Exceeded</option>
                            <option value="incident">Incident Detected</option>
                            <option value="failure">Deployment Failure</option>
                            <option value="anomaly">Anomaly Detected</option>
                        </select>
                    </div>

                    {/* Threshold Configuration */}
                    {triggerType === "threshold" && (
                        <div>
                            <label className="text-sm font-medium text-slate-700 dark:text-neutral-300">
                                Metric
                            </label>
                            <select className="mt-2 w-full px-3 py-2 border border-slate-300 dark:border-neutral-600 rounded-lg bg-white dark:bg-slate-900 text-slate-900 dark:text-neutral-100">
                                <option value="cfr">Change Failure Rate</option>
                                <option value="mttr">Mean Time to Recovery</option>
                                <option value="latency">Request Latency</option>
                                <option value="errors">Error Rate</option>
                            </select>

                            <label className="text-sm font-medium text-slate-700 dark:text-neutral-300 block mt-4">
                                Threshold
                            </label>
                            <div className="mt-2 flex items-center gap-2">
                                <input
                                    type="number"
                                    defaultValue={5}
                                    className="flex-1 px-3 py-2 border border-slate-300 dark:border-neutral-600 rounded-lg bg-white dark:bg-slate-900 text-slate-900 dark:text-neutral-100"
                                />
                                <span className="text-slate-600 dark:text-neutral-400">%</span>
                            </div>
                        </div>
                    )}

                    {/* Action Configuration */}
                    <div>
                        <label className="text-sm font-medium text-slate-700 dark:text-neutral-300">
                            Action
                        </label>
                        <select
                            value={actionType}
                            onChange={(e) => setActionType(e.target.value)}
                            className="mt-2 w-full px-3 py-2 border border-slate-300 dark:border-neutral-600 rounded-lg bg-white dark:bg-slate-900 text-slate-900 dark:text-neutral-100"
                        >
                            <option value="notify">Notify Team</option>
                            <option value="escalate">Escalate to Lead</option>
                            <option value="autofix">Attempt Auto-Fix</option>
                            <option value="rollback">Rollback Deployment</option>
                        </select>
                    </div>

                    {/* HITL Settings */}
                    <div className="border-t border-slate-200 dark:border-slate-800 pt-4">
                        <label className="flex items-center gap-2 cursor-pointer">
                            <input
                                type="checkbox"
                                checked={requiresApproval}
                                onChange={(e) => setRequiresApproval(e.target.checked)}
                                className="w-4 h-4 rounded border-slate-300"
                            />
                            <span className="text-sm font-medium text-slate-700 dark:text-neutral-300">
                                Require human approval before executing
                            </span>
                        </label>
                        <p className="text-xs text-slate-600 dark:text-neutral-400 mt-2">
                            {requiresApproval
                                ? "Actions will be queued for review"
                                : "Actions will execute automatically"}
                        </p>
                    </div>

                    {/* Preview */}
                    <div className="bg-blue-50 dark:bg-indigo-600/30 border border-blue-200 dark:border-blue-800 rounded-lg p-4">
                        <p className="text-xs font-semibold text-blue-700 dark:text-indigo-400 uppercase">
                            Preview
                        </p>
                        <p className="text-sm text-blue-800 dark:text-blue-200 mt-2">
                            When {triggerType === "threshold" && "metric exceeds threshold"} →{" "}
                            {actionType === "notify" && "Notify team"}
                            {actionType === "escalate" && "Escalate to lead"}
                            {actionType === "autofix" && "Attempt auto-fix"}
                            {actionType === "rollback" && "Rollback deployment"}
                            {requiresApproval && " (requires approval)"}
                        </p>
                    </div>
                </div>

                {/* Footer */}
                <div className="border-t border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-900 p-6 flex gap-3">
                    <button
                        onClick={onClose}
                        className="flex-1 px-4 py-2 border border-slate-300 dark:border-neutral-600 rounded-lg text-slate-900 dark:text-neutral-100 font-medium hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors"
                    >
                        Cancel
                    </button>
                    <button
                        onClick={handleSave}
                        className="flex-1 px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg font-medium transition-colors"
                    >
                        Save Playbook
                    </button>
                </div>
            </div>
        </div>
    );
}

export default PlaybookDrawer;
