import { type FC } from "react";
import { useUsageOverview } from "../../hooks";

export const UsageSection: FC = () => {
    const { data: usage, isLoading } = useUsageOverview<any[]>();

    if (isLoading) {
        return <div>Loading usage...</div>;
    }

    if (!usage || usage.length === 0) {
        return <div>No usage data available.</div>;
    }

    return (
        <div className="space-y-4">
            <h2 className="text-xl font-bold text-slate-900 dark:text-white">Recent Usage</h2>
            {usage.map((entry: any, index: number) => (
                <div key={entry.id ?? index} className="p-4 border border-slate-200 dark:border-slate-700 rounded-lg bg-white dark:bg-slate-800 shadow-sm">
                    <div className="flex justify-between items-center">
                        <div>
                            <p className="font-semibold text-slate-900 dark:text-white">{entry.date ?? "Today"}</p>
                            {entry.childName && (
                                <p className="text-sm text-slate-600 dark:text-slate-400">{entry.childName}</p>
                            )}
                        </div>
                        {entry.screenTime != null && (
                            <span className="text-sm text-slate-700 dark:text-slate-300">
                                {entry.screenTime} min
                            </span>
                        )}
                    </div>
                </div>
            ))}
        </div>
    );
};
