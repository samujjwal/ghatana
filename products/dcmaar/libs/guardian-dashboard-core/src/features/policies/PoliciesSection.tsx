import { type FC } from "react";
import { usePoliciesData } from "../../hooks";

export const PoliciesSection: FC = () => {
    const { data: policies, isLoading } = usePoliciesData<any[]>();

    if (isLoading) {
        return <div>Loading policies...</div>;
    }

    if (!policies || policies.length === 0) {
        return <div>No policies defined yet.</div>;
    }

    return (
        <div className="space-y-4">
            <h2 className="text-xl font-bold text-slate-900 dark:text-white">Policies</h2>
            {policies.map((policy: any) => (
                <div key={policy.id} className="p-4 border border-slate-200 dark:border-slate-700 rounded-lg bg-white dark:bg-slate-800 shadow-sm">
                    <p className="font-semibold text-slate-900 dark:text-white">{policy.name ?? "Untitled policy"}</p>
                    {policy.description && (
                        <p className="text-sm text-slate-600 dark:text-slate-400 mt-1">{policy.description}</p>
                    )}
                </div>
            ))}
        </div>
    );
};
