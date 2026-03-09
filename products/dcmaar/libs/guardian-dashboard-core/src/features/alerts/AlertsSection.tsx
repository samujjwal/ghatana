import { type FC } from "react";
import { useAlertsData } from "../../hooks";

export const AlertsSection: FC = () => {
    const { data: alerts, isLoading } = useAlertsData<any[]>();

    if (isLoading) {
        return <div>Loading alerts...</div>;
    }

    if (!alerts || alerts.length === 0) {
        return <div>No recent alerts.</div>;
    }

    return (
        <div className="space-y-4">
            <h2 className="text-xl font-bold text-slate-900 dark:text-white">Recent Alerts</h2>
            {alerts.map((alert: any) => (
                <div key={alert.id} className="p-4 border border-slate-200 dark:border-slate-700 rounded-lg bg-white dark:bg-slate-800 shadow-sm">
                    <p className="font-semibold text-slate-900 dark:text-white">{alert.message ?? "Alert"}</p>
                    {alert.timestamp && (
                        <p className="text-sm text-slate-600 dark:text-slate-400 mt-1">{String(alert.timestamp)}</p>
                    )}
                </div>
            ))}
        </div>
    );
};
