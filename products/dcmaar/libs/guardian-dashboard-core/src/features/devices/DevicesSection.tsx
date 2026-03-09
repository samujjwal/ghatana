import { type FC } from "react";
import { useDevicesData } from "../../hooks";
import { useCanDo } from "../../roles";

export const DevicesSection: FC = () => {
    const { data: devices, isLoading } = useDevicesData<any[]>();
    const canManageDevices = useCanDo("canManagePolicies");

    if (isLoading) {
        return <div>Loading devices...</div>;
    }

    if (!devices || devices.length === 0) {
        return <div>No managed devices yet.</div>;
    }

    return (
        <div className="space-y-4">
            <h2 className="text-xl font-bold text-slate-900 dark:text-white">Managed Devices</h2>
            {devices.map((device: any) => (
                <div key={device.id} className="p-4 border border-slate-200 dark:border-slate-700 rounded-lg bg-white dark:bg-slate-800 shadow-sm">
                    <div className="flex justify-between items-center">
                        <div>
                            <p className="font-semibold text-slate-900 dark:text-white">{device.name}</p>
                            {device.childName && (
                                <p className="text-sm text-slate-600 dark:text-slate-400">{device.childName}</p>
                            )}
                        </div>
                        <span
                            className={`px-2 py-1 rounded text-sm ${device.status === "online"
                                ? "bg-green-100 dark:bg-green-900/30 text-green-800 dark:text-green-400"
                                : "bg-slate-100 dark:bg-slate-700 text-slate-800 dark:text-slate-300"
                                }`}
                        >
                            {device.status}
                        </span>
                    </div>

                    {canManageDevices && (
                        <div className="mt-3 space-x-2">
                            <button className="px-3 py-1 bg-blue-500 text-white rounded text-sm hover:bg-blue-600">
                                Manage
                            </button>
                            <button className="px-3 py-1 bg-slate-300 dark:bg-slate-600 text-slate-800 dark:text-slate-200 rounded text-sm hover:bg-slate-400 dark:hover:bg-slate-500">
                                Details
                            </button>
                        </div>
                    )}
                </div>
            ))}
        </div>
    );
};
